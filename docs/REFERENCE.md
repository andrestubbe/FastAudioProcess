# FastAudioProcess API Reference Manual

`FastAudioProcess` provides high-performance native audio DSP, SIMD-accelerated pitch detection, and SOLA pitch shifting.

---

## 1. Native Audio API

### `detectPitchNative`
```java
public static native float detectPitchNative(float[] samples, int sampleRate)
```
Estimates fundamental frequency (F0) using AVX2 SIMD autocorrelation.

---

### `pitchShiftNative`
```java
public static native void pitchShiftNative(float[] samples, float semitones, int sampleRate)
```
Applies Synchronized Overlap-Add (SOLA) pitch shifting without modifying playback speed.
