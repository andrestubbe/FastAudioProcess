# FastAudioProcess Changelog

## [0.1.6] - 2026-10-19
- **True WSOLA Time-Stretching (`timeStretchWSOLA`)**:
  - Implementation of Waveform Similarity Overlap-Add (WSOLA) tuned specifically for speech fundamental frequencies (80 Hz – 400 Hz).
  - Employs a 25ms Hann synthesis window with normalized cross-correlation search across nominal analysis positions.
  - Modifies playback speed (`speedRatio > 1.0` = faster, `speedRatio < 1.0` = slower) while keeping pitch/frequency 100% constant.
  - Eliminates "chipmunk / Mickey-Mouse" and "monster voice" artifacts of linear resampling.
  - Normalizes overlap weights at the output to preserve exact signal amplitude across all frames.
- **Clean Echo-Free Pitch-Shifting (`pitchShift`)**:
  - Solves metallic flanging and circular echo artifacts present in fixed-size native delay-line pitch shifters.
  - Pairs WSOLA time-stretching with phase-aligned linear resampling:
    1. Audio duration is pre-stretched by `(1.0 / pitchFactor)` using WSOLA.
    2. The signal is resampled back to the original sample duration.
  - Frequencies shift smoothly up or down (in musical semitones, e.g. `-12.0` to `+12.0`) while audio duration and transients remain intact.
- **VLC-Style Volume Boost & Soft-Knee Limiter (`applyVolumeGain`)**:
  - Safely amplifies 16-bit signed PCM audio beyond standard 100% ceiling (up to 300% / `volumeMultiplier = 3.0`), mimicking VLC's volume boost.
  - Incorporates an analog-style soft-knee saturation limiter:
    - Signals below 85% full scale (`28,000` on 16-bit) receive clean linear gain.
    - Signals above 85% are smoothed using a continuous Hyperbolic Tangent (`tanh`) compression curve up to the ceiling (`32,767`).
  - Completely prevents harsh digital square-wave clipping, distortion, and speaker popping.

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