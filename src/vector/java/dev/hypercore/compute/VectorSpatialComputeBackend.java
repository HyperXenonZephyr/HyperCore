package dev.hypercore.compute;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.util.Objects;

/**
 * CPU spatial compute backend backed by the Java Vector API ({@code jdk.incubator.vector}).
 *
 * <p>It is a peer of {@link ScalarSpatialComputeBackend} and computes the same
 * squared-distance and packed radius-mask operations. The arithmetic order matches
 * the scalar baseline exactly (per-lane {@code dx*dx + dy*dy + dz*dz} with the same
 * rounding), so results are bit-identical and the scalar backend remains the
 * correctness reference. Mask bits are packed 32 per word with the same
 * {@code 1 << (index & 31)} convention as the scalar path.
 *
 * <p>This backend lives in a separate source set because the Vector API is an
 * incubator module and is therefore not visible under {@code --release}. Main code
 * never references this class directly; it is loaded reflectively so that the
 * incubator module is only required when the vector path is actually exercised.
 */
public final class VectorSpatialComputeBackend implements SpatialComputeBackend {
    public static final String ID = "cpu-vector";

    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;
    private static final int SPECIES_LENGTH = SPECIES.length();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ComputeDeviceType deviceType() {
        return ComputeDeviceType.CPU;
    }

    /** The preferred vector species used by this backend, exposed for diagnostics. */
    public static VectorSpecies<Float> species() {
        return SPECIES;
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
        Objects.requireNonNull(positionsX, "positionsX");
        Objects.requireNonNull(positionsY, "positionsY");
        Objects.requireNonNull(positionsZ, "positionsZ");
        Objects.requireNonNull(output, "output");

        int size = positionsX.length;
        if (positionsY.length != size || positionsZ.length != size || output.length < size) {
            throw new IllegalArgumentException("Position arrays must have equal lengths and fit in output");
        }

        FloatVector originXVec = FloatVector.broadcast(SPECIES, originX);
        FloatVector originYVec = FloatVector.broadcast(SPECIES, originY);
        FloatVector originZVec = FloatVector.broadcast(SPECIES, originZ);

        int upperBound = SPECIES.loopBound(size);
        int index = 0;
        for (; index < upperBound; index += SPECIES_LENGTH) {
            FloatVector deltaX = FloatVector.fromArray(SPECIES, positionsX, index).sub(originXVec);
            FloatVector deltaY = FloatVector.fromArray(SPECIES, positionsY, index).sub(originYVec);
            FloatVector deltaZ = FloatVector.fromArray(SPECIES, positionsZ, index).sub(originZVec);
            FloatVector distance = deltaX.mul(deltaX).add(deltaY.mul(deltaY)).add(deltaZ.mul(deltaZ));
            distance.intoArray(output, index);
        }
        // Scalar tail for the final partial vector.
        for (; index < size; index++) {
            float deltaX = positionsX[index] - originX;
            float deltaY = positionsY[index] - originY;
            float deltaZ = positionsZ[index] - originZ;
            output[index] = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
        }
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
        Objects.requireNonNull(positionsX, "positionsX");
        Objects.requireNonNull(positionsY, "positionsY");
        Objects.requireNonNull(positionsZ, "positionsZ");
        Objects.requireNonNull(outputWords, "outputWords");
        int size = positionsX.length;
        int wordCount = SpatialComputeBackend.maskWordCount(size);
        if (positionsY.length != size || positionsZ.length != size || outputWords.length < wordCount) {
            throw new IllegalArgumentException("Position arrays must have equal lengths and fit in output mask");
        }
        if (Float.isNaN(squaredRadius) || squaredRadius < 0.0f) {
            throw new IllegalArgumentException("squaredRadius must be non-negative");
        }

        // A 32-bit word may span multiple vector iterations, so accumulate by OR.
        for (int word = 0; word < wordCount; word++) {
            outputWords[word] = 0;
        }

        FloatVector originXVec = FloatVector.broadcast(SPECIES, originX);
        FloatVector originYVec = FloatVector.broadcast(SPECIES, originY);
        FloatVector originZVec = FloatVector.broadcast(SPECIES, originZ);
        FloatVector radiusVec = FloatVector.broadcast(SPECIES, squaredRadius);

        int upperBound = SPECIES.loopBound(size);
        int index = 0;
        for (; index < upperBound; index += SPECIES_LENGTH) {
            FloatVector deltaX = FloatVector.fromArray(SPECIES, positionsX, index).sub(originXVec);
            FloatVector deltaY = FloatVector.fromArray(SPECIES, positionsY, index).sub(originYVec);
            FloatVector deltaZ = FloatVector.fromArray(SPECIES, positionsZ, index).sub(originZVec);
            FloatVector distance = deltaX.mul(deltaX).add(deltaY.mul(deltaY)).add(deltaZ.mul(deltaZ));
            VectorMask<Float> within = distance.compare(VectorOperators.LE, radiusVec);
            for (int lane = 0; lane < SPECIES_LENGTH; lane++) {
                if (within.laneIsSet(lane)) {
                    int absolute = index + lane;
                    outputWords[absolute >>> 5] |= 1 << (absolute & 31);
                }
            }
        }
        // Scalar tail for the final partial vector.
        for (; index < size; index++) {
            float deltaX = positionsX[index] - originX;
            float deltaY = positionsY[index] - originY;
            float deltaZ = positionsZ[index] - originZ;
            float distance = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
            if (distance <= squaredRadius) {
                outputWords[index >>> 5] |= 1 << (index & 31);
            }
        }
    }
}
