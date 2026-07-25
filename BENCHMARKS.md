# HyperCore Compute Benchmarks

This file records the current local calibration run. It is evidence for backend decisions, not a claim about complete Minecraft tick performance.

Generated: 2026-07-25T12:50:26Z

- GPU: `NVIDIA GeForce RTX 4060 Laptop GPU`
- GPU transfer mode: `persistent-mapped-host-coherent`
- Java: `21.0.9`
- OS: `Windows 11 amd64`
- Logical processors: `32`
- Warmups per backend and batch: `20`
- Timed samples per backend and batch: `15`

The position arrays and output masks were allocated before timing. CPU timings include scalar mask construction. GPU timings include three host uploads, compute dispatch, fence wait, and packed-mask readback through persistent mapped host-coherent buffers. Snapshot creation and result-index expansion are excluded.

| Candidates | CPU p50 (ms) | CPU p95 (ms) | GPU p50 (ms) | GPU p95 (ms) | p50 speedup | GPU readback |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 4,096 | 0.028 | 0.057 | 0.222 | 0.600 | 0.13x | 512 B |
| 16,384 | 0.098 | 0.108 | 0.225 | 0.544 | 0.44x | 2,048 B |
| 65,536 | 0.183 | 0.248 | 0.338 | 0.473 | 0.54x | 8,192 B |
| 262,144 | 0.724 | 0.970 | 1.038 | 2.023 | 0.70x | 32,768 B |
| 1,048,576 | 2.745 | 3.197 | 3.275 | 4.043 | 0.84x | 131,072 B |
| 4,194,304 | 8.916 | 9.671 | 12.162 | 14.064 | 0.73x | 524,288 B |

## Directional Comparison

The previous run used the same benchmark shape before persistent mapping. The comparison below is directional rather than a controlled performance claim because it is a separate process run and CPU/GPU clocks are not pinned.

| Candidates | Previous GPU p50 (ms) | Persistent-mapped GPU p50 (ms) | Change |
| ---: | ---: | ---: | ---: |
| 4,096 | 0.197 | 0.222 | +12.7% |
| 16,384 | 0.237 | 0.225 | -5.1% |
| 65,536 | 0.353 | 0.338 | -4.2% |
| 262,144 | 1.128 | 1.038 | -8.0% |
| 1,048,576 | 3.873 | 3.275 | -15.4% |
| 4,194,304 | 14.930 | 12.162 | -18.5% |

No conservative p50 crossover was observed. A crossover requires GPU p50 to be at least 5% lower at that batch and every larger tested batch. The configured GPU threshold is therefore unchanged. The next optimization target is resident snapshot reuse across multiple queries; the threshold should only be reconsidered after that change and a new run.
