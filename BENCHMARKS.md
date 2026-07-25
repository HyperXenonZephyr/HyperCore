# HyperCore Compute Benchmarks

This file records the current local calibration runs. They are evidence for backend decisions, not a claim about complete Minecraft tick performance.

Generated: 2026-07-25T13:34:24Z

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
| 4,096 | 0.018 ms | 0.040 ms | 0.136 ms | 0.409 ms | 0.106 ms | 0.159 ms | 0.13x | 0.17x | 512 B |
| 16,384 | 0.028 ms | 0.053 ms | 0.144 ms | 0.444 ms | 0.089 ms | 0.175 ms | 0.20x | 0.32x | 2,048 B |
| 65,536 | 0.132 ms | 0.170 ms | 0.216 ms | 0.438 ms | 0.182 ms | 0.405 ms | 0.61x | 0.73x | 8,192 B |
| 262,144 | 0.566 ms | 0.608 ms | 0.741 ms | 1.046 ms | 0.547 ms | 0.811 ms | 0.76x | 1.03x | 32,768 B |
| 1,048,576 | 2.412 ms | 2.648 ms | 2.555 ms | 2.880 ms | 1.976 ms | 2.402 ms | 0.94x | 1.22x | 131,072 B |
| 4,194,304 | 16.795 ms | 19.380 ms | 20.842 ms | 25.227 ms | 10.362 ms | 12.103 ms | 0.81x | 1.62x | 524,288 B |

## Multi-Query Submission

Each row compares repeated resident queries, each with its own queue submission and fence wait, against one command buffer containing the same queries and one fence wait. Groups larger than 32 queries are split into bounded submissions.

| Candidates | Queries | Individual GPU p50 | Individual GPU p95 | Batched GPU p50 | Batched GPU p95 | Submission speedup | Total readback |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 4,096 | 8 | 0.871 ms | 1.213 ms | 0.151 ms | 0.472 ms | 5.76x | 4,096 B |
| 16,384 | 8 | 0.929 ms | 1.473 ms | 0.157 ms | 0.391 ms | 5.91x | 16,384 B |
| 65,536 | 8 | 1.515 ms | 2.264 ms | 0.417 ms | 0.504 ms | 3.63x | 65,536 B |
| 262,144 | 8 | 4.892 ms | 6.282 ms | 1.423 ms | 1.758 ms | 3.44x | 262,144 B |
| 1,048,576 | 8 | 16.950 ms | 17.702 ms | 6.631 ms | 10.723 ms | 2.56x | 1,048,576 B |
| 4,194,304 | 8 | 94.969 ms | 113.532 ms | 99.981 ms | 111.653 ms | 0.95x | 4,194,304 B |

## Repeatability

Five independent JVM runs used the same benchmark shape. Resident snapshots consistently removed a substantial part of the full GPU cost at large batches, but the CPU comparison varied enough that the crossover was not repeatable.

| Run | CPU p50 at 1M | Resident GPU p50 at 1M | Speedup | CPU p50 at 4M | Resident GPU p50 at 4M | Speedup | Conservative resident crossover |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 2.330 ms | 2.101 ms | 1.11x | 9.437 ms | 8.946 ms | 1.05x | 1,048,576 |
| 2 | 2.115 ms | 2.190 ms | 0.97x | 8.439 ms | 9.291 ms | 0.91x | none |
| 3 | 2.299 ms | 2.229 ms | 1.03x | 7.485 ms | 8.728 ms | 0.86x | none |
| 4 | 3.203 ms | 3.595 ms | 0.89x | 22.157 ms | 14.815 ms | 1.50x | 4,194,304 |
| 5 | 2.412 ms | 1.976 ms | 1.22x | 16.795 ms | 10.362 ms | 1.62x | 1,048,576 |

A conservative crossover requires the relevant GPU p50 to be at least 5% lower at that batch and every larger tested batch. Full-transfer calls had no crossover in any run. Resident crossover appeared in three of five runs at different thresholds, so it remains process-sensitive and is not stable enough to tune `compute.gpuMinimumBatchSize`; the default remains unchanged. Multi-query batching reduces submission overhead for the smaller measured batches, but the 4M case was compute-dominated and slightly slower. The next GPU optimization target is device-local snapshot storage and repeatable crossover measurement.
