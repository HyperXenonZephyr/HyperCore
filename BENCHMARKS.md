# HyperCore Compute Benchmarks

This file records the current local calibration run. It is evidence for backend decisions, not a claim about complete Minecraft tick performance.

Generated: 2026-07-25T12:42:25Z

- GPU: `NVIDIA GeForce RTX 4060 Laptop GPU`
- Java: `21.0.9`
- OS: `Windows 11 amd64`
- Logical processors: `32`
- Warmups per backend and batch: `20`
- Timed samples per backend and batch: `15`

The position arrays and output masks were allocated before timing. CPU timings include scalar mask construction. GPU timings include three host uploads, compute dispatch, fence wait, and packed-mask readback through persistent buffers. Snapshot creation and result-index expansion are excluded.

| Candidates | CPU p50 (ms) | CPU p95 (ms) | GPU p50 (ms) | GPU p95 (ms) | p50 speedup | GPU readback |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 4,096 | 0.037 | 0.111 | 0.197 | 0.273 | 0.19x | 512 B |
| 16,384 | 0.122 | 0.130 | 0.237 | 0.331 | 0.51x | 2,048 B |
| 65,536 | 0.193 | 0.285 | 0.353 | 0.421 | 0.55x | 8,192 B |
| 262,144 | 0.805 | 0.940 | 1.128 | 1.428 | 0.71x | 32,768 B |
| 1,048,576 | 2.887 | 3.184 | 3.873 | 4.035 | 0.75x | 131,072 B |
| 4,194,304 | 9.441 | 11.284 | 14.930 | 15.632 | 0.63x | 524,288 B |

No conservative p50 crossover was observed. A crossover requires GPU p50 to be at least 5% lower at that batch and every larger tested batch. The configured GPU threshold is therefore unchanged. The next optimization target is persistent mapped staging memory and fewer host synchronization points; the threshold should only be reconsidered after that change and a new run.
