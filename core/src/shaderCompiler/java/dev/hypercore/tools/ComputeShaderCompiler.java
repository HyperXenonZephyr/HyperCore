package dev.hypercore.tools;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ComputeShaderCompiler {
    private ComputeShaderCompiler() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Expected input and output paths");
        }

        Path input = Path.of(arguments[0]);
        Path output = Path.of(arguments[1]);
        String source = Files.readString(input, StandardCharsets.UTF_8);
        long compiler = Shaderc.shaderc_compiler_initialize();
        long options = Shaderc.shaderc_compile_options_initialize();
        if (compiler == MemoryUtil.NULL || options == MemoryUtil.NULL) {
            throw new IllegalStateException("Failed to initialize Shaderc");
        }

        long result = MemoryUtil.NULL;
        try {
            Shaderc.shaderc_compile_options_set_target_env(
                options,
                Shaderc.shaderc_target_env_vulkan,
                Shaderc.shaderc_env_version_vulkan_1_0
            );
            Shaderc.shaderc_compile_options_set_optimization_level(
                options,
                Shaderc.shaderc_optimization_level_performance
            );
            result = Shaderc.shaderc_compile_into_spv(
                compiler,
                source,
                Shaderc.shaderc_compute_shader,
                input.getFileName().toString(),
                "main",
                options
            );
            if (result == MemoryUtil.NULL) {
                throw new IllegalStateException("Shaderc returned no result");
            }
            int status = Shaderc.shaderc_result_get_compilation_status(result);
            if (status != Shaderc.shaderc_compilation_status_success) {
                throw new IllegalStateException(Shaderc.shaderc_result_get_error_message(result));
            }

            ByteBuffer bytecode = Shaderc.shaderc_result_get_bytes(result);
            byte[] copy = new byte[bytecode.remaining()];
            bytecode.get(copy);
            Files.createDirectories(output.getParent());
            Files.write(output, copy);
        } finally {
            if (result != MemoryUtil.NULL) {
                Shaderc.shaderc_result_release(result);
            }
            Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }
    }
}
