package dev.hypercore.server.compute;

/** Compile-only copy of the server extension API. Not packaged in the provider. */
public interface ComputeBackendProvider extends AutoCloseable {
    String id();

    boolean available();

    ComputeResult compute(ComputeRequest request) throws Exception;

    @Override
    default void close() {
    }
}
