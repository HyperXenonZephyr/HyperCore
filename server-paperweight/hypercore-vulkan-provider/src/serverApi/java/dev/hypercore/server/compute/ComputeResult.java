package dev.hypercore.server.compute;

import java.util.Objects;

/** Compile-only copy of the server extension API. Not packaged in the provider. */
public record ComputeResult(String backend, double[] values) {
    public ComputeResult {
        backend = Objects.requireNonNull(backend, "backend");
        values = Objects.requireNonNull(values, "values").clone();
    }

    @Override
    public double[] values() {
        return this.values.clone();
    }
}
