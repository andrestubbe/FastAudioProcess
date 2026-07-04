# FastAudioProcess Reference

## 1. CPU & SIMD Feature Model
*   **JDK Vector API (SIMD)** — Used for high-performance JVM-level vectorization (e.g., RMS calculation).
*   **AVX2 / SSE4.2** — detected via CPUID for native routines. Enables 32-byte and 16-byte vector ops.
*   **Fallback rule**: JDK Vector API / Native AVX2 → SSE4.2 → scalar.

## 2. Guarantees
*   **Zero-Copy**: All operations use direct memory pinning or direct buffers for zero copy between Java and native layers.
*   **Unaligned Access**: Safe on all byte boundaries.
*   **Thread-Safety**: All static native and Java processing methods are thread-safe.

## 3. JNI & Memory Contracts
*   **Direct Memory Pinning**: No implicit copies are made by the JNI bridge.
*   **No Allocation**: All operations work on pre-allocated Java arrays or buffers.
*   **Critical Sections**: Native calls minimize blocking to prevent GC impact.

## 4. Platform Support
| Platform | Status |
|----------|--------|
| Windows 10/11 (x64) | ✅ Fully Supported |

---
**Part of the FastJava Ecosystem** — *Making the JVM faster.*

Made with ⚡ by Andre Stubbe
