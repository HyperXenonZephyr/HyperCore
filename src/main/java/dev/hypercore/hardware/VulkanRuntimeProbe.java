package dev.hypercore.hardware;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Platform;
import com.sun.jna.ptr.IntByReference;

public final class VulkanRuntimeProbe {
    private static final int VK_SUCCESS = 0;

    private VulkanRuntimeProbe() {
    }

    public static Result detect() {
        String libraryName = Platform.isWindows() ? "vulkan-1" : "vulkan";
        try (NativeLibrary library = NativeLibrary.getInstance(libraryName)) {
            Function enumerateVersion;
            try {
                enumerateVersion = library.getFunction("vkEnumerateInstanceVersion");
            } catch (UnsatisfiedLinkError missingVersionFunction) {
                return Result.available(libraryName, "1.0.0");
            }

            IntByReference version = new IntByReference();
            int result = enumerateVersion.invokeInt(new Object[] {version});
            if (result != VK_SUCCESS) {
                return Result.failed("vkEnumerateInstanceVersion returned " + result);
            }
            return Result.available(libraryName, formatVersion(version.getValue()));
        } catch (Throwable error) {
            return Result.failed(error.getClass().getSimpleName() + ": " + normalize(error.getMessage()));
        }
    }

    public static Result disabled() {
        return new Result(false, false, "", "", null);
    }

    static String formatVersion(int version) {
        int major = version >>> 22;
        int minor = (version >>> 12) & 0x3ff;
        int patch = version & 0xfff;
        return major + "." + minor + "." + patch;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    public record Result(
        boolean attempted,
        boolean available,
        String library,
        String apiVersion,
        String error
    ) {
        public static Result available(String library, String apiVersion) {
            return new Result(true, true, library, apiVersion, null);
        }

        public static Result failed(String error) {
            return new Result(true, false, "", "", normalize(error));
        }
    }
}
