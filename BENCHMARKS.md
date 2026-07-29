# HyperCore Compute Benchmarks

This file records the current local calibration runs. They are evidence for backend decisions, not a claim about complete Minecraft tick performance.

Generated: 2026-07-29T14:13:35Z

- GPU: `NVIDIA GeForce RTX 4060 Laptop GPU`
- GPU transfer mode: `persistent-mapped-host-coherent`
- Java: `21.0.9`
- OS: `Windows 11 amd64`
- Logical processors: `32`
- Warmups per backend and batch: `20`
- Timed samples per backend and batch: `15`

The position arrays and output masks are allocated before timing. CPU timings include mask construction. Full GPU timings include three host uploads, compute dispatch, fence wait, and packed-mask readback. Resident GPU timings reuse one prepared position snapshot and include dispatch, fence wait, and packed-mask readback. Snapshot preparation and result-index expansion are excluded.

Before the per-batch loop, each CPU backend is primed with 3,000 iterations on a 65,536-element batch. The Java Vector API runs interpreted — orders of magnitude slower — until HotSpot C2 compiles its intrinsic-bearing methods, which needs far more invocations than the per-batch warmup provides. Without this prime the first measured batches reflect interpreter overhead instead of steady-state throughput; the prime also lets C2 auto-vectorize the scalar baseline before it is timed.

| Candidates | CPU p50 | CPU p95 | Vector CPU p50 | Vector CPU p95 | Vector speedup | Full GPU p50 | Full GPU p95 | Resident GPU p50 | Resident GPU p95 | Full speedup | Resident speedup | Readback |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 4,096 | 0.005 ms | 0.013 ms | 0.004 ms | 0.013 ms | 1.24x | 0.155 ms | 0.201 ms | 0.120 ms | 0.382 ms | 0.03x | 0.04x | 512 B |
| 16,384 | 0.029 ms | 0.048 ms | 0.014 ms | 0.035 ms | 2.04x | 0.143 ms | 0.265 ms | 0.090 ms | 0.127 ms | 0.20x | 0.32x | 2,048 B |
| 65,536 | 0.127 ms | 0.176 ms | 0.092 ms | 0.128 ms | 1.38x | 0.247 ms | 0.365 ms | 0.168 ms | 0.190 ms | 0.51x | 0.75x | 8,192 B |
| 262,144 | 0.498 ms | 0.552 ms | 0.330 ms | 0.394 ms | 1.51x | 0.906 ms | 1.068 ms | 0.863 ms | 1.142 ms | 0.55x | 0.58x | 32,768 B |
| 1,048,576 | 1.932 ms | 2.071 ms | 1.146 ms | 1.246 ms | 1.69x | 3.434 ms | 4.067 ms | 2.914 ms | 3.472 ms | 0.56x | 0.66x | 131,072 B |
| 4,194,304 | 12.359 ms | 16.992 ms | 7.366 ms | 8.280 ms | 1.68x | 19.938 ms | 21.936 ms | 11.757 ms | 13.207 ms | 0.62x | 1.05x | 524,288 B |

## CPU Vector Backend

The Java Vector API (`jdk.incubator.vector`) CPU backend vectorizes the same squared-distance and packed radius-mask operations as the scalar baseline with bit-identical results. After JIT priming it is consistently faster than scalar at every tested batch (1.24x–2.04x p50); the scalar baseline is itself C2-auto-vectorized, so this is the explicit vector compare and mask packing winning over the JIT's auto-vectorization, not a scalar-to-vector cliff. Because the win is consistent and correctness is bit-exact, `compute.cpuBackend=auto` (the default) selects the vector backend at runtime when the incubator module is available and falls back to scalar otherwise. The GPU is still not faster than either CPU backend for a single radius mask at any tested batch, so Vulkan offload remains gated behind `compute.gpuMinimumBatchSize` and the resident-snapshot path.

## Multi-Query Submission

Each row compares repeated resident queries, each with its own queue submission and fence wait, against one command buffer containing the same queries and one fence wait. Groups larger than 32 queries are split into bounded submissions.

| Candidates | Queries | Individual GPU p50 | Individual GPU p95 | Batched GPU p50 | Batched GPU p95 | Submission speedup | Total readback |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 4,096 | 8 | 0.807 ms | 1.172 ms | 0.146 ms | 0.188 ms | 5.55x | 4,096 B |
| 16,384 | 8 | 0.787 ms | 1.096 ms | 0.152 ms | 0.205 ms | 5.18x | 16,384 B |
| 65,536 | 8 | 1.414 ms | 1.748 ms | 0.367 ms | 0.438 ms | 3.85x | 65,536 B |
| 262,144 | 8 | 6.822 ms | 7.284 ms | 1.407 ms | 2.229 ms | 4.85x | 262,144 B |
| 1,048,576 | 8 | 27.898 ms | 30.858 ms | 9.163 ms | 11.740 ms | 3.04x | 1,048,576 B |
| 4,194,304 | 8 | 93.940 ms | 111.660 ms | 97.088 ms | 107.362 ms | 0.97x | 4,194,304 B |

Conservative full-call p50 crossover: none in the tested range.
Conservative resident-snapshot p50 crossover: none in the tested range.
A crossover requires the relevant GPU p50 to be at least 5% lower at that batch and every larger tested batch.

## Repeatability

These five runs predate CPU JIT priming and the vector backend; they document GPU crossover process-sensitivity only and are retained as historical evidence. The current (primed) run above also shows no conservative crossover.

| Run | CPU p50 at 1M | Resident GPU p50 at 1M | Speedup | CPU p50 at 4M | Resident GPU p50 at 4M | Speedup | Conservative resident crossover |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 2.330 ms | 2.101 ms | 1.11x | 9.437 ms | 8.946 ms | 1.05x | 1,048,576 |
| 2 | 2.115 ms | 2.190 ms | 0.97x | 8.439 ms | 9.291 ms | 0.91x | none |
| 3 | 2.299 ms | 2.229 ms | 1.03x | 7.485 ms | 8.728 ms | 0.86x | none |
| 4 | 3.203 ms | 3.595 ms | 0.89x | 22.157 ms | 14.815 ms | 1.50x | 4,194,304 |
| 5 | 2.412 ms | 1.976 ms | 1.22x | 16.795 ms | 10.362 ms | 1.62x | 1,048,576 |

A conservative crossover requires the relevant GPU p50 to be at least 5% lower at that batch and every larger tested batch. Full-transfer calls had no crossover in any run. Resident crossover appeared in three of five runs at different thresholds, so it remains process-sensitive and is not stable enough to tune `compute.gpuMinimumBatchSize`; the default remains unchanged. Multi-query batching reduces submission overhead for the smaller measured batches, but the 4M case was compute-dominated and slightly slower. The next GPU optimization target is device-local snapshot storage and repeatable crossover measurement.
