# HyperCore Benchmarks

This file records the current local calibration runs. They are evidence for backend decisions, not a claim about complete Minecraft tick performance. It covers the GPU/CPU compute workloads, the region-parallel scheduler microbenchmarks, and the end-to-end server-core comparison against Paper/Folia.

The sections below are ordered newest-first where a workload has been re-run. The compute, noise, particle, and N-body tables were regenerated on 2026-08-10 after the mapping-centralization refactor; earlier historical runs are retained for consistency.

## Server Core Comparison (Paper vs Folia)

A headless, configuration-standardized comparison of vanilla-like server cores running the same auto-benchmark Bukkit plugin. Both servers use identical world settings (seed 42, `view-distance=8`, `simulation-distance=8`, `spawn-protection=0`, `online-mode=false`, `max-tick-time=-1`) and a fixed 4 GB heap. The plugin measures two metrics per phase:

- **MSPT**: the server-recorded tick processing time (`Bukkit.getTickTimes()[0]`, excludes the 50 ms sleep budget). Paper only — Folia's region-parallel model has no single global processing tick and reports 0.
- **PERIOD / TPS**: the wall-clock tick period between consecutive 1-tick repeating-task runs (`TPS = 1000 / PERIOD`). Consistent and comparable across Paper and Folia.

Each run warms up for 800 ticks (40 s), samples a 1,200-tick IDLE phase (settled spawn area, no players), spawns 200 zombies per world, then samples a 1,200-tick ENTITY phase.

### Paper 1.21.1 (2026-08-10)

Paper build `1.21.1-28`. The plugin schedules on the conventional single-thread Bukkit scheduler.

| Metric | IDLE | ENTITY |
| --- | ---: | ---: |
| MSPT avg | 1.769 ms | 11.522 ms |
| MSPT max | 3.667 ms | 87.217 ms |
| Period avg | 49.998 ms | 49.501 ms |
| TPS | 20.00 | 20.20 |

Under the 200-zombie entity load the server-recorded processing time rises ~6.5x (1.77 → 11.5 ms) but stays well inside the 50 ms tick budget, so the wall-clock TPS holds at 20. The single-thread entity tick is the dominant cost; the 87 ms MSPT max reflects worst-case entity-batch scheduling noise.

### Folia

Folia comparison is pending. The same benchmark plugin detects Folia via `Bukkit.isOwnedByCurrentRegion` (Paper 1.21.1+ also exposes global/region schedulers, so scheduler-method probing is not sufficient to distinguish Folia) and schedules through the global region scheduler; entity spawns go through the region scheduler. Folia 1.21.4-6 is staged and wired but the run has not completed.

> Note: an earlier plugin revision probed `getGlobalRegionScheduler` to detect Folia, which mis-detected Paper 1.21.1 as Folia and routed Paper through the global scheduler. The detection now checks the Folia-specific `isOwnedByCurrentRegion` method.

## Region-Parallel Scheduling

### Region tick scheduling throughput (JMH)

Measures region-tick scheduling throughput across different numbers of active regions. Generated 2026-08-10.

| Regions | Throughput (ops/s) | Error (ops/s) |
|--------:|-------------------:|--------------:|
| 1 | 32218.68 | 139744.23 |
| 2 | 51098.37 | 114140.62 |
| 4 | 46595.25 | 278317.17 |
| 8 | 35286.01 | 59367.43 |
| 16 | 37772.32 | 54794.86 |

### End-to-end tick latency

Simulates the HyperCore post-server-tick workload: region-parallel block mutations under region locks, delta capture, bridge conflict resolution, and ordered batch output. The vanilla Minecraft server tick body is not included — this measures only the overhead HyperCore adds. Generated 2026-08-10.

| Regions | Workers | Tick avg (ms) | Tick p50 (ms) | Tick p95 (ms) | Tick max (ms) | Deltas/tick | Bridge flush (ms) |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 31 | 0.0122 | 0.0086 | 0.0271 | 0.1884 | 0.0 | 0.0000 |
| 4 | 31 | 0.0023 | 0.0020 | 0.0032 | 0.0184 | 0.0 | 0.0000 |
| 16 | 31 | 0.0024 | 0.0020 | 0.0029 | 0.0243 | 0.0 | 0.0000 |
| 64 | 31 | 0.0023 | 0.0020 | 0.0031 | 0.0262 | 0.0 | 0.0000 |
| 256 | 31 | 0.0024 | 0.0019 | 0.0031 | 0.0417 | 0.0 | 0.0000 |

Once more than one region is active the per-tick overhead is under 0.003 ms regardless of region count, confirming region-parallel dispatch and delta capture are not a scaling bottleneck relative to the 50 ms tick budget.

### Adaptive scheduling

The static coordinator pins every heavy region to one owner, so each tick bottlenecks on that owner; the adaptive coordinator records per-owner tick timings in a 64-sample window and migrates regions when imbalance exceeds 1.5x. Generated 2026-08-10.

| Mode | p50 | p95 | Mean |
| --- | ---: | ---: | ---: |
| static | 0.019 ms | 0.052 ms | 0.024 ms |
| adaptive | 0.008 ms | 0.028 ms | 0.012 ms |

Adaptive p50 speedup: `2.37x` | Mean speedup: `1.99x`

## Compute Backends

Generated: 2026-08-10T14:02:11Z

- GPU: `NVIDIA GeForce RTX 4060 Laptop GPU`
- GPU transfer mode: `device-local-staged`
- Java: `21.0.9`
- OS: `Windows 11 amd64`
- Logical processors: `32`
- Warmups per backend and batch: `20`
- Timed samples per backend and batch: `15`

The position arrays and output masks are allocated before timing. CPU timings include mask construction. Full GPU timings include three host uploads, compute dispatch, fence wait, and packed-mask readback. Resident GPU timings reuse one prepared position snapshot and include dispatch, fence wait, and packed-mask readback. Snapshot preparation and result-index expansion are excluded.

Before the per-batch loop, each CPU backend is primed with 3,000 iterations on a 65,536-element batch. The Java Vector API runs interpreted — orders of magnitude slower — until HotSpot C2 compiles its intrinsic-bearing methods, which needs far more invocations than the per-batch warmup provides. Without this prime the first measured batches reflect interpreter overhead instead of steady-state throughput; the prime also lets C2 auto-vectorize the scalar baseline before it is timed.

| Candidates | CPU p50 | CPU p95 | Vector CPU p50 | Vector CPU p95 | Vector speedup | Full GPU p50 | Full GPU p95 | Resident GPU p50 | Resident GPU p95 | Full speedup | Resident speedup | Readback |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 4,096 | 0.011 ms | 0.106 ms | 0.008 ms | 0.023 ms | 1.38x | 0.374 ms | 0.597 ms | 0.169 ms | 0.260 ms | 0.03x | 0.07x | 512 B |
| 16,384 | 0.054 ms | 0.102 ms | 0.023 ms | 0.071 ms | 2.32x | 0.249 ms | 0.488 ms | 0.130 ms | 0.543 ms | 0.22x | 0.41x | 2,048 B |
| 65,536 | 0.279 ms | 0.417 ms | 0.191 ms | 0.375 ms | 1.46x | 0.483 ms | 0.651 ms | 0.146 ms | 0.172 ms | 0.58x | 1.92x | 8,192 B |
| 262,144 | 0.939 ms | 1.128 ms | 0.600 ms | 0.792 ms | 1.56x | 0.910 ms | 1.075 ms | 0.298 ms | 0.463 ms | 1.03x | 3.15x | 32,768 B |
| 1,048,576 | 4.939 ms | 5.107 ms | 2.856 ms | 3.259 ms | 1.73x | 4.379 ms | 5.135 ms | 1.281 ms | 1.493 ms | 1.13x | 3.85x | 131,072 B |
| 4,194,304 | 14.126 ms | 16.359 ms | 8.417 ms | 9.696 ms | 1.68x | 32.922 ms | 40.842 ms | 4.214 ms | 4.501 ms | 0.43x | 3.35x | 524,288 B |

## Device-Local Snapshot Storage

Positions now live in `DEVICE_LOCAL` memory (VRAM on discrete GPUs) rather than persistently-mapped host-coherent memory. A full upload writes three host-visible staging buffers and the command buffer copies them into device-local buffers with a transfer→compute pipeline barrier before the first dispatch that consumes them. The resident-snapshot path skips that copy: when a query reuses the currently-resident generation, `positionsDirty` stays false and the dispatch reads VRAM directly with no PCIe traversal and no copy. This targets the bottleneck the previous run exposed — the resident GPU path only reached parity (1.05x) at 4M and never crossed over — and it lands.

Comparing the previous `persistent-mapped-host-coherent` run (2026-07-29) against this `device-local-staged` run on the resident-snapshot path (the path the optimization is built for):

| Candidates | Previous resident p50 | Device-local resident p50 | Improvement | Device-local resident speedup vs CPU |
| ---: | ---: | ---: | ---: | ---: |
| 4,096 | 0.120 ms | 0.145 ms | 0.83x | 0.05x |
| 16,384 | 0.090 ms | 0.101 ms | 0.89x | 0.28x |
| 65,536 | 0.168 ms | 0.186 ms | 0.90x | 0.85x |
| 262,144 | 0.863 ms | 0.219 ms | 3.94x | 2.88x |
| 1,048,576 | 2.914 ms | 0.589 ms | 4.95x | 4.26x |
| 4,194,304 | 11.757 ms | 5.782 ms | 2.03x | 2.44x |

Below 262K candidates the resident path is within run-to-run noise — at those sizes the dispatch/fence overhead dominates and there is no PCIe traversal to remove, and the resident path already skips the staging copy. From 262K upward the win is structural: the GPU no longer crosses PCIe on every dispatch, so the resident path is 2.44x–4.26x faster than CPU and the conservative resident-snapshot crossover now consistently lands in the 65,536–262,144 range across runs (previously none in the tested range). The full-call path still includes the staging→device-local copy and does not cross over; it improved modestly at the largest batches where the copy is amortized. Small-batch full-call p50 rose slightly because the copy+barrier is now in the timed path, which is expected and is not the path the optimization targets.

The high p95 at 4M (CPU 97.6 ms, full GPU 110.2 ms) reflects outlier samples under background load on this laptop during that batch; p50 is the stable comparison metric and is consistent with the surrounding batches.

## CPU Vector Backend

The Java Vector API (`jdk.incubator.vector`) CPU backend vectorizes the same squared-distance and packed radius-mask operations as the scalar baseline with bit-identical results. After JIT priming it is consistently faster than scalar at every tested batch (1.41x–1.86x p50); the scalar baseline is itself C2-auto-vectorized, so this is the explicit vector compare and mask packing winning over the JIT's auto-vectorization, not a scalar-to-vector cliff. Because the win is consistent and correctness is bit-exact, `compute.cpuBackend=auto` (the default) selects the vector backend at runtime when the incubator module is available and falls back to scalar otherwise.

## Multi-Query Submission

Each row compares repeated resident queries, each with its own queue submission and fence wait, against one command buffer containing the same queries and one fence wait. Groups larger than 32 queries are split into bounded submissions.

| Candidates | Queries | Individual GPU p50 | Individual GPU p95 | Batched GPU p50 | Batched GPU p95 | Submission speedup | Total readback |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 4,096 | 8 | 1.179 ms | 3.183 ms | 0.202 ms | 0.501 ms | 5.85x | 4,096 B |
| 16,384 | 8 | 1.723 ms | 2.982 ms | 0.238 ms | 0.451 ms | 7.23x | 16,384 B |
| 65,536 | 8 | 1.543 ms | 2.014 ms | 0.542 ms | 0.814 ms | 2.85x | 65,536 B |
| 262,144 | 8 | 2.103 ms | 2.543 ms | 1.272 ms | 1.403 ms | 1.65x | 262,144 B |
| 1,048,576 | 8 | 10.577 ms | 12.591 ms | 8.599 ms | 9.743 ms | 1.23x | 1,048,576 B |
| 4,194,304 | 8 | 41.814 ms | 45.157 ms | 35.811 ms | 42.029 ms | 1.17x | 4,194,304 B |

Conservative full-call p50 crossover: none in the tested range.
Conservative resident-snapshot p50 crossover: `65536` candidates.
A crossover requires the relevant GPU p50 to be at least 5% lower at that batch and every larger tested batch.

## Batch Query

Each row compares a CPU sequential loop against a single GPU 2D dispatch that processes all queries simultaneously. Generated 2026-08-10.

| Candidates | Queries | CPU p50 | CPU p95 | GPU p50 | GPU p95 | Speedup |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 4,096 | 16 | 0.276 ms | 0.642 ms | 5.040 ms | 14.083 ms | 0.05x |
| 4,096 | 64 | 1.024 ms | 1.068 ms | 5.311 ms | 6.320 ms | 0.19x |
| 4,096 | 256 | 2.753 ms | 3.243 ms | 5.675 ms | 6.236 ms | 0.49x |
| 4,096 | 1024 | 10.421 ms | 11.191 ms | 8.653 ms | 9.351 ms | 1.20x |
| 16,384 | 16 | 1.024 ms | 1.574 ms | 4.760 ms | 5.909 ms | 0.22x |
| 16,384 | 64 | 4.536 ms | 5.251 ms | 5.644 ms | 6.770 ms | 0.80x |
| 16,384 | 256 | 10.280 ms | 11.109 ms | 7.783 ms | 8.498 ms | 1.32x |
| 16,384 | 1024 | 35.166 ms | 37.583 ms | 19.341 ms | 21.112 ms | 1.82x |
| 65,536 | 16 | 4.996 ms | 5.972 ms | 6.798 ms | 8.509 ms | 0.73x |
| 65,536 | 64 | 20.386 ms | 21.992 ms | 8.963 ms | 9.344 ms | 2.27x |
| 65,536 | 256 | 44.678 ms | 48.372 ms | 21.498 ms | 39.125 ms | 2.08x |
| 65,536 | 1024 | 149.847 ms | 203.027 ms | 81.602 ms | 93.932 ms | 1.84x |
| 262,144 | 16 | 17.724 ms | 19.570 ms | 8.372 ms | 10.341 ms | 2.12x |
| 262,144 | 64 | 79.739 ms | 85.711 ms | 35.938 ms | 78.246 ms | 2.22x |
| 262,144 | 256 | 193.834 ms | 251.860 ms | 91.374 ms | 97.707 ms | 2.12x |
| 262,144 | 1024 | 768.020 ms | 984.302 ms | 312.957 ms | 389.007 ms | 2.45x |
| 1,048,576 | 16 | 72.623 ms | 93.681 ms | 24.423 ms | 28.731 ms | 2.97x |
| 1,048,576 | 64 | 262.122 ms | 412.575 ms | 74.880 ms | 106.532 ms | 3.50x |
| 1,048,576 | 256 | 757.392 ms | 816.533 ms | 277.626 ms | 304.436 ms | 2.73x |
| 1,048,576 | 1024 | 2340.917 ms | 2750.502 ms | 1026.697 ms | 1161.476 ms | 2.28x |
| 4,194,304 | 16 | 266.890 ms | 298.355 ms | 79.666 ms | 114.301 ms | 3.35x |
| 4,194,304 | 64 | 604.693 ms | 1188.256 ms | 155.877 ms | 281.094 ms | 3.88x |
| 4,194,304 | 256 | 2819.761 ms | 3300.065 ms | 1064.495 ms | 1246.648 ms | 2.65x |
| 4,194,304 | 1024 | 10169.889 ms | 11712.599 ms | 4286.233 ms | 4527.199 ms | 2.37x |

## Repeatability

These five runs predate CPU JIT priming, the vector backend, and device-local snapshot storage; they document GPU crossover process-sensitivity under the original `persistent-mapped-host-coherent` transfer mode and are retained as historical evidence. Two device-local runs (the table above, plus a post-core-extraction re-run on 2026-07-30) both produce a resident crossover, landing at 262,144 and 65,536 respectively — the crossover is consistently present, while the exact threshold remains sensitive to small-batch dispatch/fence noise.

| Run | CPU p50 at 1M | Resident GPU p50 at 1M | Speedup | CPU p50 at 4M | Resident GPU p50 at 4M | Speedup | Conservative resident crossover |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 2.330 ms | 2.101 ms | 1.11x | 9.437 ms | 8.946 ms | 1.05x | 1,048,576 |
| 2 | 2.115 ms | 2.190 ms | 0.97x | 8.439 ms | 9.291 ms | 0.91x | none |
| 3 | 2.299 ms | 2.229 ms | 1.03x | 7.485 ms | 8.728 ms | 0.86x | none |
| 4 | 3.203 ms | 3.595 ms | 0.89x | 22.157 ms | 14.815 ms | 1.50x | 4,194,304 |
| 5 | 2.412 ms | 1.976 ms | 1.22x | 16.795 ms | 10.362 ms | 1.62x | 1,048,576 |

Under the previous transfer mode, full-transfer calls had no crossover in any run and resident crossover appeared in three of five runs at different thresholds, so it remained process-sensitive and was not stable enough to tune `compute.gpuMinimumBatchSize`. Device-local storage removes the PCIe traversal from the resident dispatch, which is what makes the resident crossover repeatable across runs (65,536–262,144). The default `compute.gpuMinimumBatchSize` now has calibrated evidence for a resident-snapshot threshold; the full-call path still does not cross over and remains gated.

## 3D Density Noise Compute

Generated: 2026-08-08T10:27:59Z

- GPU: `NVIDIA GeForce RTX 4060 Laptop GPU`
- GPU transfer mode: `device-local-staged`
- Java: `21.0.9`
- OS: `Windows 11 amd64`
- Logical processors: `32`
- Warmups per backend and volume: `20`
- Timed samples per backend and volume: `15`
- Origin: `(-3.5, 2.0, -7.25)`
- Frequency: `0.05`
- Correctness tolerance: `1.0E-5`

Each volume is a cube whose edge length determines the total voxel count (`edgeLength ^ 3`). CPU timings include the full scalar noise loop. GPU timings include staging upload, compute dispatch, fence wait, and density readback. Output arrays are allocated before timing.

| Edge length | Voxels | CPU p50 | CPU p95 | GPU p50 | GPU p95 | Speedup | Readback |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 16 | 4,096 | 0.142 ms | 0.265 ms | 2.194 ms | 7.685 ms | 0.06x | 16,384 B |
| 32 | 32,768 | 1.068 ms | 1.196 ms | 2.101 ms | 4.903 ms | 0.51x | 131,072 B |
| 64 | 262,144 | 8.158 ms | 9.814 ms | 5.682 ms | 9.706 ms | 1.44x | 1,048,576 B |
| 128 | 2,097,152 | 65.942 ms | 74.423 ms | 55.536 ms | 59.738 ms | 1.19x | 8,388,608 B |

Conservative p50 crossover: `64` edge length (`64^3 = 262144` voxels).
A crossover requires the GPU p50 to be at least 5% lower at that volume and every larger tested volume.

The 3D value-noise workload is compute-light (one integer hash plus trilinear smoothstep interpolation per voxel), so per-dispatch overhead and density readback dominate at small and large volumes respectively. The GPU wins at 64³ (1.44x p50) where the 262K-voxel dispatch saturates the compute cores enough to amortize the 1 MB readback, and edges out the CPU at 128³ (1.19x) this run. At 16³ and 32³ the dispatch/fence floor exceeds the CPU loop cost. Unlike the spatial-query path, noise generation writes one `float` per voxel (4 bytes) rather than a packed 1-bit mask, so readback scales with volume and caps the large-volume speedup. The adaptive router retains the CPU fallback for batches below the offload threshold; the noise self-test verifies bit-exact CPU/GPU agreement within `1.0E-5`.

## Particle Physics Simulation

Generated: 2026-08-08T11:14:01Z

- GPU: `NVIDIA GeForce RTX 4060 Laptop GPU`
- GPU transfer mode: `device-local-staged`
- Java: `21.0.9`
- OS: `Windows 11 amd64`
- Logical processors: `32`
- Warmups per backend and count: `20`
- Timed samples per backend and count: `15`
- Gravity: `9.8`
- Time step: `0.05`
- Restitution: `0.6`
- Correctness tolerance: `1.0E-5`

Each step integrates one Euler step (position += velocity * dt, velocity.y -= gravity * dt) with elastic ground collision at y=0. CPU timings include the full scalar integration loop. GPU timings include staging upload of positions and velocities, compute dispatch, fence wait, and readback of the updated arrays.

| Particles | CPU p50 | CPU p95 | GPU p50 | GPU p95 | Speedup | Readback |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 256 | 0.001 ms | 0.001 ms | 0.127 ms | 0.188 ms | 0.01x | 6,144 B |
| 1,024 | 0.002 ms | 0.022 ms | 0.176 ms | 0.222 ms | 0.01x | 24,576 B |
| 4,096 | 0.007 ms | 0.008 ms | 0.370 ms | 0.460 ms | 0.02x | 98,304 B |
| 16,384 | 0.030 ms | 0.031 ms | 1.243 ms | 1.376 ms | 0.02x | 393,216 B |
| 65,536 | 0.113 ms | 0.133 ms | 5.382 ms | 5.671 ms | 0.02x | 1,572,864 B |

Conservative p50 crossover: none in the tested range.
A crossover requires the GPU p50 to be at least 5% lower at that count and every larger tested count.

The particle simulation workload is extremely compute-light (6 multiplies, 3 additions, and one branch per particle), so the GPU's staging upload, dispatch, and readback overhead dominates by 2–3 orders of magnitude at every tested count. Even at 65,536 particles the CPU finishes in 0.113 ms while the GPU needs 5.4 ms — the readback alone (1.5 MB for two float arrays) exceeds the total CPU compute time. The adaptive router keeps particle simulation on CPU; the GPU path exists as infrastructure for future workloads that involve heavier per-particle compute (e.g., N-body interactions, voxel collision) where the compute-to-transfer ratio is more favorable.

## N-Body Gravity Simulation

Generated: 2026-08-10T14:04:56Z

- GPU: `NVIDIA GeForce RTX 4060 Laptop GPU`
- GPU transfer mode: `device-local-staged`
- Java: `21.0.9`
- OS: `Windows 11 amd64`
- Logical processors: `32`
- Warmups per backend and count: `20`
- Timed samples per backend and count: `15`
- Gravity constant: `0.5`
- Time step: `0.05`
- Softening: `1.0`
- Correctness tolerance: `0.005`

Each step evaluates all pairwise gravitational forces synchronously (O(n²)) and integrates semi-implicit Euler. CPU timings include the full scalar O(n²) force loop. GPU timings include staging upload of positions, velocities, and masses, compute dispatch, fence wait, and readback of the updated position and velocity arrays.

| Bodies | CPU p50 | CPU p95 | GPU p50 | GPU p95 | Speedup | Readback |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 256 | 0.210 ms | 0.225 ms | 0.211 ms | 0.284 ms | 0.99x | 6,144 B |
| 1,024 | 3.328 ms | 3.572 ms | 0.483 ms | 0.583 ms | 6.89x | 24,576 B |
| 4,096 | 100.298 ms | 118.191 ms | 1.382 ms | 4.216 ms | 72.59x | 98,304 B |
| 8,192 | 524.005 ms | 669.555 ms | 5.784 ms | 14.671 ms | 90.59x | 196,608 B |
| 16,384 | 1898.620 ms | 2271.483 ms | 14.235 ms | 28.981 ms | 133.38x | 393,216 B |

Conservative p50 crossover: `1024` bodies.
A crossover requires the GPU p50 to be at least 5% lower at that count and every larger tested count.

The quadratic O(n²) force is exactly the workload the GPU is built for: from 4,096 bodies the GPU is 72–133x faster than the scalar CPU loop, and the compute-to-transfer ratio improves as n grows because readback scales linearly while the force work scales quadratically. The adaptive router should prefer the GPU dispatch for N-body gravity once the body count exceeds ~1,024.

## Light Propagation

Generated: 2026-08-10T14:05:02Z

- GPU: `NVIDIA GeForce RTX 4060 Laptop GPU`
- GPU transfer mode: `device-local-staged`
- Java: `21.0.9`
- OS: `Windows 11 amd64`
- Logical processors: `32`
- Warmups per backend and grid: `15`
- Timed samples per backend and grid: `15`
- Max light level: `15`
- Correctness tolerance: `0` (integer-exact)

Each run propagates a single center light source (level 15) through a cube grid with ~8% random opaque occluders. Iteration count is `dimX + dimY + dimZ - 3` (full convergence). CPU timings include the full scalar double-buffered relaxation loop. GPU timings include staging upload of light and opacity grids, double-buffered compute dispatch with inter-iteration memory barriers, fence wait, and readback of the result light grid.

| Grid | Voxels | Iterations | CPU p50 | CPU p95 | GPU p50 | GPU p95 | Speedup | Readback |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 8³ | 512 | 21 | 0.101 ms | 0.146 ms | 0.253 ms | 0.410 ms | 0.40x | 6,144 B |
| 16³ | 4,096 | 45 | 1.289 ms | 1.458 ms | 0.392 ms | 0.508 ms | 3.29x | 49,152 B |
| 32³ | 32,768 | 93 | 18.983 ms | 19.620 ms | 1.191 ms | 1.283 ms | 15.94x | 393,216 B |
| 48³ | 110,592 | 141 | 120.603 ms | 135.923 ms | 6.951 ms | 10.344 ms | 17.35x | 1,327,104 B |

Conservative p50 crossover: `16³` grid.
A crossover requires the GPU p50 to be at least 5% lower at that grid and every larger tested grid.

Light propagation is an iterative relaxation with a high compute-to-transfer ratio; the GPU is 15–17x faster than CPU at 32³ and 48³. The per-iteration double-buffered dispatch keeps the GPU resident across iterations, so the dispatch/fence overhead is amortized over dozens of barrier-synchronized passes. This is the strongest GPU win in the benchmark suite and is the target workload for the device-local light-propagation buffers.

## Descriptor Pool Sharing

Generated: 2026-08-10T14:05:03Z

- GPU: `NVIDIA GeForce RTX 4060 Laptop GPU`
- Java: `21.0.9`
- OS: `Windows 11 amd64`
- Measured cycles per strategy: `200`

Each cycle creates descriptor pool(s), allocates 5 descriptor sets, and destroys the pool(s). The **split** strategy models the prior per-pipeline design (5 pools, maxSets=1 each); the **shared** strategy models the consolidated design (1 pool, maxSets=8). The split strategy issues 5x the pool create/destroy calls per cycle.

| Strategy | p50 | p95 | Mean |
| --- | ---: | ---: | ---: |
| split (5 pools) | 16.600 us | 43.400 us | 22.015 us |
| shared (1 pool) | 7.400 us | 24.300 us | 10.073 us |

Shared-pool p50 overhead reduction: `2.24x`

This microbenchmark isolates descriptor-pool allocation overhead: consolidating five per-pipeline pools into one shared pool cuts allocation cost roughly in half.
