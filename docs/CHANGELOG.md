# FastAudioProcess Changelog

## [0.1.4] - 2026-08-30
- **True AVX2 SIMD Pitch Tracking**: 256-bit `_mm256_fmadd_ps` Fused-Multiply-Add autocorrelation with DC-Mean removal.
- **Native & Java FastFFT**: O(N log N) in-place Cooley-Tukey Radix-2 FFT and IFFT with precomputed static twiddle factor tables and `std::call_once` thread-safe initialization.
- **Modular Architecture Separation**: Split into clean modular classes: `FastAudioProcess` (Facade), `FastAudioAcoustics`, `FastAudioDenoise`, `FastAudioEqualizer`, `FastAudioCodec`, `FastAudioChunker`, and `FastFFT`.
- **Zero-Allocation Hot-Paths**: Thread-local scratch buffers eliminate runtime Garbage Collection in streaming audio loops.
- **Lock-Free SPSC Chunker**: `FastAudioChunker` with atomic indices and power-of-two bitmask indexing replaces monitor locks.
- **Stateful 3-Band Equalizer**: `FastAudioEqualizer` preserves continuous IIR crossover filter states across chunk boundaries.
- **High-Accuracy Triangular Mel-Filterbank**: Standard triangular frequency integration for AI/VAD acoustic feature extraction.
- **Zero-Padding Tail Protection**: `FastAudioDenoise.suppressNoise` handles arbitrary non-power-of-two frame sizes without sample truncation.
- **Expanded JMH DSP Matrix**: Documented micro-benchmarks for FFT, Denoise, Equalizer, Crest Factor, ZCR, and Pitch Detection.

## [0.1.3] - 2026-08-30
- Added acoustic feature extraction methods: `computeCrestFactor`, `computeZeroCrossingRate`, `computeAutocorrelationPeriodicity`.
- Switched native library loading to 100% pure `FastCore.loadLibrary("fastaudioprocess", FastAudioProcess.class)`.
- Structured `FastAudioProcess.java` into clear logical sections with exhaustive Javadocs.
- Removed incubator vector module dependencies in favor of native SIMD and pure DSP algorithms.

## [0.1.1] - 2026-08-14
- Integrated native `FastSIMD` (v0.1.3) AVX2 vector audio processing engine.
- Added official JMH benchmark measuring 24,118 pitch detection ops/sec.
- Updated 3-section installation guide for Maven, Gradle, and Direct Download.

## [0.1.0] - 2026-05-18
- Initial release of FastAudioProcess with pitch detection and time-domain pitch modulation.