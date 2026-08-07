package dev.hypercore.compute;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Routes batch work to Vulkan when ready and keeps a deterministic CPU path available. */
public final class AdaptiveSpatialComputeBackend implements SpatialComputeBackend, AutoCloseable {
    public static final String VULKAN_ID = "adaptive-vulkan";
    private static final Logger LOGGER = LoggerFactory.getLogger(AdaptiveSpatialComputeBackend.class);
    private static final long INITIALIZER_JOIN_MILLIS = 5_000L;

    private final SpatialComputeBackend cpu;
    private final GpuOffloadPolicy policy;
    private final AtomicLong cpuBatches = new AtomicLong();
    private final AtomicLong gpuBatches = new AtomicLong();
    private final AtomicLong gpuFailures = new AtomicLong();
    private final AtomicLong cpuRadiusMaskBatches = new AtomicLong();
    private final AtomicLong gpuRadiusMaskBatches = new AtomicLong();
    private final AtomicLong gpuRadiusMaskReadbackBytes = new AtomicLong();
    private final AtomicLong gpuSnapshotUploads = new AtomicLong();
    private final AtomicLong gpuSnapshotReuses = new AtomicLong();
    private final AtomicLong gpuMultiQueryBatches = new AtomicLong();
    private final AtomicLong gpuMultiQueryQueries = new AtomicLong();
    private final AtomicLong spatialQueries = new AtomicLong();
    private final AtomicLong spatialCandidates = new AtomicLong();
    private final AtomicLong spatialMatches = new AtomicLong();
    private final CountDownLatch initializationComplete;
    private final GpuBackendFactory gpuFactory;
    private final long initializationStartedNanos;
    private volatile long initializationFinishedNanos;
    private volatile InitializationState initializationState;
    private volatile ManagedSpatialComputeBackend gpu;
    private AdaptivePositionSnapshot activeGpuSnapshot;
    private volatile String unavailableReason;
    private volatile Thread initializer;
    private volatile boolean closed;

    private AdaptiveSpatialComputeBackend(
        GpuOffloadPolicy policy,
        ManagedSpatialComputeBackend gpu,
        InitializationState state,
        String unavailableReason,
        GpuBackendFactory gpuFactory,
        SpatialComputeBackend cpu
    ) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.gpu = gpu;
        this.cpu = Objects.requireNonNull(cpu, "cpu");
        this.initializationState = Objects.requireNonNull(state, "state");
        this.unavailableReason = Objects.requireNonNullElse(unavailableReason, "");
        this.gpuFactory = gpuFactory;
        this.initializationComplete = new CountDownLatch(state == InitializationState.INITIALIZING ? 1 : 0);
        this.initializationStartedNanos = System.nanoTime();
        this.initializationFinishedNanos = state == InitializationState.INITIALIZING ? 0L : initializationStartedNanos;
    }

    /** Starts Vulkan creation off the server lifecycle thread. */
    public static AdaptiveSpatialComputeBackend create(GpuOffloadPolicy policy, boolean enabled) {
        return create(policy, enabled, new ScalarSpatialComputeBackend());
    }

    public static AdaptiveSpatialComputeBackend create(
        GpuOffloadPolicy policy, boolean enabled, SpatialComputeBackend cpu
    ) {
        if (!enabled) {
            return unavailable(policy, "disabled by configuration", cpu);
        }
        AdaptiveSpatialComputeBackend backend = new AdaptiveSpatialComputeBackend(
            policy, null, InitializationState.INITIALIZING, "", VulkanSpatialComputeBackend::create, cpu);
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        Thread initializer = new Thread(() -> {
            Thread.currentThread().setContextClassLoader(contextLoader);
            backend.initializeGpu();
        }, "HyperCore-Vulkan-Init");
        initializer.setDaemon(true);
        backend.initializer = initializer;
        initializer.start();
        return backend;
    }

    public static AdaptiveSpatialComputeBackend unavailable(GpuOffloadPolicy policy, String reason) {
        return unavailable(policy, reason, new ScalarSpatialComputeBackend());
    }

    public static AdaptiveSpatialComputeBackend unavailable(
        GpuOffloadPolicy policy, String reason, SpatialComputeBackend cpu
    ) {
        return new AdaptiveSpatialComputeBackend(policy, null, InitializationState.UNAVAILABLE, reason, null, cpu);
    }

    static AdaptiveSpatialComputeBackend createForTesting(GpuOffloadPolicy policy, GpuBackendFactory gpuFactory) {
        return createForTesting(policy, gpuFactory, new ScalarSpatialComputeBackend());
    }

    static AdaptiveSpatialComputeBackend createForTesting(
        GpuOffloadPolicy policy, GpuBackendFactory gpuFactory, SpatialComputeBackend cpu
    ) {
        AdaptiveSpatialComputeBackend backend = new AdaptiveSpatialComputeBackend(
            policy, null, InitializationState.INITIALIZING, "",
            Objects.requireNonNull(gpuFactory, "gpuFactory"), cpu);
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        Thread initializer = new Thread(() -> {
            Thread.currentThread().setContextClassLoader(contextLoader);
            backend.initializeGpu();
        }, "HyperCore-Vulkan-Init-Test");
        initializer.setDaemon(true);
        backend.initializer = initializer;
        initializer.start();
        return backend;
    }

    @Override
    public String id() {
        return gpu == null ? cpu.id() : VULKAN_ID;
    }

    @Override
    public ComputeDeviceType deviceType() {
        return gpu == null ? ComputeDeviceType.CPU : ComputeDeviceType.GPU;
    }

    @Override
    public SpatialComputeBackend.PositionSnapshot prepareSnapshot(
        float[] positionsX,
        float[] positionsY,
        float[] positionsZ
    ) {
        validatePositions(positionsX, positionsY, positionsZ);
        return prepareOwnedSnapshot(
            positionsX.clone(),
            positionsY.clone(),
            positionsZ.clone()
        );
    }

    SpatialComputeBackend.PositionSnapshot prepareOwnedSnapshot(
        float[] positionsX,
        float[] positionsY,
        float[] positionsZ
    ) {
        validatePositions(positionsX, positionsY, positionsZ);
        return new AdaptivePositionSnapshot(
            this,
            positionsX,
            positionsY,
            positionsZ
        );
    }

    @Override
    public void squaredDistances(
        float originX,
        float originY,
        float originZ,
        float[] positionsX,
        float[] positionsY,
        float[] positionsZ,
        float[] output
    ) {
        int size = validate(positionsX, positionsY, positionsZ, output);
        ManagedSpatialComputeBackend currentGpu = gpu;
        GpuOffloadPolicy.Decision decision = policy.evaluate(size, currentGpu != null, currentGpu != null);
        if (decision.offload()) {
            try {
                // Keep dispatch and native cleanup mutually exclusive.
                synchronized (this) {
                    if (closed || gpu != currentGpu) {
                        throw new IllegalStateException("Vulkan compute backend is closing");
                    }
                    currentGpu.squaredDistances(originX, originY, originZ, positionsX, positionsY, positionsZ, output);
                    activeGpuSnapshot = null;
                }
                gpuBatches.incrementAndGet();
                return;
            } catch (VulkanSpatialComputeBackend.BatchNotSupportedException unsupported) {
                // This batch can still run correctly on the scalar fallback.
            } catch (RuntimeException | LinkageError error) {
                disableGpu(currentGpu, error);
            }
        }

        cpu.squaredDistances(originX, originY, originZ, positionsX, positionsY, positionsZ, output);
        cpuBatches.incrementAndGet();
    }

    @Override
    public void radiusMask(
        float originX,
        float originY,
        float originZ,
        float squaredRadius,
        float[] positionsX,
        float[] positionsY,
        float[] positionsZ,
        int[] outputWords
    ) {
        int size = validateMask(positionsX, positionsY, positionsZ, outputWords);
        if (Float.isNaN(squaredRadius) || squaredRadius < 0.0f) {
            throw new IllegalArgumentException("squaredRadius must be non-negative");
        }
        ManagedSpatialComputeBackend currentGpu = gpu;
        GpuOffloadPolicy.Decision decision = policy.evaluate(size, currentGpu != null, currentGpu != null);
        if (decision.offload()) {
            try {
                synchronized (this) {
                    if (closed || gpu != currentGpu) {
                        throw new IllegalStateException("Vulkan compute backend is closing");
                    }
                    currentGpu.radiusMask(
                        originX, originY, originZ, squaredRadius,
                        positionsX, positionsY, positionsZ, outputWords
                    );
                    activeGpuSnapshot = null;
                }
                gpuBatches.incrementAndGet();
                gpuRadiusMaskBatches.incrementAndGet();
                gpuRadiusMaskReadbackBytes.addAndGet(
                    (long) SpatialComputeBackend.maskWordCount(size) * Integer.BYTES
                );
                return;
            } catch (VulkanSpatialComputeBackend.BatchNotSupportedException unsupported) {
                // This batch can still run correctly on the scalar fallback.
            } catch (RuntimeException | LinkageError error) {
                disableGpu(currentGpu, error);
            }
        }

        cpu.radiusMask(
            originX, originY, originZ, squaredRadius,
            positionsX, positionsY, positionsZ, outputWords
        );
        cpuBatches.incrementAndGet();
        cpuRadiusMaskBatches.incrementAndGet();
    }

    private void radiusMaskFromSnapshot(
        AdaptivePositionSnapshot snapshot,
        float originX,
        float originY,
        float originZ,
        float squaredRadius,
        int[] outputWords
    ) {
        if (snapshot.closed) {
            throw new IllegalStateException("Position snapshot is closed");
        }
        int size = validateMask(snapshot.positionsX, snapshot.positionsY, snapshot.positionsZ, outputWords);
        if (Float.isNaN(squaredRadius) || squaredRadius < 0.0f) {
            throw new IllegalArgumentException("squaredRadius must be non-negative");
        }
        ManagedSpatialComputeBackend currentGpu = gpu;
        GpuOffloadPolicy.Decision decision = policy.evaluate(size, currentGpu != null, currentGpu != null);
        if (decision.offload()) {
            boolean uploaded = false;
            try {
                synchronized (this) {
                    if (closed || snapshot.closed || gpu != currentGpu) {
                        throw new IllegalStateException("Vulkan compute backend is closing");
                    }
                    if (snapshot.gpuBackend != currentGpu) {
                        if (snapshot.gpuSnapshot != null) {
                            snapshot.gpuSnapshot.close();
                        }
                        snapshot.gpuSnapshot = currentGpu instanceof VulkanSpatialComputeBackend vulkan
                            ? vulkan.prepareOwnedSnapshot(
                                snapshot.positionsX, snapshot.positionsY, snapshot.positionsZ
                            )
                            : currentGpu.prepareSnapshot(
                                snapshot.positionsX, snapshot.positionsY, snapshot.positionsZ
                            );
                        snapshot.gpuBackend = currentGpu;
                    }
                    uploaded = activeGpuSnapshot != snapshot;
                    snapshot.gpuSnapshot.radiusMask(
                        originX, originY, originZ, squaredRadius, outputWords
                    );
                    activeGpuSnapshot = snapshot;
                }
                gpuBatches.incrementAndGet();
                gpuRadiusMaskBatches.incrementAndGet();
                gpuRadiusMaskReadbackBytes.addAndGet(
                    (long) SpatialComputeBackend.maskWordCount(size) * Integer.BYTES
                );
                if (uploaded) {
                    gpuSnapshotUploads.incrementAndGet();
                } else {
                    gpuSnapshotReuses.incrementAndGet();
                }
                return;
            } catch (VulkanSpatialComputeBackend.BatchNotSupportedException unsupported) {
                // This batch can still run correctly on the scalar fallback.
            } catch (RuntimeException | LinkageError error) {
                disableGpu(currentGpu, error);
            }
        }

        cpu.radiusMask(
            originX, originY, originZ, squaredRadius,
            snapshot.positionsX, snapshot.positionsY, snapshot.positionsZ, outputWords
        );
        cpuBatches.incrementAndGet();
        cpuRadiusMaskBatches.incrementAndGet();
    }

    private void radiusMasksFromSnapshot(
        AdaptivePositionSnapshot snapshot,
        SpatialComputeBackend.RadiusMaskQuery[] queries,
        int[] outputWords
    ) {
        if (snapshot.closed) {
            throw new IllegalStateException("Position snapshot is closed");
        }
        Objects.requireNonNull(queries, "queries");
        Objects.requireNonNull(outputWords, "outputWords");
        int size = snapshot.positionsX.length;
        int wordCount = SpatialComputeBackend.maskWordCount(size);
        long requiredWords = (long) wordCount * queries.length;
        if (requiredWords > outputWords.length) {
            throw new IllegalArgumentException("Output mask cannot fit every radius query");
        }
        for (int queryIndex = 0; queryIndex < queries.length; queryIndex++) {
            Objects.requireNonNull(queries[queryIndex], "queries[" + queryIndex + "]");
        }
        if (queries.length == 0) {
            return;
        }

        ManagedSpatialComputeBackend currentGpu = gpu;
        GpuOffloadPolicy.Decision decision = policy.evaluate(size, currentGpu != null, currentGpu != null);
        if (decision.offload()) {
            boolean uploaded;
            try {
                synchronized (this) {
                    if (closed || snapshot.closed || gpu != currentGpu) {
                        throw new IllegalStateException("Vulkan compute backend is closing");
                    }
                    if (snapshot.gpuBackend != currentGpu) {
                        if (snapshot.gpuSnapshot != null) {
                            snapshot.gpuSnapshot.close();
                        }
                        snapshot.gpuSnapshot = currentGpu instanceof VulkanSpatialComputeBackend vulkan
                            ? vulkan.prepareOwnedSnapshot(
                                snapshot.positionsX, snapshot.positionsY, snapshot.positionsZ
                            )
                            : currentGpu.prepareSnapshot(
                                snapshot.positionsX, snapshot.positionsY, snapshot.positionsZ
                            );
                        snapshot.gpuBackend = currentGpu;
                    }
                    uploaded = activeGpuSnapshot != snapshot;
                    snapshot.gpuSnapshot.radiusMasks(queries, outputWords);
                    activeGpuSnapshot = snapshot;
                }
                gpuBatches.incrementAndGet();
                gpuRadiusMaskBatches.addAndGet(queries.length);
                gpuRadiusMaskReadbackBytes.addAndGet(requiredWords * Integer.BYTES);
                gpuMultiQueryBatches.incrementAndGet();
                gpuMultiQueryQueries.addAndGet(queries.length);
                if (uploaded) {
                    gpuSnapshotUploads.incrementAndGet();
                } else {
                    gpuSnapshotReuses.incrementAndGet();
                }
                return;
            } catch (VulkanSpatialComputeBackend.BatchNotSupportedException unsupported) {
                // This batch can still run correctly on the scalar fallback.
            } catch (RuntimeException | LinkageError error) {
                disableGpu(currentGpu, error);
            }
        }

        int[] singleMask = new int[wordCount];
        for (int queryIndex = 0; queryIndex < queries.length; queryIndex++) {
            SpatialComputeBackend.RadiusMaskQuery query = queries[queryIndex];
            cpu.radiusMask(
                query.originX(), query.originY(), query.originZ(), query.squaredRadius(),
                snapshot.positionsX, snapshot.positionsY, snapshot.positionsZ, singleMask
            );
            System.arraycopy(singleMask, 0, outputWords, queryIndex * wordCount, wordCount);
        }
        cpuBatches.incrementAndGet();
        cpuRadiusMaskBatches.addAndGet(queries.length);
    }

    void recordSpatialQuery(int candidateCount, int matchCount) {
        spatialQueries.incrementAndGet();
        spatialCandidates.addAndGet(candidateCount);
        spatialMatches.addAndGet(matchCount);
    }

    public synchronized Status status() {
        ManagedSpatialComputeBackend currentGpu = gpu;
        long finished = initializationFinishedNanos;
        long duration = finished == 0L ? System.nanoTime() - initializationStartedNanos : finished - initializationStartedNanos;
        return new Status(
            currentGpu != null,
            initializationState,
            duration / 1_000_000L,
            currentGpu == null ? "" : currentGpu.deviceName(),
            currentGpu == null ? "" : currentGpu.transferMode(),
            policy.minimumBatchSize(),
            cpuBatches.get(),
            gpuBatches.get(),
            gpuFailures.get(),
            cpuRadiusMaskBatches.get(),
            gpuRadiusMaskBatches.get(),
            gpuRadiusMaskReadbackBytes.get(),
            gpuSnapshotUploads.get(),
            gpuSnapshotReuses.get(),
            gpuMultiQueryBatches.get(),
            gpuMultiQueryQueries.get(),
            spatialQueries.get(),
            spatialCandidates.get(),
            spatialMatches.get(),
            unavailableReason
        );
    }

    boolean awaitInitialization(long timeout, TimeUnit unit) throws InterruptedException {
        return initializationComplete.await(timeout, unit);
    }

    @Override
    public void close() {
        Thread pendingInitializer;
        ManagedSpatialComputeBackend currentGpu;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            initializationState = InitializationState.CLOSED;
            if (initializationFinishedNanos == 0L) {
                initializationFinishedNanos = System.nanoTime();
            }
            pendingInitializer = initializer;
            initializer = null;
            currentGpu = gpu;
            gpu = null;
            activeGpuSnapshot = null;
        }
        if (pendingInitializer != null && pendingInitializer != Thread.currentThread()) {
            pendingInitializer.interrupt();
            try {
                pendingInitializer.join(INITIALIZER_JOIN_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Interrupted while waiting for Vulkan initialization to stop");
            }
        }
        if (currentGpu != null) {
            closeGpu(currentGpu);
        }
    }

    private void initializeGpu() {
        ManagedSpatialComputeBackend created = null;
        try {
            created = gpuFactory.create();
            verify(created);
            synchronized (this) {
                if (closed) {
                    closeGpu(created);
                    return;
                }
                gpu = created;
                initializationState = InitializationState.READY;
                initializationFinishedNanos = System.nanoTime();
                unavailableReason = "";
                initializer = null;
            }
            LOGGER.info("Vulkan compute initialized asynchronously on {}", created.deviceName());
        } catch (RuntimeException | LinkageError error) {
            if (created != null) {
                closeGpu(created);
            }
            String reason = error.getClass().getSimpleName() + ": " + normalize(error.getMessage());
            synchronized (this) {
                if (!closed) {
                    initializationState = InitializationState.UNAVAILABLE;
                    initializationFinishedNanos = System.nanoTime();
                    unavailableReason = reason;
                    initializer = null;
                }
            }
            if (!closed) {
                LOGGER.warn("Vulkan compute initialization failed; using {}: {}", cpu.id(), reason, error);
            }
        } finally {
            initializationComplete.countDown();
        }
    }

    private synchronized void disableGpu(ManagedSpatialComputeBackend failedGpu, Throwable error) {
        if (gpu != failedGpu || closed) {
            return;
        }
        gpu = null;
        activeGpuSnapshot = null;
        initializationState = InitializationState.UNAVAILABLE;
        gpuFailures.incrementAndGet();
        unavailableReason = error.getClass().getSimpleName() + ": " + normalize(error.getMessage());
        LOGGER.error("Vulkan compute failed and has been disabled; using {}", cpu.id(), error);
        closeGpu(failedGpu);
    }

    private static void closeGpu(ManagedSpatialComputeBackend backend) {
        try {
            backend.close();
        } catch (RuntimeException | LinkageError closeError) {
            LOGGER.warn("Vulkan compute cleanup failed", closeError);
        }
    }

    private static void verify(SpatialComputeBackend gpu) {
        int size = 1_024;
        float[] x = new float[size];
        float[] y = new float[size];
        float[] z = new float[size];
        float[] expected = new float[size];
        float[] actual = new float[size];
        for (int index = 0; index < size; index++) {
            x[index] = index * 0.25f - 30.0f;
            y[index] = index % 17 - 8.0f;
            z[index] = index % 31 * 0.5f;
        }
        new ScalarSpatialComputeBackend().squaredDistances(1.25f, -2.5f, 4.0f, x, y, z, expected);
        gpu.squaredDistances(1.25f, -2.5f, 4.0f, x, y, z, actual);
        for (int index = 0; index < size; index++) {
            float tolerance = Math.max(1.0e-4f, Math.abs(expected[index]) * 1.0e-5f);
            if (Math.abs(expected[index] - actual[index]) > tolerance) {
                throw new IllegalStateException(
                    "Vulkan self-test mismatch at index " + index
                        + ": expected=" + expected[index]
                        + ", actual=" + actual[index]
                );
            }
        }

        int wordCount = SpatialComputeBackend.maskWordCount(size);
        int[] expectedMask = new int[wordCount];
        int[] actualMask = new int[wordCount];
        new ScalarSpatialComputeBackend().radiusMask(
            1.25f, -2.5f, 4.0f, 4_096.0f, x, y, z, expectedMask
        );
        gpu.radiusMask(1.25f, -2.5f, 4.0f, 4_096.0f, x, y, z, actualMask);
        verifyMask("radius-mask", expectedMask, actualMask);

        float[] shiftedX = x.clone();
        for (int index = 0; index < shiftedX.length; index++) {
            shiftedX[index] += 10_000.0f;
        }
        int[] shiftedMask = new int[wordCount];
        int[] actualShiftedMask = new int[wordCount];
        new ScalarSpatialComputeBackend().radiusMask(
            1.25f, -2.5f, 4.0f, 4_096.0f, shiftedX, y, z, shiftedMask
        );
        try (
            SpatialComputeBackend.PositionSnapshot original = gpu.prepareSnapshot(x, y, z);
            SpatialComputeBackend.PositionSnapshot shifted = gpu.prepareSnapshot(shiftedX, y, z)
        ) {
            original.radiusMask(1.25f, -2.5f, 4.0f, 4_096.0f, actualMask);
            verifyMask("resident-original", expectedMask, actualMask);
            shifted.radiusMask(1.25f, -2.5f, 4.0f, 4_096.0f, actualShiftedMask);
            verifyMask("resident-shifted", shiftedMask, actualShiftedMask);
            original.radiusMask(1.25f, -2.5f, 4.0f, 4_096.0f, actualMask);
            verifyMask("resident-restored", expectedMask, actualMask);

            SpatialComputeBackend.RadiusMaskQuery[] queries =
                new SpatialComputeBackend.RadiusMaskQuery[33];
            for (int queryIndex = 0; queryIndex < queries.length; queryIndex++) {
                queries[queryIndex] = new SpatialComputeBackend.RadiusMaskQuery(
                    queryIndex - 16.0f,
                    queryIndex % 7 - 3.0f,
                    queryIndex % 11 - 5.0f,
                    4_096.0f + queryIndex * 16.0f
                );
            }
            int[] expectedBatchMask = new int[wordCount * queries.length];
            int[] actualBatchMask = new int[expectedBatchMask.length];
            try (SpatialComputeBackend.PositionSnapshot scalar =
                     new ScalarSpatialComputeBackend().prepareSnapshot(x, y, z)) {
                scalar.radiusMasks(queries, expectedBatchMask);
            }
            original.radiusMasks(queries, actualBatchMask);
            verifyMask("resident-multi-query", expectedBatchMask, actualBatchMask);
        }
    }

    private static void verifyMask(String operation, int[] expected, int[] actual) {
        for (int word = 0; word < expected.length; word++) {
            if (expected[word] != actual[word]) {
                throw new IllegalStateException(
                    "Vulkan " + operation + " self-test mismatch at word " + word
                        + ": expected=" + Integer.toUnsignedString(expected[word])
                        + ", actual=" + Integer.toUnsignedString(actual[word])
                );
            }
        }
    }

    private static String normalize(String message) {
        return message == null || message.isBlank() ? "unknown" : message.trim();
    }

    private static void validatePositions(float[] x, float[] y, float[] z) {
        Objects.requireNonNull(x, "positionsX");
        Objects.requireNonNull(y, "positionsY");
        Objects.requireNonNull(z, "positionsZ");
        if (y.length != x.length || z.length != x.length) {
            throw new IllegalArgumentException("Position arrays must have equal lengths");
        }
    }

    private static int validate(float[] x, float[] y, float[] z, float[] output) {
        Objects.requireNonNull(x, "positionsX");
        Objects.requireNonNull(y, "positionsY");
        Objects.requireNonNull(z, "positionsZ");
        Objects.requireNonNull(output, "output");
        int size = x.length;
        if (y.length != size || z.length != size || output.length < size) {
            throw new IllegalArgumentException("Position arrays must have equal lengths and fit in output");
        }
        return size;
    }

    private static int validateMask(float[] x, float[] y, float[] z, int[] output) {
        Objects.requireNonNull(x, "positionsX");
        Objects.requireNonNull(y, "positionsY");
        Objects.requireNonNull(z, "positionsZ");
        Objects.requireNonNull(output, "outputWords");
        int size = x.length;
        if (y.length != size || z.length != size || output.length < SpatialComputeBackend.maskWordCount(size)) {
            throw new IllegalArgumentException("Position arrays must have equal lengths and fit in output mask");
        }
        return size;
    }

    private static final class AdaptivePositionSnapshot implements SpatialComputeBackend.PositionSnapshot {
        private final AdaptiveSpatialComputeBackend owner;
        private final float[] positionsX;
        private final float[] positionsY;
        private final float[] positionsZ;
        private ManagedSpatialComputeBackend gpuBackend;
        private SpatialComputeBackend.PositionSnapshot gpuSnapshot;
        private volatile boolean closed;

        private AdaptivePositionSnapshot(
            AdaptiveSpatialComputeBackend owner,
            float[] positionsX,
            float[] positionsY,
            float[] positionsZ
        ) {
            this.owner = owner;
            this.positionsX = positionsX;
            this.positionsY = positionsY;
            this.positionsZ = positionsZ;
        }

        @Override
        public int size() {
            return positionsX.length;
        }

        @Override
        public void radiusMask(
            float originX,
            float originY,
            float originZ,
            float squaredRadius,
            int[] outputWords
        ) {
            owner.radiusMaskFromSnapshot(
                this, originX, originY, originZ, squaredRadius, outputWords
            );
        }

        @Override
        public void radiusMasks(
            SpatialComputeBackend.RadiusMaskQuery[] queries,
            int[] outputWords
        ) {
            owner.radiusMasksFromSnapshot(this, queries, outputWords);
        }

        @Override
        public void close() {
            synchronized (owner) {
                if (closed) {
                    return;
                }
                closed = true;
                if (owner.activeGpuSnapshot == this) {
                    owner.activeGpuSnapshot = null;
                }
                if (gpuSnapshot != null) {
                    gpuSnapshot.close();
                    gpuSnapshot = null;
                    gpuBackend = null;
                }
            }
        }
    }

    public enum InitializationState {
        INITIALIZING,
        READY,
        UNAVAILABLE,
        CLOSED
    }

    @FunctionalInterface
    interface GpuBackendFactory {
        ManagedSpatialComputeBackend create();
    }

    public record Status(
        boolean gpuAvailable,
        InitializationState initializationState,
        long initializationDurationMillis,
        String deviceName,
        String transferMode,
        int minimumBatchSize,
        long cpuBatches,
        long gpuBatches,
        long gpuFailures,
        long cpuRadiusMaskBatches,
        long gpuRadiusMaskBatches,
        long gpuRadiusMaskReadbackBytes,
        long gpuSnapshotUploads,
        long gpuSnapshotReuses,
        long gpuMultiQueryBatches,
        long gpuMultiQueryQueries,
        long spatialQueries,
        long spatialCandidates,
        long spatialMatches,
        String unavailableReason
    ) {
    }
}
