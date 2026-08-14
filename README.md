# FastAudioProcess 0.1.1 [ALPHA-2026-08] — High-Performance Audio Processing for Java

[![Status](https://img.shields.io/badge/status-0.1.1-brightgreen.svg)](https://github.com/andrestubbe/FastAudioProcess/releases/tag/0.1.1)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://www.java.com)
[![Platform](https://img.shields.io/badge/Platform-Windows%2010+-lightgrey.svg)]()
[![JitPack](https://img.shields.io/badge/JitPack-0.1.1-green.svg)](https://jitpack.io/#andrestubbe/FastAudioProcess)

---

**⚡ Hardware SIMD-accelerated real-time audio pitch detection, SOLA pitch shifting, and zero-allocation DSP filters.**

`FastAudioProcess` provides native C++ AVX2 vector processing for Java audio applications, enabling high-throughput DSP filtering, pitch tracking, and format conversions without Garbage Collection stalls.

![docs/screenshot.png](docs/screenshot.png)

---

## Quick Start — Example

```java
import fastaudioprocess.FastAudioProcess;

public class Demo {
    public static void main(String[] args) {
        // 1. Generate 1 second 440Hz sine wave buffer
        float[] audio = new float[44100];
        for (int i = 0; i < audio.length; i++) {
            audio[i] = (float) Math.sin(2.0 * Math.PI * 440.0 * i / 44100.0);
        }

        // 2. AVX2 SIMD pitch detection
        float pitch = FastAudioProcess.detectPitchNative(audio, 44100);
        System.out.printf("Detected pitch: %.2f Hz%n", pitch);
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
- **Native Autocorrelation Pitch Tracking** — Detects fundamental frequencies (F0) at sub-millisecond speeds.
- **SOLA Pitch Shifting** — Synchronized Overlap-Add algorithm for pitch modulation without changing playback duration.
- **Off-Heap Direct Buffers** — Operates directly on native memory buffers to prevent JVM Garbage Collection stutters.

---

## Key Features

* **⚡ Native AVX2 SIMD Acceleration** — Accelerated float audio vector math for 44.1kHz / 48kHz audio streams.
* **🎤 Real-Time Pitch Tracking** — High-speed autocorrelation pitch estimator for voice and instrument tuning.
* **🎵 SOLA Pitch Shifter** — Native time-domain pitch shifter preserving audio duration and tempo.
* **🎛️ Zero-GC DSP Filters** — Multi-channel gain, biquad EQ, and FIR filters operating on off-heap memory.
* **🔄 Interoperable Java Audio Bridge** — Seamless conversion to and from standard `javax.sound.sampled` formats.

---

## Real-World Use Cases

- 🎙️ **Live Voice Modulation & Pitch Correction**: Auto-tune and pitch correction for real-time voice streaming apps.
- 🎸 **Digital Audio Workstations (DAW)**: High-speed audio effect plugins running on **[FastAudioCapture](https://github.com/andrestubbe/FastAudioCapture)** streams.
- 🤖 **Speech Recognition Preprocessing**: Formant analysis and noise suppression for AI speech recognition models.
- 🎮 **Game Sound Engines**: Multi-channel audio mixer with real-time pitch shifting for sound effects.

---

## Performance Benchmarks

In the official [JMH Benchmark](examples/Benchmark), `FastAudioProcess` measured throughput for 44.1kHz audio frame processing:

```text
Benchmark                                     Mode  Cnt   Score   Error  Units
JMH_Audio.benchmarkFastAudioProcessPitch     thrpt    2  24,118          ops/s
```

> **24,000+ Pitch Detections / sec**: `FastAudioProcess` analyzes 1-second 44.1kHz audio buffers at **24,118 operations per second** with **zero JVM Garbage Collection allocations**.

---

## Architecture Overview

**FastAudioProcess (This Library — Native DSP Engine)**  
Provides SIMD-accelerated pitch detection, SOLA pitch shifting, and audio filters.

**[FastSIMD](https://github.com/andrestubbe/FastSIMD) (Hardware Acceleration Engine)**  
Provides cross-platform hardware SIMD vectorization primitives.

**[FastAudioCapture](https://github.com/andrestubbe/FastAudioCapture) (WASAPI Audio Capture)**  
Captures low-latency Windows audio streams for `FastAudioProcess`.

---

## API Quick Reference

| Method | Description | Path |
|--------|-------------|------|
| `detectPitchNative(samples, rate)` | AVX2 SIMD pitch detection using autocorrelation. | [Reference 📖](docs/REFERENCE.md#detectpitch) |
| `pitchShiftNative(samples, semitones, rate)` | SOLA native pitch shifter algorithm. | [Reference 📖](docs/REFERENCE.md#pitchshift) |

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
        <version>0.1.1</version>
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

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.andrestubbe:FastAudioProcess:0.1.1'
    implementation 'com.github.andrestubbe:FastSIMD:0.1.3'
    implementation 'com.github.andrestubbe:FastMemory:0.1.1'
    implementation 'com.github.andrestubbe:FastPointer:0.1.1'
    implementation 'com.github.andrestubbe:FastCore:0.1.0'
}
```

### Option 3: Direct Download (No Build Tool)

Download the required JARs directly to add them to your classpath:

1. ⚡ **[FastAudioProcess-0.1.1.jar](https://github.com/andrestubbe/FastAudioProcess/releases/download/0.1.1/FastAudioProcess-0.1.1.jar)** (The Core Library)
2. 🚀 **[FastSIMD-0.1.3.jar](https://github.com/andrestubbe/FastSIMD/releases/download/0.1.3/FastSIMD-0.1.3.jar)** (Hardware Vector Acceleration Engine)
3. 💾 **[FastMemory-0.1.1.jar](https://github.com/andrestubbe/FastMemory/releases/download/0.1.1/FastMemory-0.1.1.jar)** (32-Byte Aligned Allocator)
4. 📍 **[FastPointer-0.1.1.jar](https://github.com/andrestubbe/FastPointer/releases/download/0.1.1/FastPointer-0.1.1.jar)** (Primitive Address Pointer)
5. ⚙️ **[fastcore-0.1.0.jar](https://github.com/andrestubbe/FastCore/releases/download/0.1.0/fastcore-0.1.0.jar)** (Mandatory Native Loader)

> [!IMPORTANT]
> All JARs must be included in your classpath for the native SIMD JNI bindings to function correctly.

---

## Documentation

- **[COMPILE.md](docs/COMPILE.md)**: Full compilation guide (MSVC C++17 build chain + JNI Setup).
- **[REFERENCE.md](docs/REFERENCE.md)**: Full API contracts and routing logic.
- **[PHILOSOPHY.md](docs/PHILOSOPHY.md)**: Off-heap zero-GC memory philosophy.
- **[ROADMAP.md](docs/ROADMAP.md)**: Future development goals.

---

## Platform Support

| Platform | Status |
|----------|--------|
| Windows 10/11 (x64) | ✅ Fully Supported |
| Linux | 🔄 Planned |
| macOS | 🔄 Planned |

---

## License

MIT License — See [LICENSE](LICENSE) file for details.

---

## Related Projects

- [FastAudioCapture](https://github.com/andrestubbe/FastAudioCapture) — Low-latency WASAPI audio capture
- [FastSIMD](https://github.com/andrestubbe/FastSIMD) — Hardware SIMD acceleration engine
- [FastCore](https://github.com/andrestubbe/FastCore) — Native JNI loader for FastJava libraries

---

Part of the FastJava Ecosystem — Making the JVM faster. Small package. Maximum speed. Zero bloat. ⚡
