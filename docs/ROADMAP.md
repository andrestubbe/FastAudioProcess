# FastAudioProcess Roadmap

## Milestone 0.1.x — Alpha Core (Current)
- [x] AVX2 SIMD autocorrelation pitch detection with DC removal.
- [x] Native & Java O(N log N) FastFFT / IFFT with static twiddle tables.
- [x] Lock-Free SPSC FastAudioChunker ring buffer.
- [x] Stateful FastAudioEqualizer preserving IIR filter states.
- [x] Standard triangular Mel-filterbank synthesis.
- [x] Zero-padding tail protection in spectral noise suppression.

## Milestone 0.2.x — Multi-Platform & Vector Extensions
- [ ] ARM64 NEON SIMD vectorization for Linux and Apple Silicon.
- [ ] Panama Vector API hardware fallback when native DLL is omitted.
- [ ] Psychoacoustic Bark & ERB scale filterbanks.
- [ ] Overlap-Add (OLA) streaming convolution filter engine.

## Milestone 0.3.x — GPU Compute & Advanced Neural Frontends
- [ ] Vulkan GLSL Compute Shader FFT via FastGPU.
- [ ] Polyphase sinc resampler for audiophile-grade sample rate conversion.
- [ ] Multi-channel beamforming spatial audio filter.