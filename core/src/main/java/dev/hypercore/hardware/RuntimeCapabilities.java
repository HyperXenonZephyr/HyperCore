package dev.hypercore.hardware;

import oshi.SystemInfo;
import oshi.hardware.GraphicsCard;

import java.util.ArrayList;
import java.util.List;

public record RuntimeCapabilities(
    int logicalProcessors,
    String operatingSystem,
    String architecture,
    String javaVersion,
    GpuProbe gpu
) {
    public RuntimeCapabilities {
        if (logicalProcessors < 1) {
            throw new IllegalArgumentException("logicalProcessors must be positive");
        }
        gpu = gpu == null ? GpuProbe.failed("GPU probe returned no result") : gpu;
    }

    public static RuntimeCapabilities detect(boolean probeGpu) {
        Runtime runtime = Runtime.getRuntime();
        return new RuntimeCapabilities(
            runtime.availableProcessors(),
            property("os.name"),
            property("os.arch"),
            property("java.version"),
            probeGpu ? detectGpus() : GpuProbe.disabled()
        );
    }

    private static GpuProbe detectGpus() {
        try {
            List<GpuDevice> devices = new ArrayList<>();
            for (GraphicsCard card : new SystemInfo().getHardware().getGraphicsCards()) {
                devices.add(new GpuDevice(
                    normalized(card.getName()),
                    normalized(card.getVendor()),
                    normalized(card.getDeviceId()),
                    Math.max(0L, card.getVRam())
                ));
            }
            return GpuProbe.completed(devices);
        } catch (Throwable error) {
            return GpuProbe.failed(error.getClass().getSimpleName() + ": " + normalized(error.getMessage()));
        }
    }

    private static String property(String name) {
        return normalized(System.getProperty(name));
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    public record GpuDevice(String name, String vendor, String deviceId, long vramBytes) {
        public GpuDevice {
            name = normalized(name);
            vendor = normalized(vendor);
            deviceId = normalized(deviceId);
            if (vramBytes < 0L) {
                throw new IllegalArgumentException("vramBytes cannot be negative");
            }
        }
    }

    public record GpuProbe(boolean attempted, List<GpuDevice> devices, String error) {
        public GpuProbe {
            devices = List.copyOf(devices == null ? List.of() : devices);
        }

        public static GpuProbe disabled() {
            return new GpuProbe(false, List.of(), null);
        }

        public static GpuProbe completed(List<GpuDevice> devices) {
            return new GpuProbe(true, devices, null);
        }

        public static GpuProbe failed(String error) {
            return new GpuProbe(true, List.of(), normalized(error));
        }

        public boolean succeeded() {
            return attempted && error == null;
        }
    }
}
