# FastAudioProcess Roadmap 🗺️

**Vision:** To provide the fastest possible native and SIMD audio processing primitives for the FastJava ecosystem, optimizing where standard Java APIs fail.

## 🟢 v0.1.0: Initial Release (Current)
- [x] **Format Conversions**: MP3 to WAV conversion.
- [x] **Resampling**: Wave resampling to 44100Hz.
- [x] **SIMD RMS Volume Calculation**: High-performance volume level computation accelerated by Java 17 Vector API (SIMD).

## 🟢 v0.1.1: Audio DSP, FX & Waveform Release (Current)
- [x] **Wake-Word Integration Utilities**:
  - [x] **Frame Normalization**: Energy-sensitive amplitude normalization.
  - [x] **Band-limited Pre-Emphasis Filter**: High-pass filtering to boost higher speech frequencies.
  - [x] **Modellfriendly Frame-Chunking Pipelines**: Buffering audio frames tailored to wake-word neural networks.
- [x] **Log-Mel Feature Extraction**: Ultra-fast extraction of MFCCs / Log-Mel spectrograms for local speech models.
- [x] **Audio-Manipulation & FX Library**:
  - [x] Equalizer (IIR/FIR crossover filters)
  - [x] Dynamic Processing (Noise Gate)
  - [x] Pitch-Shifting (Native speed-preserved & resampling-based)
- [x] **Audio I/O & Mixing**:
  - [x] Multi-channel mixing (downmix / upmix)
- [x] **Waveform Visualization & Metering**:
  - [x] In-memory signal downsampling (`generateWaveformPoints`) for timeline plotting.
  - [x] Absolute peak amplitude tracking (`getFramePeak`) for real-time streams.

## 🟡 v0.1.2: Future steps
- [ ] **Whisper & TTS Substrates**:
  - Native integration helper for Whisper (STT) and local TTS engine (Style-TTS/VITS).
- [ ] **VAD Trigger Integration**:
  - Stable Voice Activity Detection (VAD) runtime bindings.
- [ ] **Fast Audio Embedding extraction**:
  - CLAP/Wav2Vec ONNX extraction.
- [ ] **AI Noise Reduction**:
  - RNNoise / Demucs JNI bindings.
- [ ] **CREAM Integration**:
  - Audio event markers on timetables and timeline node snapshots.

---
**Focus:** Performance is our USP. We optimize where Java stops.
