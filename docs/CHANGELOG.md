# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.1.1] - 2026-07-04

### Added
- Native JNI Autocorrelation-based Pitch Detection (`detectPitchNative`)
- Native JNI Overlap-Add Pitch Shifting (`pitchShiftNative`) preserving speed/duration
- Local stateful Silero VAD v5 ONNX model runner (`SileroVAD`)
- Sliding-window frame segmentation chunker (`FrameChunker`)
- Crossover 3-band Equalizer and Noise Gate FX filters
- Log-Mel Spectrogram extraction and pre-emphasis filtering
- PitchDemo and VADDemo under `examples/` with automated root runner batch scripts

## [0.1.0] - 2026-07-04

### Added
- First release containing high-performance audio processing helper methods.
- SIMD Java Vector API accelerated RMS computation
- WAV Resampler to 44.1kHz
- MP3 to WAV conversion utilities
- JNI C++ compile pipeline setup
- Restructured documentation based on the FastJava blueprint
