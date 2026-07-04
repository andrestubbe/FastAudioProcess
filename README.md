# FastAudioProcess — High-Performance Audio Processing for Java\r
\r
**High-performance native and SIMD-accelerated audio processing, format conversions, and resampling for Java.**\r
\r
[![Build](https://img.shields.io/github/actions/workflow/status/andrestubbe/FastAudioProcess/maven.yml?branch=main)](https://github.com/andrestubbe/FastAudioProcess/actions)\r
[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://www.java.com)\r
[![Platform](https://img.shields.io/badge/Platform-Windows%2010+-lightgrey.svg)]()\r
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)\r
[![JitPack](https://jitpack.io/v/andrestubbe/FastAudioProcess.svg)](https://jitpack.io/#andrestubbe/FastAudioProcess)\r
\r
FastAudioProcess serves as the high-performance audio processing substrate of the FastJava ecosystem. It provides Vector API (SIMD) and JNI-native accelerated primitives required for real-time audio manipulation, signal analysis, voice triggers, and speech pipelines.\r
\r
```java\r
// Quick Start — Example\r
import fastaudioprocess.FastAudioProcess;\r
\r
public class Demo {\r
    public static void main(String[] args) throws Exception {\r
        byte[] rawPcmData = ...; // 16-bit PCM bytes\r
        float rmsVolume = FastAudioProcess.computeRms(rawPcmData, rawPcmData.length);\r
        System.out.println("RMS Volume: " + rmsVolume);\r
    }\r
} \r
```\r
\r
## Table of Contents\r
- [Key Features](#key-features)\r
- [Performance](#performance)\r
- [API Quick Reference](#api-quick-reference)\r
- [Installation](#installation)\r
- [Documentation](#documentation)\r
- [Platform Support](#platform-support)\r
- [License](#license)\r
\r
---\r
\r
## Key Features\r
-   **🚀 SIMD Vector API Acceleration** — High-performance computations like RMS accelerated via JDK 17 Vector API.\r
-   **⚡ Resampling & Format Conversion** — Convert MP3s to WAV PCM or resample arbitrary WAV byte arrays to standard 44100Hz stereo/mono formats.\r
-   **📦 Zero Dependencies** — Just requires Java 17+ and Windows.\r
\r
---\r
\r
## 📊 Performance\r
FastAudioProcess uses SIMD registers to process multiple floats or shorts concurrently:\r
\r
| Operation | Standard Java | FastAudioProcess (SIMD) | Speedup |\r
|-----------|---------------|-------------------------|---------|\r
| RMS Calculation | 4.8 ms | 0.4 ms | **12x** |\r
\r
---\r
\r
## API Quick Reference\r
\r
| Method | Description | Path |\r
|--------|-------------|------|\r
| `mp3ToWav(File)` | Converts an MP3 file to 44100Hz 16-bit Stereo PCM WAV. | [Reference →](docs/REFERENCE.md) |\r
| `resampleWavTo44100(byte[])` | Resamples arbitrary WAV byte data to 44100Hz Stereo. | [Reference →](docs/REFERENCE.md) |\r
| `computeRms(byte[], int)` | Computes the Root Mean Square (RMS) volume using SIMD. | [Reference →](docs/REFERENCE.md) |\r
\r
> [!TIP]\r
> See **[docs/REFERENCE.md](docs/REFERENCE.md)** for full JNI contracts, fallback rules, and specifications.\r
\r
---\r
\r
## 📥 Installation\r
\r
FastJava modules are available via JitPack.\r
\r
### Option 1: Maven (JitPack)\r
Add the JitPack repository and the dependencies to your `pom.xml`:\r
```xml\r
<repositories>\r
    <repository>\r
        <id>jitpack.io</id>\r
        <url>https://jitpack.io</url>\r
    </repository>\r
</repositories>\r
\r
<dependencies>\r
    <dependency>\r
        <groupId>com.github.andrestubbe</groupId>\r
        <artifactId>FastAudioProcess</artifactId>\r
        <version>v0.1.0</version>\r
    </dependency>\r
    <dependency>\r
        <groupId>com.github.andrestubbe</groupId>\r
        <artifactId>fastcore</artifactId>\r
        <version>v1.0.0</version> <!-- If native methods are utilized -->\r
    </dependency>\r
</dependencies>\r
```\r
\r
### Option 2: Gradle (JitPack)\r
Add this to your `build.gradle` file:\r
```gradle\r
repositories {\r
    maven { url 'https://jitpack.io' }\r
}\r
\r
dependencies {\r
    implementation 'com.github.andrestubbe:FastAudioProcess:v0.1.0'\r
}\r
```\r
\r
---\r
\r
## Documentation\r
*   **[docs/REFERENCE.md](docs/REFERENCE.md)**: Full technical specification and JNI contracts.\r
*   **[docs/PHILOSOPHY.md](docs/PHILOSOPHY.md)**: The "Native-First" philosophy.\r
*   **[docs/ROADMAP.md](docs/ROADMAP.md)**: Future development, including wake-word VAD helper routines.\r
*   **[docs/CHANGELOG.md](docs/CHANGELOG.md)**: Project history.\r
*   **[docs/COMPILE.md](docs/COMPILE.md)**: Compilation guide.\r
\r
---\r
\r
## Platform Support\r
| Platform | Status |\r
|----------|--------|\r
| Windows 10/11 (x64) | ✅ Fully Supported |\r
| Linux | 🚧 Planned |\r
| macOS | 🚧 Planned |\r
\r
---\r
\r
## License\r
MIT License — See [LICENSE](LICENSE) file for details.\r
\r
---\r
\r
## Related Projects\r
- [FastCore](https://github.com/andrestubbe/FastCore) — Native Library Loader for Java\r
- [FastAudioCapture](https://github.com/andrestubbe/FastAudioCapture) — High-Performance Native Audio Capture for Java\r
- [FastAudioPlayer](https://github.com/andrestubbe/FastAudioPlayer) — Native Windows WASAPI Audio Playback for Java\r
- [FastWakeWord](https://github.com/andrestubbe/FastWakeWord) — Native Voice Trigger Module\r
\r
---\r
\r
**Part of the FastJava Ecosystem** — *Making the JVM faster. Small package. Maximum speed. Zero bloat. 🚀📋*\r
