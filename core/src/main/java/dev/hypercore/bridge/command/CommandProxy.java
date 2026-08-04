package dev.hypercore.bridge.command;

import dev.hypercore.bridge.ipc.Packet;
import dev.hypercore.bridge.ipc.packet.CommandExecutePacket;
import dev.hypercore.bridge.ipc.packet.CommandExecuteResultPacket;
import dev.hypercore.bridge.ipc.packet.CommandRegistrySnapshotPacket;
import dev.hypercore.bridge.world.BridgeLink;
import dev.hypercore.orchestrator.HyperCoreRole;
import dev.hypercore.plugin.PluginCommandRegistry;
import dev.hypercore.plugin.PluginCommandSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mirrors the remote host's command registry and forwards executions.
 *
 * <p>Each host publishes a serializable snapshot of its plugin commands when the
 * bridge connects. The remote host registers them locally under a configurable
 * prefix (for example {@code forge_*} on the Fabric host), so players and mods
 * can invoke cross-host commands. Executions are forwarded with a request id and
 * the result (including collected messages) is delivered back to the original
 * sender.
 */
public final class CommandProxy {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandProxy.class);

    private final HyperCoreRole localRole;
    private final PluginCommandRegistry registry;
    private final BridgeLink link;
    private final String remotePrefix;
    private final AtomicLong nextRequestId = new AtomicLong();
    private final Map<Long, PluginCommandSender> pending = new ConcurrentHashMap<>();
    private final AtomicLong mirrored = new AtomicLong();

    /**
     * @param localRole this host's role
     * @param registry the local plugin command registry
     * @param link the bridge connection
     * @param remotePrefix prefix added to mirrored remote commands
     */
    public CommandProxy(HyperCoreRole localRole, PluginCommandRegistry registry, BridgeLink link, String remotePrefix) {
        this.localRole = Objects.requireNonNull(localRole, "localRole");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.link = Objects.requireNonNull(link, "link");
        this.remotePrefix = Objects.requireNonNull(remotePrefix, "remotePrefix");
    }

    /**
     * Returns the prefix applied to commands mirrored from the remote host.
     */
    public String remotePrefix() {
        return remotePrefix;
    }

    /**
     * Called when the bridge connection becomes ready: publishes this host's
     * command snapshot to the remote host.
     */
    public void publishSnapshot() {
        List<CommandRegistrySnapshotPacket.CommandDescriptor> commands = registry.snapshot();
        if (commands.isEmpty()) {
            return;
        }
        if (!link.send(new CommandRegistrySnapshotPacket(commands))) {
            LOGGER.debug("Could not publish command snapshot: bridge not connected");
        }
    }

    /**
     * Mirrors the remote host's commands into the local registry under the
     * configured prefix. Executions are forwarded back to the remote host.
     */
    public void mirrorRemote(List<CommandRegistrySnapshotPacket.CommandDescriptor> commands) {
        for (CommandRegistrySnapshotPacket.CommandDescriptor command : commands) {
            String prefixed = remotePrefix + "_" + command.name();
            List<String> prefixedAliases = command.aliases().stream()
                .map(alias -> remotePrefix + "_" + alias)
                .toList();
            try {
                registry.register(
                    command.pluginId().isBlank() ? "remote:" + localRole.displayName() : "remote:" + command.pluginId(),
                    new PluginCommandRegistry.CommandDefinition(
                        prefixed,
                        prefixedAliases,
                        command.permission(),
                        command.description().isBlank()
                            ? "Mirrored from the " + remoteHostName() + " host"
                            : command.description(),
                        command.usage(),
                        (sender, label, arguments) -> sendRemoteRequest(sender, command.name(), arguments),
                        (sender, label, arguments) -> List.of()
                    )
                );
            } catch (IllegalArgumentException error) {
                LOGGER.warn("Could not mirror remote command {}: {}", command.name(), error.getMessage());
            }
        }
        mirrored.addAndGet(commands.size());
        LOGGER.info("Mirrored {} command(s) from the {} host under the '{}' prefix",
            commands.size(), remoteHostName(), remotePrefix);
    }

    /**
     * Returns the number of commands mirrored from the remote host.
     */
    public int mirroredCount() {
        return (int) mirrored.get();
    }

    /**
     * Executes a command that originated on the remote host against the local
     * registry and sends the result back.
     */
    public void handleExecute(CommandExecutePacket packet) {
        RemoteCommandSender remoteSender = new RemoteCommandSender(
            packet.senderName(),
            packet.operator(),
            packet.console()
        );
        PluginCommandRegistry.DispatchResult result = registry.dispatch(packet.label(), packet.arguments(), remoteSender);
        link.send(new CommandExecuteResultPacket(
            packet.requestId(),
            result.status() == PluginCommandRegistry.DispatchStatus.EXECUTED && result.success(),
            remoteSender.drainMessages()
        ));
    }

    /**
     * Delivers an execution result to the original local sender.
     */
    public void handleResult(CommandExecuteResultPacket packet) {
        PluginCommandSender original = pending.remove(packet.requestId());
        if (original == null) {
            LOGGER.debug("No pending command for request {}", packet.requestId());
            return;
        }
        if (!packet.message().isBlank()) {
            original.sendMessage(packet.message());
        }
    }

    /**
     * Forwards a command execution to the remote host and remembers the local
     * sender so the result can be delivered back.
     *
     * @return {@code true} if the request was forwarded
     */
    public boolean sendRemoteRequest(PluginCommandSender sender, String remoteLabel, List<String> arguments) {
        Objects.requireNonNull(sender, "sender");
        long requestId = nextRequestId.getAndIncrement();
        pending.put(requestId, sender);
        boolean console = "console".equalsIgnoreCase(sender.name()) || "Server".equalsIgnoreCase(sender.name());
        boolean sent = link.send(new CommandExecutePacket(
            requestId,
            remoteLabel,
            arguments,
            sender.name(),
            sender.operator(),
            console
        ));
        if (!sent) {
            pending.remove(requestId);
            sender.sendMessage("Cannot reach the " + remoteHostName() + " host; the bridge is not connected.");
            return false;
        }
        return true;
    }

    private String remoteHostName() {
        return localRole == HyperCoreRole.FORGE_HOST ? "fabric" : "forge";
    }
}
