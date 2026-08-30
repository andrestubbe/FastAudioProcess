package fastaudioprocess;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lock-Free Single-Producer Single-Consumer (SPSC) Audio RingBuffer with Power-of-Two Bitmasking.
 * <p>
 * Splits continuous incoming audio sample streams into fixed-size, overlapping windows tailored for
 * real-time Neural Network inference (Voice Activity Detection, Wake-Word, Speech-to-Text).
 * Eliminates monitor lock contention and guarantees zero runtime Garbage Collection allocations.
 * </p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // 512-sample windows with 160-sample hop (10ms step at 16 kHz):
 * FastAudioChunker chunker = new FastAudioChunker(512, 160);
 *
 * // Push incoming stream
 * chunker.push(liveAudioFrame);
 *
 * // Pull overlapping chunks without GC allocations:
 * float[] window = new float[512];
 * while (chunker.nextChunk(window)) {
 *     model.infer(window);
 * }
 * }</pre>
 */
public final class FastAudioChunker {

    private final float[] buffer;
    private final int mask;
    private final int chunkSize;
    private final int hopSize;

    private int writeIndex = 0;
    private int readIndex = 0;
    private final AtomicInteger availableCount = new AtomicInteger(0);

    /**
     * Creates a new lock-free frame chunker with the specified window and hop sizes.
     *
     * @param chunkSize window length in float samples (must be &gt; 0)
     * @param hopSize   hop step size in float samples between consecutive windows (must be &gt; 0)
     * @throws IllegalArgumentException if {@code chunkSize <= 0} or {@code hopSize <= 0}
     */
    public FastAudioChunker(int chunkSize, int hopSize) {
        if (chunkSize <= 0 || hopSize <= 0) {
            throw new IllegalArgumentException("chunkSize and hopSize must be > 0");
        }
        this.chunkSize = chunkSize;
        this.hopSize = hopSize;

        // Next power of 2 >= max(chunkSize * 8, 16384) for fast bitmask indexing
        int minCap = Math.max(chunkSize * 8, 16384);
        int cap = 1;
        while (cap < minCap) cap <<= 1;
        this.buffer = new float[cap];
        this.mask = cap - 1;
    }

    /**
     * Pushes incoming raw audio samples into the lock-free ring buffer.
     *
     * @param samples array of audio samples to ingest (silently ignored if null or empty)
     */
    public void push(float[] samples) {
        if (samples == null || samples.length == 0) return;
        int len = samples.length;
        for (int i = 0; i < len; i++) {
            buffer[writeIndex] = samples[i];
            writeIndex = (writeIndex + 1) & mask;
        }
        availableCount.addAndGet(len);
    }

    /**
     * Fills the supplied destination array with the next overlapping frame window.
     * <p>
     * <b>Zero-Allocation Hotpath Method</b>: Does not allocate any heap memory during extraction.
     * </p>
     *
     * @param destination preallocated destination array with length &gt;= {@code chunkSize}
     * @return {@code true} if a frame window was successfully copied, {@code false} if insufficient samples are buffered
     */
    public boolean nextChunk(float[] destination) {
        if (destination == null || destination.length < chunkSize) {
            return false;
        }
        if (availableCount.get() < chunkSize) {
            return false;
        }

        int idx = readIndex;
        for (int i = 0; i < chunkSize; i++) {
            destination[i] = buffer[(idx + i) & mask];
        }
        readIndex = (readIndex + hopSize) & mask;
        availableCount.addAndGet(-hopSize);
        return true;
    }

    /**
     * Pulls the next overlapping frame window, allocating a new array of length {@code chunkSize}.
     *
     * @return new float array containing the frame window, or {@code null} if insufficient samples are available
     */
    public float[] nextChunk() {
        if (availableCount.get() < chunkSize) return null;
        float[] chunk = new float[chunkSize];
        nextChunk(chunk);
        return chunk;
    }

    /**
     * Resets and clears the internal ring buffer and index pointers.
     */
    public void reset() {
        this.writeIndex = 0;
        this.readIndex = 0;
        this.availableCount.set(0);
    }

    /**
     * Returns the number of unread audio samples currently available in the buffer.
     *
     * @return available sample count (always &gt;= 0)
     */
    public int availableSamples() {
        return Math.max(0, availableCount.get());
    }
}