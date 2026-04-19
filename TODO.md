# TODO

## Architecture
- **Complete DSP Double Precision Upgrade**: Systematically audit the remaining legacy `chuck-core` UGens (e.g., `BiQuad`, `SineWave`, `Phasor`, STK ports) and upgrade their internal recursive state variables from 32-bit `float` to 64-bit `double`. This will eliminate edge-case limit cycles, quantization noise at extreme low frequencies, and denormal CPU spikes across the entire engine while preserving SIMD vectorization on the I/O buffers.
