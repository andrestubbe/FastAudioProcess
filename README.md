# FastAudioProcess 0.1.4 [ALPHA-2026-08] — High-Performance Audio Processing for Java

[![Status](https://img.shields.io/badge/status-0.1.4-brightgreen.svg)](https://github.com/andrestubbe/FastAudioProcess/releases/tag/0.1.4)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://www.java.com)
[![Platform](https://img.shields.io/badge/Platform-Windows%2010+-lightgrey.svg)]()
[![JitPack](https://img.shields.io/badge/JitPack-0.1.4-green.svg)](https://jitpack.io/#andrestubbe/FastAudioProcess)

---

**⚡ Hardware SIMD-accelerated real-time audio pitch detection, SOLA pitch shifting, real-time noise suppression, acoustic feature extraction (Crest Factor, ZCR, Autocorrelation), and zero-allocation DSP filters.**

`FastAudioProcess` provides native C++ AVX2 vector processing and DSP algorithms for Java audio applications, enabling high-throughput noise suppression, pitch tracking, acoustic classification, and format conversions without Garbage Collection stalls.

---

## Quick Start — Example

```java
import fastaudioprocess.FastAudioProcess;
import fastaudioprocess.FastAudioEqualizer;
import fastaudioprocess.FastAudioChunker;
import fastaudioprocess.FastFFT;

public class Demo {
    public static void main(String[] args) {
        // 1. Generate 1 second 440Hz sine wave buffer with background noise
        float[] audio = new float[44100];
        for (int i = 0; i < audio.length; i++) {
            audio[i] = (float) Math.sin(2.0 * Math.PI * 440.0 * i / 44100.0);
        }

        // 2. Real-time Spectral Subtraction Noise Suppression (FastFFT)
        FastAudioProcess.suppressNoise(audio, 44100, 1.0f, 0.02f);

        // 3. Acoustic Feature Analysis (Crest Factor & Periodicity)
        float crest = FastAudioProcess.computeCrestFactor(audio);
        float zcr = FastAudioProcess.computeZeroCrossingRate(audio);
        float periodicity = FastAudioProcess.computeAutocorrelationPeriodicity(audio, 35, 160);

        // 4. AVX2 SIMD Pitch Detection with DC Removal
        float pitch = FastAudioProcess.detectPitchNative(audio, 44100);
        System.out.printf("Pitch: %.2f Hz | Crest: %.2f | ZCR: %.3f | Periodicity: %.2f%n", pitch, crest, zcr, periodicity);

        // 5. Stateful Streaming 3-Band Equalizer (Bass +3dB, Treble +2dB)
        FastAudioEqualizer eq = new FastAudioEqualizer();
        eq.setGains(3.0f, 0.0f, 2.0f);
        eq.process(audio);

        // 6. Lock-Free SPSC Frame Chunking (512 samples window, 160 hop)
        FastAudioChunker chunker = new FastAudioChunker(512, 160);
        chunker.push(audio);
        float[] window = new float[512];
        while (chunker.nextChunk(window)) {
            // Ingest into VAD / STT model without allocations...
        }
    }
}
```

---

## Table of Contents

- [Why FastAudioProcess?](#why-fastaudioprocess)
- [Key Features](#key-features)
- [Real-World Use Cases](#real-world-use-cases)
- [Performance Benchmarks](#performance-benchmarks)
- [Architecture Overview](#architecture-overview)
- [API Quick Reference](#api-quick-reference)
- [Installation](#installation)
- [Documentation](#documentation)
- [Platform Support](#platform-support)
- [License](#license)
- [Related Projects](#related-projects)

---

## Why FastAudioProcess?

Standard Java audio loops suffer from float boxing overhead, slow software resamplers, and JVM GC pauses that cause real-time audio crackles. FastAudioProcess solves this by:

- **AVX2 SIMD Vector Acceleration** — Uses 256-bit SIMD registers to process multiple float audio channels in parallel.
- **Spectral Power Noise Suppression** — Real-time in-place stationary noise cancellation ($< 0.1\text{ ms}$ latency).
- **Acoustic Transient & Periodicity Extraction** — Instantaneous Zero-Crossing Rate (ZCR), Crest Factor, and Autocorrelation analysis.
- **Native Autocorrelation Pitch Tracking** — Detects fundamental frequencies (F0) at sub-millisecond speeds.
- **SOLA Pitch Shifting** — Synchronized Overlap-Add algorithm for pitch modulation without changing playback duration.
- **Off-Heap Direct Buffers** — Operates directly on native memory buffers to prevent JVM Garbage Collection stutters.

---

## Key Features

* **⚡ Native AVX2 SIMD Acceleration** — Accelerated float audio vector math for 44.1kHz / 48kHz audio streams.
* **🔇 Real-Time Noise Suppression** — High-speed spectral subtraction & Wiener gain smoothing for studio voice isolation.
* **📊 Acoustic Feature Extraction** — Zero-allocation Crest Factor, Zero-Crossing Rate (ZCR), and Harmonic Periodicity primitives.
* **🎤 Real-Time Pitch Tracking** — High-speed autocorrelation pitch estimator for voice and instrument tuning.
* **🎵 SOLA Pitch Shifter** — Native time-domain pitch shifter preserving audio duration and tempo.
* **🎛️ Zero-GC DSP Filters** — Multi-channel gain, 3-band equalizer, dynamic noise gate, and FIR filters.
* **🔄 Interoperable Java Audio Bridge** — Seamless conversion to and from standard `javax.sound.sampled` formats.

---

## Real-World Use Cases

- 🎙️ **Live Voice AI & STT Preprocessing**: Eliminates microphone hiss, room fans, and background noise prior to Whisper/FastSTT.
- 🗣️ **VAD & Voice Feature Classifiers**: Supplies fundamental acoustic metrics (Crest Factor, ZCR, Periodicity) to **[FastVAD](https://github.com/andrestubbe/FastVAD)**.
- 🎸 **Digital Audio Workstations (DAW)**: High-speed audio effect plugins running on **[FastAudioCapture](https://github.com/andrestubbe/FastAudioCapture)** streams.
- 🤖 **Voice Bots & Agent Pipelines**: Studio-quality voice clean-up with zero cloud API minute-fees.
- 🎮 **Game Sound Engines**: Multi-channel audio mixer with real-time pitch shifting for sound effects.

---

## Performance Benchmarks

In the official [JMH Benchmark](examples/Benchmark), `FastAudioProcess` measured throughput across real-time DSP audio operations on 512-sample (16 kHz / 32 ms) frames:

```text
Benchmark                                Mode  Cnt        Score   Units
JMH_Audio.benchmarkAcousticCrestFactor  thrpt    2  1,619,341   ops/s
JMH_Audio.benchmarkAcousticZCR          thrpt    2  1,428,964   ops/s
JMH_Audio.benchmarkStreamingEqualizer   thrpt    2    416,658   ops/s
JMH_Audio.benchmarkFastFFT              thrpt    2    182,448   ops/s
JMH_Audio.benchmarkPitchDetection       thrpt    2     48,836   ops/s
JMH_Audio.benchmarkSpectralDenoise      thrpt    2      8,189   ops/s
```

> **Sub-Microsecond Latency**: Acoustic features execute at **1.6M+ operations/sec**, native AVX2 pitch tracking delivers **48,800+ detections/sec**, and native FastFFT transforms run at **180,000+ transforms/sec** with **zero runtime GC allocations**.

---

## Architecture Overview

**FastAudioProcess (This Library — Native DSP Engine)**  
Provides SIMD-accelerated pitch detection, SOLA pitch shifting, noise suppression, and acoustic analysis.

**[FastSIMD](https://github.com/andrestubbe/FastSIMD) (Hardware Acceleration Engine)**  
Provides cross-platform hardware SIMD vectorization primitives.

**[FastVAD](https://github.com/andrestubbe/FastVAD) (Neural Voice Activity Detector)**  
Consumes acoustic features and performs sub-10ms barge-in voice segmentation.

**[FastAudioCapture](https://github.com/andrestubbe/FastAudioCapture) (WASAPI Audio Capture)**  
Captures low-latency Windows audio streams for `FastAudioProcess`.

---

## API Quick Reference

| Method | Description | Path |
|--------|-------------|------|
| `suppressNoise(samples, rate, factor, floor)` | Real-time spectral power subtraction noise filter. | [Reference](docs/REFERENCE.md#suppressnoise) |
| `computeCrestFactor(samples)` | Spectral Crest Factor (Peak / RMS) transient spike detector. | [Reference](docs/REFERENCE.md#computecrestfactor) |
| `computeZeroCrossingRate(samples)` | Normalized Zero-Crossing Rate (ZCR) sign-change ratio. | [Reference](docs/REFERENCE.md#computezcr) |
| `computeAutocorrelationPeriodicity(samples, min, max)` | Normalized pitch harmonic periodicity ratio. | [Reference](docs/REFERENCE.md#computeperiodicity) |
| `logMelSpectrogram(samples, rate, fft, hop, bins)` | High-accuracy triangular Mel-filterbank spectrogram. | [Reference](docs/REFERENCE.md#logmelspectrogram) |
| `applyNoiseGate(samples, thresholdDb, reductionDb)` | Fast dynamic downward expander noise gate. | [Reference](docs/REFERENCE.md#applynoisegate) |
| `detectPitchNative(samples, rate)` | AVX2 SIMD pitch detection using autocorrelation. | [Reference](docs/REFERENCE.md#detectpitch) |
| `pitchShiftNative(samples, semitones, rate)` | Zero-allocation time-domain pitch modulation algorithm. | [Reference](docs/REFERENCE.md#pitchshift) |
| `FastAudioEqualizer` | Stateful 3-band streaming IIR equalizer. | [Reference](docs/REFERENCE.md#equalizer) |
| `FastAudioChunker` | Lock-free SPSC power-of-two frame chunker. | [Reference](docs/REFERENCE.md#chunker) |
| `FastFFT` | Forward & inverse Radix-2 Fast Fourier Transform. | [Reference](docs/REFERENCE.md#fft) |

---

## Installation

### Option 1: Maven (Recommended)

Add the JitPack repository and the complete dependency stack to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <!-- FastAudioProcess Engine -->
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>FastAudioProcess</artifactId>
        <version>0.1.4</version>
    </dependency>

    <!-- FastSIMD Hardware Vector Acceleration Engine -->
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>FastSIMD</artifactId>
        <version>0.1.3</version>
    </dependency>

    <!-- FastMemory Aligned Allocator -->
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>FastMemory</artifactId>
        <version>0.1.1</version>
    </dependency>

    <!-- FastPointer Address Wrapper -->
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>FastPointer</artifactId>
        <version>0.1.1</version>
    </dependency>

    <!-- FastCore Native Loader -->
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>FastCore</artifactId>
        <version>0.1.0</version>
    </dependency>
</dependencies>
```

### Option 2: Gradle (via JitPack)

Add the repository and dependencies to your `build.gradle`:

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.andrestubbe:FastAudioProcess:0.1.4'
    implementation 'com.github.andrestubbe:FastSIMD:0.1.3'
    implementation 'com.github.andrestubbe:FastMemory:0.1.1'
    implementation 'com.github.andrestubbe:FastPointer:0.1.1'
    implementation 'com.github.andrestubbe:FastCore:0.1.0'
}
```

### Option 3: Direct Download (No Build Tool)

Download the latest JAR directly to add it to your classpath:

1. 📦 **[FastAudioProcess-0.1.4.jar](https://github.com/andrestubbe/FastAudioProcess/releases/download/0.1.4/FastAudioProcess-0.1.4.jar)** (The Core Library)
2. 📦 **[FastSIMD-0.1.3.jar](https://github.com/andrestubbe/FastSIMD/releases/download/0.1.3/FastSIMD-0.1.3.jar)** (Vector Math Substrate)
3. 📦 **[FastMemory-0.1.1.jar](https://github.com/andrestubbe/FastMemory/releases/download/0.1.1/FastMemory-0.1.1.jar)** (Aligned Off-Heap Allocator)
4. 📦 **[FastPointer-0.1.1.jar](https://github.com/andrestubbe/FastPointer/releases/download/0.1.1/FastPointer-0.1.1.jar)** (Memory Address Wrapper)
5. 📦 **[FastCore-0.1.0.jar](https://github.com/andrestubbe/FastCore/releases/download/0.1.0/FastCore-0.1.0.jar)** (Native JNI Extractor & Loader)

---

---

## Documentation

- [COMPILE.md](docs/COMPILE.md): Full compilation guide (MSVC Native AVX2 & Maven Build Setup).
- [REFERENCE.md](docs/REFERENCE.md): Exhaustive catalog of DSP algorithms, FFT kernels, and acoustics architecture.
- [PHILOSOPHY.md](docs/PHILOSOPHY.md): Zero-allocation, native-first, and low-overhead real-time DSP design principles.
- [ROADMAP.md](docs/ROADMAP.md): Planned milestone features, ARM NEON SIMD, and GPU compute extensions.
- [CHANGELOG.md](docs/CHANGELOG.md): Complete chronological record of version releases and performance improvements.

---

## Platform Support

| Platform | Architecture | Status | Vector ISA |
|----------|--------------|--------|------------|
| Windows 10 / 11 | x64 (AMD64) | ✅ Fully Supported | AVX2 / FMA3 |
| Windows Server 2019+ | x64 | ✅ Fully Supported | AVX2 |
| Linux (Ubuntu/Debian) | x64 / ARM64 | 🚧 Planned | AVX2 / NEON |
| macOS (Apple Silicon) | ARM64 | 🚧 Planned | NEON |

---

## License

MIT License — See [LICENSE](LICENSE) for details.

---

## Related Projects

- [FastVAD](https://github.com/andrestubbe/FastVAD) — Real-time Voice Activity Detection with sub-10ms latency
- [FastAudioCapture](https://github.com/andrestubbe/FastAudioCapture) — Zero-copy WASAPI audio loopback & microphone capture
- [FastAudioPlayer](https://github.com/andrestubbe/FastAudioPlayer) — Ultra low-latency WASAPI and DirectSound audio playback engine
- [FastTurn](https://github.com/andrestubbe/FastTurn) — Real-time turn-taking and speech interruption latency detector
- [FastWakeWord](https://github.com/andrestubbe/FastWakeWord) — Neural wake-word and keyword spotting detector
- [FastSTT](https://github.com/andrestubbe/FastSTT) — High-throughput local speech-to-text recognition
- [FastTTS](https://github.com/andrestubbe/FastTTS) — Low-latency neural text-to-speech synthesis
- [FastSIMD](https://github.com/andrestubbe/FastSIMD) — Hardware SIMD vectorization primitives
- [FastCore](https://github.com/andrestubbe/FastCore) — Native JNI Loader and Utilities

---

*Part of the FastJava Ecosystem — Making the JVM faster. Small package. Maximum speed. Zero bloat. 🚀*