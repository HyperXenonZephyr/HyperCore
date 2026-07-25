# Changelog

All notable changes to HyperCore are documented in this file. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project intends to follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Initial Forge 1.21.1 project using Forge 52.1.16 and Java 21.
- Dedicated-server HyperCore entry point and lifecycle logging.
- Managed background executor sized from the available logical processors.
- Rolling 200-tick latency metrics with average, p95, and maximum values.
- `/hypercore status` command for worker, task, and heap diagnostics.
- `/hypercore timings` command for recent tick latency diagnostics.
- Initial architecture, GPU policy, build instructions, and roadmap documentation.
- JUnit coverage for tick metrics and isolated worker execution.
- Verified Forge userdev server-side loading on Minecraft 1.21.1.
- Replaced the unbounded executor queue with bounded backpressure, rejection metrics, and pending-task cancellation on shutdown.
- Expanded `/hypercore status` with active worker, queue capacity, and rejection diagnostics.
- Added an automated Forge GameTest that verifies HyperCore in the dedicated-server environment.
- Added Forge common configuration for worker threads, task queue capacity, tick sampling, and optional GPU probing.
- Delayed worker-pool creation until the server lifecycle has loaded configuration.
- Added structured OS, JVM, CPU, graphics adapter, device ID, and VRAM capability detection through OSHI.
- Added `/hypercore capabilities` and exposed the active compute backend in `/hypercore status`.
- Added a `cpu-scalar` structure-of-arrays squared-distance backend as the correctness baseline for future vector and GPU implementations.
- Added tests for configuration resolution, runtime lifecycle, capability reporting, compute correctness, and custom tick windows.

### Fixed

- Disabled Gradle configuration cache because ForgeGradle 7 could restore an incomplete merged source set after `clean`, producing a JAR with missing classes.
- Disabled ForgeGradle's merged source-set output, restoring standard Gradle class directories and preventing clean builds from omitting unchanged classes.
- Staged development classes and resources into one exploded mod directory so Forge server and GameTest runs load HyperCore correctly with standard Gradle source-set outputs.
