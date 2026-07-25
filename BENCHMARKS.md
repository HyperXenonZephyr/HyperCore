# HyperCore Compute Benchmarks

This file records the current local calibration runs. They are evidence for backend decisions, not a claim about complete Minecraft tick performance.

Generated: 2026-07-25T13:16:01Z

- GPU: `NVIDIA GeForce RTX 4060 Laptop GPU`
- GPU transfer mode: `persistent-mapped-host-coherent`
- Java: `21.0.9`
- OS: `Windows 11 amd64`
- Logical processors: `32`
- Warmups per backend and batch: `20`
- Timed samples per backend and batch: `15`

The position arrays and output masks were allocated before timing. CPU timings include scalar mask construction. Full GPU timings include three host uploads, compute dispatch, fence wait, and packed-mask readback. Resident GPU timings reuse one prepared position snapshot and include dispatch, fence wait, and packed-mask readback. Snapshot preparation and result-index expansion are excluded.

| Candidates | CPU p50 | CPU p95 | Full GPU p50 | Full GPU p95 | Resident GPU p50 | Resident GPU p95 | Full speedup | Resident speedup | Readback |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 4,096 | 0.035 ms | 0.081 ms | 0.296 ms | 0.860 ms | 0.233 ms | 0.907 ms | 0.12x | 0.15x | 512 B |
| 16,384 | 0.112 ms | 0.123 ms | 0.252 ms | 0.579 ms | 0.191 ms | 0.825 ms | 0.44x | 0.59x | 2,048 B |
| 65,536 | 0.196 ms | 0.296 ms | 0.363 ms | 0.735 ms | 0.296 ms | 1.103 ms | 0.54x | 0.66x | 8,192 B |
| 262,144 | 0.873 ms | 0.979 ms | 1.338 ms | 2.316 ms | 1.010 ms | 1.782 ms | 0.65x | 0.86x | 32,768 B |
| 1,048,576 | 3.203 ms | 3.446 ms | 4.966 ms | 6.260 ms | 3.595 ms | 4.319 ms | 0.64x | 0.89x | 131,072 B |
| 4,194,304 | 22.157 ms | 23.941 ms | 28.492 ms | 35.072 ms | 14.815 ms | 16.385 ms | 0.78x | 1.50x | 524,288 B |

## Repeatability

Four independent JVM runs used the same benchmark shape. Resident snapshots consistently removed a substantial part of the full GPU cost at large batches, but the CPU comparison varied enough that the crossover was not repeatable.

| Run | CPU p50 at 1M | Resident GPU p50 at 1M | Speedup | CPU p50 at 4M | Resident GPU p50 at 4M | Speedup | Conservative resident crossover |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 2.330 ms | 2.101 ms | 1.11x | 9.437 ms | 8.946 ms | 1.05x | 1,048,576 |
| 2 | 2.115 ms | 2.190 ms | 0.97x | 8.439 ms | 9.291 ms | 0.91x | none |
| 3 | 2.299 ms | 2.229 ms | 1.03x | 7.485 ms | 8.728 ms | 0.86x | none |
| 4 | 3.203 ms | 3.595 ms | 0.89x | 22.157 ms | 14.815 ms | 1.50x | 4,194,304 |

A conservative crossover requires the relevant GPU p50 to be at least 5% lower at that batch and every larger tested batch. Full-transfer calls had no crossover in any run. Resident crossover appeared in two of four runs at different thresholds, so it is not stable enough to tune `compute.gpuMinimumBatchSize`; the default remains unchanged. The next GPU optimization target is device-local snapshot storage and batching multiple queries per submission to reduce fence cost.
