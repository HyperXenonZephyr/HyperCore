package dev.hypercore.orchestrator.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wraps a child host {@link Process} launched by the orchestrator.
 *
 * <p>Captures stdout and stderr asynchronously so the orchestrator can detect
 * readiness from a configurable stdout marker, surface host logs, and diagnose
 * crashes. Readiness is signalled exactly once when the marker line appears or
 * when the process exits before that point.
 */
public final class ServerProcess implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerProcess.class);

    private final String name;
    private final Process process;
    private final String readyMarker;
    private final CopyOnWriteArrayList<String> output = new CopyOnWriteArrayList<>();
    private final AtomicBoolean ready = new AtomicBoolean();
    private final AtomicBoolean finished = new AtomicBoolean();

    private ServerProcess(String name, Process process, String readyMarker) {
        this.name = Objects.requireNonNull(name, "name");
        this.process = Objects.requireNonNull(process, "process");
        this.readyMarker = Objects.requireNonNull(readyMarker, "readyMarker");
        pump(process.getInputStream());
        pump(process.getErrorStream());
    }

    /**
     * Launches the host process with the given command line.
     *
     * @param name human-readable host name used in logs
     * @param command the full JVM command line, including the executable
     * @param readyMarker the stdout line (substring) that marks the host ready
     * @return the new process handle
     * @throws IOException if the process cannot be started
     */
    public static ServerProcess launch(String name, List<String> command, String readyMarker) throws IOException {
        Objects.requireNonNull(command, "command");
        if (command.isEmpty()) {
            throw new IllegalArgumentException("command line must not be empty");
        }
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(false);
        builder.redirectInput(ProcessBuilder.Redirect.PIPE);
        LOGGER.info("Launching {} with command: {}", name, String.join(" ", command));
        Process process = builder.start();
        // The hosts are dedicated servers driven by flags, never by stdin; close
        // the pipe so the child does not block waiting for input.
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // Best effort; some platforms reject closing a pipe's stream twice.
        }
        return new ServerProcess(name, process, readyMarker);
    }

    /**
     * Returns the name of this host.
     */
    public String name() {
        return name;
    }

    /**
     * Returns whether the underlying process is still running.
     */
    public boolean isAlive() {
        return process.isAlive();
    }

    /**
     * Returns whether the ready marker has been observed on stdout.
     */
    public boolean isReady() {
        return ready.get();
    }

    /**
     * Blocks until the ready marker appears or the process exits.
     *
     * @param timeout maximum time to wait for readiness
     * @return {@code true} if the host became ready, {@code false} if it timed
     *         out or exited before emitting the marker
     */
    public boolean awaitReady(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!ready.get() && process.isAlive()) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                LOGGER.warn(
                    "Host {} did not become ready within {}; last output: {}",
                    name,
                    timeout,
                    recentOutput(8)
                );
                return false;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(25);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        if (!ready.get()) {
            LOGGER.warn("Host {} exited before becoming ready; last output: {}", name, recentOutput(8));
            return false;
        }
        return true;
    }

    /**
     * Returns the most recent captured output lines, oldest first.
     *
     * @param maxLines maximum number of lines to return
     */
    public List<String> recentOutput(int maxLines) {
        if (maxLines <= 0) {
            return List.of();
        }
        int size = output.size();
        int from = Math.max(0, size - maxLines);
        return new ArrayList<>(output.subList(from, size));
    }

    /**
     * Requests a graceful stop: sends the terminate signal, waits up to the
     * grace period, then force-kills the process.
     */
    public void stop(Duration gracePeriod) {
        if (finished.compareAndSet(false, true)) {
            process.destroy();
        } else {
            return;
        }
        try {
            if (!process.waitFor(gracePeriod.toMillis(), TimeUnit.MILLISECONDS)) {
                LOGGER.warn("Host {} did not stop gracefully; forcing termination", name);
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        LOGGER.info("Host {} stopped with exit code {}", name, process.exitValue());
    }

    /**
     * Returns the exit code if the process has terminated, or {@code -1}.
     */
    public int exitValue() {
        return process.isAlive() ? -1 : process.exitValue();
    }

    @Override
    public void close() {
        stop(Duration.ofSeconds(10));
    }

    private void pump(java.io.InputStream stream) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.add(line);
                    if (line.contains(readyMarker)) {
                        if (ready.compareAndSet(false, true)) {
                            LOGGER.info("Host {} is ready", name);
                        }
                    }
                    LOGGER.debug("[{}] {}", name, line);
                }
            } catch (IOException error) {
                if (process.isAlive()) {
                    LOGGER.debug("Output stream for host {} closed: {}", name, error.getMessage());
                }
            }
        }, "host-output-" + name);
        thread.setDaemon(true);
        thread.start();
    }
}
