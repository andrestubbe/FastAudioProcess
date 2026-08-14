# FastAudioProcess API Reference Manual

`FastAudioProcess` provides high-performance native audio DSP, SIMD-accelerated pitch detection, and SOLA pitch shifting for real-time Java audio applications.

---

## 1. Native Audio Acceleration API

### `detectPitchNative`
```java
public static native float detectPitchNative(float[] samples, int sampleRate)
```
Estimates the fundamental frequency (F0 pitch in Hz) of an audio buffer using native C++ AVX2 autocorrelation.

#### Parameters:
- **`samples`** (`float[]`): Direct 32-bit floating-point monophonic audio PCM samples.
- **`sampleRate`** (`int`): Audio sample rate in Hz (e.g., `44100` or `48000`).

#### Returns:
- **`float`**: Detected pitch frequency in Hertz (Hz), or `0.0f` if unpitched/silent.

#### Example:
```java
float[] pcm = new float[44100]; // 1 second of 44.1kHz audio
float pitch = FastAudioProcess.detectPitchNative(pcm, 44100);
```

---

### `pitchShiftNative`
```java
public static native void pitchShiftNative(float[] samples, float semitones, int sampleRate)
```
Modulates the pitch of a monophonic audio buffer by the specified semitone offset natively using the Synchronized Overlap-Add (SOLA) algorithm, preserving audio duration and tempo.

#### Parameters:
- **`samples`** (`float[]`): Audio sample buffer modified in-place by the SOLA filter.
- **`semitones`** (`float`): Pitch shift offset in semitones (e.g., `+2.0f` for two semitones up, `-12.0f` for one octave down).
- **`sampleRate`** (`int`): Sampling rate in Hz (e.g., `44100`).

#### Example:
```java
float[] voiceBuffer = getLiveAudioFrame();
FastAudioProcess.pitchShiftNative(voiceBuffer, 3.0f, 44100); // Pitch up +3 semitones
```
