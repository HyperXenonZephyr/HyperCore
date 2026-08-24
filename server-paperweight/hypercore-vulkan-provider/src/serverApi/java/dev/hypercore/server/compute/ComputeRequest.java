package dev.hypercore.server.compute;

import java.util.Objects;

/** Compile-only copy of the server extension API. Not packaged in the provider. */
public record ComputeRequest(String operation, double[] values) {
    public ComputeRequest {
        operation = Objects.requireNonNull(operation, "operation").trim();
        if (operation.isEmpty()) throw new IllegalArgumentException("operation cannot be blank");
        values = Objects.requireNonNull(values, "values").clone();
    }

    @Override
    public double[] values() {
        return this.values.clone();
    }
}
