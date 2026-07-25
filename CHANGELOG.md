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
- Verified Forge userdev server-side loading on Minecraft 1.21.1; the empty GameTest target exits only because no test functions are registered yet.
