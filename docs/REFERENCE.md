# FastAudioProcess API Reference Manual

`FastAudioProcess` provides high-performance native audio DSP, AVX2-accelerated pitch detection, zero-allocation FastFFT spectral filters, and acoustic feature extraction for real-time Java audio applications.

---

## Table of Contents

- [1. Core Facade API (`FastAudioProcess`)](#1-core-facade-api-fastaudioprocess)
- [2. Fast Fourier Transform (`FastFFT`)](#2-fast-fourier-transform-fastfft)
- [3. Real-Time Denoising (`FastAudioDenoise`)](#3-real-time-denoising-fastaudiodenoise)
- [4. Acoustic Feature Extraction (`FastAudioAcoustics`)](#4-acoustic-feature-extraction-fastaudioacoustics)
- [5. Stateful Streaming Equalizer (`FastAudioEqualizer`)](#5-stateful-streaming-equalizer-fastaudioequalizer)
- [6. Lock-Free Streaming Chunker (`FastAudioChunker`)](#6-lock-free-streaming-chunker-fastaudiochunker)
- [7. Codec & Format Conversion (`FastAudioCodec`)](#7-codec--format-conversion-fastaudiocodec)

---

## 1. Core Facade API (`FastAudioProcess`)

### `detectPitchNative`
```java
public static native float detectPitchNative(float[] samples, int sampleRate)
```
Estimates fundamental frequency (F0 pitch in Hz) using 256-bit AVX2 SIMD autocorrelation with DC-Mean removal.

### `pitchShiftNative`
```java
public static native void pitchShiftNative(float[] samples, float semitones, int sampleRate)
```
Modulates the pitch of audio samples in-place using zero-allocation time-domain modulation with persistent 16,384-sample delay lines.

---

## 2. Fast Fourier Transform (`FastFFT`)

### `fft` / `ifft`
```java
public static void fft(float[] real, float[] imag)
public static void ifft(float[] real, float[] imag)
```
In-place Radix-2 Cooley-Tukey FFT and IFFT transforms using precalculated static twiddle factor tables and native AVX2 SIMD kernels.

---

## 3. Real-Time Denoising (`FastAudioDenoise`)

### `suppressNoise`
```java
public static void suppressNoise(float[] samples, int sampleRate, float reductionFactor, float spectralFloor)
```
Real-time spectral power subtraction with Wiener floor gain smoothing and zero-padding tail protection.

### `applyNoiseGate`
```java
public static void applyNoiseGate(float[] samples, float thresholdDb, float reductionDb)
```
Dynamic downward expander attenuating signals below the specified decibel threshold.

---

## 4. Acoustic Feature Extraction (`FastAudioAcoustics`)

### `computeCrestFactor`
```java
public static float computeCrestFactor(float[] samples)
```
Calculates Spectral Crest Factor (Peak / RMS) to detect sharp transient spikes (clicks, cutlery, claps).

### `computeZeroCrossingRate`
```java
public static float computeZeroCrossingRate(float[] samples)
```
Measures sign-change rate per sample to differentiate unvoiced consonants from voiced speech.

### `computeAutocorrelationPeriodicity`
```java
public static float computeAutocorrelationPeriodicity(float[] samples, int minLag, int maxLag)
```
Measures normalized harmonic pitch periodicity ratio in `[0.0, 1.0]`.

### `logMelSpectrogram`
```java
public static float[][] logMelSpectrogram(float[] samples, int sampleRate, int fftSize, int hopSize, int melBins)
```
Generates standard triangular Mel-filterbank spectrogram matrix `[numFrames][melBins]`.

---

## 5. Stateful Streaming Equalizer (`FastAudioEqualizer`)

### `setGains` / `process`
```java
public void setGains(float bassGainDb, float midGainDb, float trebleGainDb)
public void process(float[] samples)
```
Preserves continuous low-pass and high-pass IIR crossover filter states across consecutive streaming audio blocks.

---

## 6. Lock-Free Streaming Chunker (`FastAudioChunker`)

### `push` / `nextChunk`
```java
public void push(float[] samples)
public boolean nextChunk(float[] destination)
```
Single-Producer Single-Consumer (SPSC) lock-free ring buffer with power-of-two bitmask indexing for zero-allocation window extraction.

---

## 7. Codec & Format Conversion (`FastAudioCodec`)

### `mp3ToWav` / `resampleWavTo44100` / `generateWaveformPoints`
```java
public static File mp3ToWav(File mp3File) throws Exception
public static byte[] resampleWavTo44100(byte[] wavData) throws Exception
public static float[] generateWaveformPoints(float[] samples, int targetPoints)
```
Decodes MP3 to WAV PCM, resamples audio byte data, and downsamples waveforms for visualization timelines.