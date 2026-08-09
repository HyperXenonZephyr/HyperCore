package dev.hypercore.compute;

/**
 * CPU scalar reference implementation of {@link LightPropagationBackend}.
 * Uses a double-buffered relaxation loop: each iteration reads from the
 * source array and writes to the destination, then swaps. This matches
 * the GPU shader's double-buffer semantics and provides a bit-exact oracle.
 */
public final class ScalarLightPropagationBackend implements LightPropagationBackend {
    public static final String ID = "cpu-scalar-light";
    private static final int MAX_LIGHT = 15;

    @Override
    public void propagate(int[] light, int[] opacity, int dimX, int dimY, int dimZ, int iterations) {
        int required = LightPropagationBackend.validate(light, opacity, dimX, dimY, dimZ, iterations);
        int[] source = new int[required];
        System.arraycopy(light, 0, source, 0, required);

        int[] read = source;
        int[] write = light;
        for (int iter = 0; iter < iterations; iter++) {
            for (int z = 0; z < dimZ; z++) {
                for (int y = 0; y < dimY; y++) {
                    for (int x = 0; x < dimX; x++) {
                        int idx = x + y * dimX + z * dimX * dimY;
                        if (opacity[idx] != 0) {
                            write[idx] = 0;
                            continue;
                        }
                        int current = read[idx];
                        int best = current;
                        if (x + 1 < dimX) {
                            int n = read[idx + 1];
                            if (n > 0) best = Math.max(best, n - 1);
                        }
                        if (x > 0) {
                            int n = read[idx - 1];
                            if (n > 0) best = Math.max(best, n - 1);
                        }
                        if (y + 1 < dimY) {
                            int n = read[idx + dimX];
                            if (n > 0) best = Math.max(best, n - 1);
                        }
                        if (y > 0) {
                            int n = read[idx - dimX];
                            if (n > 0) best = Math.max(best, n - 1);
                        }
                        if (z + 1 < dimZ) {
                            int n = read[idx + dimX * dimY];
                            if (n > 0) best = Math.max(best, n - 1);
                        }
                        if (z > 0) {
                            int n = read[idx - dimX * dimY];
                            if (n > 0) best = Math.max(best, n - 1);
                        }
                        write[idx] = Math.min(best, MAX_LIGHT);
                    }
                }
            }
            int[] tmp = read;
            read = write;
            write = tmp;
        }

        // After the loop, 'read' holds the most recent result.
        if (read != light) {
            System.arraycopy(read, 0, light, 0, required);
        }
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ComputeDeviceType deviceType() {
        return ComputeDeviceType.CPU;
    }
}
