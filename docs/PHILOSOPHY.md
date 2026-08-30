# FastAudioProcess Design Philosophy

`FastAudioProcess` is built on the core engineering tenets of the **FastJava Ecosystem**:

1. **Zero Runtime Garbage Collection**: Audio hotpaths must not allocate heap objects. All FFT scratch spaces, delay lines, and ring buffers use thread-local or preallocated memory.
2. **Native-First Acceleration**: Where CPU SIMD vectorization provides orders of magnitude speedups (AVX2 pitch detection, FastFFT), C++ native substrates with JNI Critical Arrays (`GetPrimitiveArrayCritical`) are used.
3. **Lock-Free Streaming**: Multi-threaded audio pipelines must not block on monitor locks. SPSC ringbuffers utilize atomic counters and power-of-two bitmasks.
4. **Stateful Streaming Continuity**: Filter crossover memory (Equalizers, Overlap-Add) is preserved across chunk boundaries to eliminate audio crackles and phase discontinuities.
5. **Standard Mathematical Precision**: Standard triangular Mel filterbanks, Hermitian symmetry preservation, and DC-removal ensure mathematical correctness for neural inference (VAD, STT).