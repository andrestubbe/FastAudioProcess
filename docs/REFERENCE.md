# FastAudioProcess Reference Specification

---

**Detailed JNI contracts, fallbacks, and execution rules for FastAudioProcess.**

---

## 1. CPU & SIMD Feature Model

*   **⚡ JDK Vector API (SIMD)** — Used for high-performance JVM-level vectorization (e.g., RMS calculation, normalize, mixChannels).
*   **🚀 AVX2 / SSE4.2** — detected via CPUID for native routines. Enables 32-byte and 16-byte vector ops.
*   **🔄 Fallback rule**: JDK Vector API / Native AVX2 → SSE4.2 → scalar.

---

## 2. Guarantees

*   **🚫 Zero-Copy** — All operations use direct memory pinning (`GetPrimitiveArrayCritical`) or direct buffers for zero copy between Java and native layers.
*   **📏 Unaligned Access** — Safe on all byte boundaries.
*   **🔒 Thread-Safety** — All static native and Java processing methods are thread-safe.

---

## 3. JNI & Memory Contracts

*   **⚡ Direct Memory Pinning** — No implicit copies are made by the JNI bridge.
*   **📦 No Allocation** — All operations work on pre-allocated Java arrays or buffers.
*   **⏰ Critical Sections** — Native calls minimize blocking to prevent GC impact.

---

## 4. API Reference Details

### 4.1 Native JNI Methods
*   `detectPitchNative(float[] samples, int sampleRate)`: Uses JNI autocorrelation mapping to estimate the fundamental frequency. Returns float pitch in Hz, or `0.0f` if unvoiced.
*   `pitchShiftNative(float[] samples, float semitones, int sampleRate)`: Modifies pitch in-place using a zero-copy time-domain Overlap-Add algorithm in native C++, preserving duration.

### 4.2 Local AI VAD (ONNX)
*   `SileroVAD(String modelPath)`: Stateful recurrent LSTM wrapper.
*   `detectSpeech(float[] samples, int sampleRate)`: Accepts exactly 512 samples (16kHz), internally updates recurrent state `[2, 1, 128]`, and outputs speech probability `[0.0, 1.0]`.

### 4.3 Java DSP & Chunker
*   `logMelSpectrogram(float[] samples, ...)`: Computes linear FFT bins and maps them onto log Mel-scale spectrogram bands.
*   `FrameChunker`: Overlapping sliding-window chunker for stream block segmentation.

---

## 5. Platform Support

| Platform | Status |
|----------|--------|
| Windows 10/11 (x64) | ✅ Fully Supported |

---

**Part of the FastJava Ecosystem** — *Making the JVM faster.*

Made with ⚡ by Andre Stubbe
