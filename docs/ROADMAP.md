# FastAudioProcess Roadmap 🗺️

**Vision:** To provide the fastest possible native and SIMD audio processing primitives for the FastJava ecosystem, optimizing where standard Java APIs fail.

## 🟢 v0.1.0: Initial Release (Current)
- [x] **Format Conversions**: MP3 to WAV conversion.
- [x] **Resampling**: Wave resampling to 44100Hz.
- [x] **SIMD RMS Volume Calculation**: High-performance volume level computation accelerated by Java 17 Vector API (SIMD).

## 🟡 v0.1.1: Next steps (Collected requirements)
- [ ] **Wake-Word Integration Utilities**:
  - **Wake-Word Optimized VAD-Frames**: Voice Activity Detection helper for frame segmentation.
  - **Frame Normalization**: Energy-sensitive amplitude normalization.
  - **Band-limited Pre-Emphasis Filter**: High-pass filtering to boost higher speech frequencies.
  - **Modellfriendly Frame-Chunking Pipelines**: Buffering audio frames tailored to wake-word neural networks.
- [ ] **Log-Mel Feature Extraction**: Ultra-fast extraction of MFCCs / Log-Mel spectrograms for local speech models.
- [ ] **Audio-Manipulation Library**:
  - Equalizer (IIR/FIR filters)
  - Dynamic Processing (Compressor / Limiter / Noise Gate)
  - Reverb / Delay / Pitch-Shifting & Time-Stretching
- [ ] **Audio I/O & Mixing**:
  - Multi-channel mixing (downmix / upmix)
  - Ring-buffering / Zero-Copy audio pipelines
- [ ] **Local AI Models Substrate**:
  - Native integration helper for Whisper (STT) and local TTS engine (Style-TTS/VITS)
  - Fast Audio Embedding extraction (CLAP/Wav2Vec)
  - Native Voice Activity Detection (VAD) (Silero, etc.)
  - AI Noise Reduction (RNNoise / Demucs)
- [ ] **CREAM Integration**:
  - Audio event markers on timetables and timeline node snapshots

---
**Focus:** Performance is our USP. We optimize where Java stops.
