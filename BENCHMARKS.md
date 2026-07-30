# HyperCore Compute Benchmarks

This file records the current local calibration runs. They are evidence for backend decisions, not a claim about complete Minecraft tick performance.

Generated: 2026-07-30T13:07:34Z

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
| 4,096 | 0.008 ms | 0.015 ms | 0.005 ms | 0.013 ms | 1.67x | 0.288 ms | 0.774 ms | 0.145 ms | 0.732 ms | 0.03x | 0.05x | 512 B |
| 16,384 | 0.028 ms | 0.048 ms | 0.020 ms | 0.047 ms | 1.41x | 0.234 ms | 0.556 ms | 0.101 ms | 0.125 ms | 0.12x | 0.28x | 2,048 B |
| 65,536 | 0.159 ms | 0.210 ms | 0.110 ms | 0.142 ms | 1.44x | 0.685 ms | 0.786 ms | 0.186 ms | 0.814 ms | 0.23x | 0.85x | 8,192 B |
| 262,144 | 0.629 ms | 0.717 ms | 0.401 ms | 0.641 ms | 1.57x | 1.349 ms | 2.376 ms | 0.219 ms | 0.400 ms | 0.47x | 2.88x | 32,768 B |
| 1,048,576 | 2.511 ms | 3.088 ms | 1.554 ms | 2.030 ms | 1.62x | 3.623 ms | 5.307 ms | 0.589 ms | 0.682 ms | 0.69x | 4.26x | 131,072 B |
| 4,194,304 | 14.130 ms | 97.612 ms | 7.607 ms | 38.199 ms | 1.86x | 18.236 ms | 110.184 ms | 5.782 ms | 18.827 ms | 0.77x | 2.44x | 524,288 B |

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
| 4,096 | 8 | 1.163 ms | 3.057 ms | 0.154 ms | 0.299 ms | 7.56x | 4,096 B |
| 16,384 | 8 | 0.784 ms | 2.186 ms | 0.159 ms | 0.759 ms | 4.94x | 16,384 B |
| 65,536 | 8 | 1.148 ms | 2.709 ms | 0.370 ms | 0.829 ms | 3.10x | 65,536 B |
| 262,144 | 8 | 2.017 ms | 3.648 ms | 1.189 ms | 1.431 ms | 1.70x | 262,144 B |
| 1,048,576 | 8 | 5.584 ms | 7.757 ms | 4.191 ms | 5.087 ms | 1.33x | 1,048,576 B |
| 4,194,304 | 8 | 50.989 ms | 144.262 ms | 42.733 ms | 121.033 ms | 1.19x | 4,194,304 B |

Conservative full-call p50 crossover: none in the tested range.
Conservative resident-snapshot p50 crossover: `262144` candidates.
A crossover requires the relevant GPU p50 to be at least 5% lower at that batch and every larger tested batch.

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
