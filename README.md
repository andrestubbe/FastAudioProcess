# FastAudioProcess — High-Performance Audio Processing for Java

**High-performance native and SIMD-accelerated audio processing, format conversions, and resampling for Java.**

[![Build](https://img.shields.io/github/actions/workflow/status/andrestubbe/FastAudioProcess/maven.yml?branch=main)](https://github.com/andrestubbe/FastAudioProcess/actions)
[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://www.java.com)
[![Platform](https://img.shields.io/badge/Platform-Windows%2010+-lightgrey.svg)]()
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![JitPack](https://jitpack.io/v/andrestubbe/FastAudioProcess.svg)](https://jitpack.io/#andrestubbe/FastAudioProcess)

FastAudioProcess serves as the high-performance audio processing substrate of the FastJava ecosystem. It provides Vector API (SIMD) and JNI-native accelerated primitives required for real-time audio manipulation, signal analysis, voice triggers, and speech pipelines.

```java
// Quick Start — Example
import fastaudioprocess.FastAudioProcess;

public class Demo {
    public static void main(String[] args) throws Exception {
        byte[] rawPcmData = new byte[16000]; // 16-bit PCM bytes
        float rmsVolume = FastAudioProcess.computeRms(rawPcmData, rawPcmData.length);
        System.out.println("RMS Volume: " + rmsVolume);
    }
} 
```

## Table of Contents
- [Key Features](#key-features)
- [Performance](#performance)
- [API Quick Reference](#api-quick-reference)
- [Installation](#installation)
- [Documentation](#documentation)
- [Platform Support](#platform-support)
- [License](#license)

---

## Key Features
-   **🚀 SIMD Vector API Acceleration** — High-performance computations like RMS accelerated via JDK 17 Vector API.
-   **⚡ Resampling & Format Conversion** — Convert MP3s to WAV PCM or resample arbitrary WAV byte arrays to standard 44100Hz stereo/mono formats.
-   **📦 Zero Dependencies** — Just requires Java 17+ and Windows.

---

## 📊 Performance
FastAudioProcess uses SIMD registers to process multiple floats or shorts concurrently:

| Operation | Standard Java | FastAudioProcess (SIMD) | Speedup |
|-----------|---------------|-------------------------|---------|
| RMS Calculation | 4.8 ms | 0.4 ms | **12x** |

---

## API Quick Reference

| Method | Description | Path |
|--------|-------------|------|
| `mp3ToWav(File)` | Converts an MP3 file to 44100Hz 16-bit Stereo PCM WAV. | [Reference →](docs/REFERENCE.md) |
| `resampleWavTo44100(byte[])` | Resamples arbitrary WAV byte data to 44100Hz Stereo. | [Reference →](docs/REFERENCE.md) |
| `computeRms(byte[], int)` | Computes the Root Mean Square (RMS) volume using SIMD. | [Reference →](docs/REFERENCE.md) |

> [!TIP]
> See **[docs/REFERENCE.md](docs/REFERENCE.md)** for full JNI contracts, fallback rules, and specifications.

---

## 📥 Installation

FastJava modules are available via JitPack.

### Option 1: Maven (JitPack)
Add the JitPack repository and the dependencies to your `pom.xml`:
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>FastAudioProcess</artifactId>
        <version>v0.1.0</version>
    </dependency>
</dependencies>
```

### Option 2: Gradle (JitPack)
Add this to your `build.gradle` file:
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.andrestubbe:FastAudioProcess:v0.1.0'
}
```

---

## Documentation
*   **[docs/REFERENCE.md](docs/REFERENCE.md)**: Full technical specification and JNI contracts.
*   **[docs/PHILOSOPHY.md](docs/PHILOSOPHY.md)**: The "Native-First" philosophy.
*   **[docs/ROADMAP.md](docs/ROADMAP.md)**: Future development, including wake-word VAD helper routines.
*   **[docs/CHANGELOG.md](docs/CHANGELOG.md)**: Project history.
*   **[docs/COMPILE.md](docs/COMPILE.md)**: Compilation guide.

---

## Platform Support
| Platform | Status |
|----------|--------|
| Windows 10/11 (x64) | ✅ Fully Supported |
| Linux | 🚧 Planned |
| macOS | 🚧 Planned |

---

## License
MIT License — See [LICENSE](LICENSE) file for details.

---

## Related Projects
- [FastCore](https://github.com/andrestubbe/FastCore) — Native Library Loader for Java
- [FastAudioCapture](https://github.com/andrestubbe/FastAudioCapture) — High-Performance Native Audio Capture for Java
- [FastAudioPlayer](https://github.com/andrestubbe/FastAudioPlayer) — Native Windows WASAPI Audio Playback for Java
- [FastWakeWord](https://github.com/andrestubbe/FastWakeWord) — Native Voice Trigger Module

---

**Part of the FastJava Ecosystem** — *Making the JVM faster. Small package. Maximum speed. Zero bloat. 🚀📋*
