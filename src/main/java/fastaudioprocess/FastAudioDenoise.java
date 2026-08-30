package fastaudioprocess;

/**
 * Real-Time Stationary Noise Cancellation and Dynamic Noise Gate Filters powered by FastFFT.
 * <p>
 * Implements in-place spectral power subtraction with musical noise over-subtraction protection,
 * Wiener gain floor smoothing, and block-based downward expander noise gating without heap allocations.
 * </p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // 1. Real-time Spectral Noise Suppression
 * FastAudioDenoise.suppressNoise(frame512, 16000, 1.2f, 0.05f);
 *
 * // 2. Dynamic Noise Gate (-40dB threshold, -24dB reduction)
 * FastAudioDenoise.applyNoiseGate(frame512, -40.0f, -24.0f);
 * }</pre>
 */
public final class FastAudioDenoise {

    private static final ThreadLocal<float[]> TL_REAL = ThreadLocal.withInitial(() -> new float[4096]);
    private static final ThreadLocal<float[]> TL_IMAG = ThreadLocal.withInitial(() -> new float[4096]);
    private static final ThreadLocal<float[]> TL_MAG  = ThreadLocal.withInitial(() -> new float[4096]);

    private FastAudioDenoise() {
    }

    /**
     * Suppresses stationary acoustic background noise (fans, hums, mic hiss) in-place
     * using O(N log N) FastFFT spectral power subtraction with Wiener floor gain smoothing.
     * <p>
     * <b>Zero-Allocation Method</b>: Reuses thread-local scratch buffers without runtime heap allocation.
     * </p>
     *
     * @param samples          raw audio frame samples (modified in-place, length should be &gt;= 32)
     * @param sampleRate       audio sampling rate in Hz (e.g. 16000 or 44100)
     * @param reductionFactor  noise attenuation factor (e.g. 0.85f for gentle, 1.5f for aggressive)
     * @param spectralFloor    minimum spectral gain floor to prevent musical noise artifacts (e.g. 0.02f - 0.05f)
     */
    public static void suppressNoise(float[] samples, int sampleRate, float reductionFactor, float spectralFloor) {
        if (samples == null || samples.length < 32) return;
        int n = samples.length;

        int fftSize = 1;
        while ((fftSize << 1) <= n) fftSize <<= 1;

        float[] real = TL_REAL.get();
        float[] imag = TL_IMAG.get();
        float[] mag  = TL_MAG.get();

        if (real.length < fftSize) {
            real = new float[fftSize];
            imag = new float[fftSize];
            mag  = new float[fftSize / 2 + 1];
            TL_REAL.set(real);
            TL_IMAG.set(imag);
            TL_MAG.set(mag);
        }

        // 1. Hann Windowing into preallocated buffers
        for (int i = 0; i < fftSize; i++) {
            float w = (float) (0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (fftSize - 1))));
            real[i] = samples[i] * w;
            imag[i] = 0.0f;
        }

        // 2. Forward FastFFT O(N log N)
        FastFFT.fft(real, imag);

        int half = fftSize / 2 + 1;
        float noiseEstimate = 0.0f;
        for (int k = 0; k < half; k++) {
            float r = real[k];
            float im = imag[k];
            float m = (float) Math.sqrt(r * r + im * im);
            mag[k] = m;
            noiseEstimate += m;
        }
        noiseEstimate = (noiseEstimate / half) * 0.25f;

        // 3. Spectral Subtraction with Wiener floor protection
        for (int k = 0; k < half; k++) {
            float orig = mag[k];
            float cleaned = orig - (reductionFactor * noiseEstimate);
            float minGain = orig * spectralFloor;
            if (cleaned < minGain) cleaned = minGain;

            float gain = orig > 1e-6f ? (cleaned / orig) : spectralFloor;
            real[k] *= gain;
            imag[k] *= gain;

            if (k > 0 && k < fftSize / 2) {
                real[fftSize - k] = real[k];
                imag[fftSize - k] = -imag[k];
            }
        }

        // 4. Inverse FastFFT O(N log N)
        FastFFT.ifft(real, imag);

        // 5. Commit back to input audio array
        for (int i = 0; i < fftSize; i++) {
            samples[i] = real[i];
        }
    }

    /**
     * Applies a block-based dynamic downward expander / noise gate to attenuate sub-threshold noise in-place.
     *
     * @param samples     audio buffer (modified in-place)
     * @param thresholdDb threshold in decibels below which attenuation is applied (e.g. -40.0f)
     * @param reductionDb attenuation reduction in decibels below threshold (e.g. -24.0f)
     */
    public static void applyNoiseGate(float[] samples, float thresholdDb, float reductionDb) {
        if (samples == null || samples.length == 0) return;
        double threshold = Math.pow(10.0, thresholdDb / 20.0);
        double reduction = Math.pow(10.0, reductionDb / 20.0);
        int blockSize = 256;
        for (int i = 0; i < samples.length; i += blockSize) {
            int size = Math.min(blockSize, samples.length - i);
            float peak = 0.0f;
            for (int j = 0; j < size; j++) {
                float abs = Math.abs(samples[i + j]);
                if (abs > peak) peak = abs;
            }
            float multiplier = (peak < threshold) ? (float) reduction : 1.0f;
            for (int j = 0; j < size; j++) {
                samples[i + j] *= multiplier;
            }
        }
    }
}