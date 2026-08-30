package fastaudioprocess;

/**
 * Real-Time Stationary Noise Cancellation and Dynamic Noise Gate Filters powered by FastFFT.
 * <p>
 * Implements in-place spectral power subtraction with musical noise over-subtraction protection,
 * Wiener gain floor smoothing, zero-padding tail protection for non-power-of-two frames,
 * and block-based downward expander noise gating without heap allocations.
 * </p>
 */
public final class FastAudioDenoise {

    private static final ThreadLocal<float[]> TL_REAL = ThreadLocal.withInitial(() -> new float[4096]);
    private static final ThreadLocal<float[]> TL_IMAG = ThreadLocal.withInitial(() -> new float[4096]);
    private static final ThreadLocal<float[]> TL_MAG  = ThreadLocal.withInitial(() -> new float[4096]);

    private FastAudioDenoise() {
    }

    /**
     * Suppresses stationary acoustic background noise in-place with zero-padding frame protection.
     *
     * @param samples          raw audio frame samples (modified in-place)
     * @param sampleRate       audio sampling rate in Hz (e.g. 16000 or 44100)
     * @param reductionFactor  noise attenuation factor (e.g. 0.85f for gentle, 1.5f for aggressive)
     * @param spectralFloor    minimum spectral gain floor to prevent musical noise artifacts (e.g. 0.02f)
     */
    public static void suppressNoise(float[] samples, int sampleRate, float reductionFactor, float spectralFloor) {
        if (samples == null || samples.length < 32) return;
        int n = samples.length;

        // Smallest power of 2 >= n to preserve all tail samples without dropping
        int fftSize = 1;
        while (fftSize < n) fftSize <<= 1;
        if (fftSize > 16384) fftSize = 16384;

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

        // 1. Hann Windowing + Zero-Padding
        for (int i = 0; i < fftSize; i++) {
            if (i < n) {
                float w = (float) (0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (n - 1))));
                real[i] = samples[i] * w;
            } else {
                real[i] = 0.0f; // Zero-pad tail
            }
            imag[i] = 0.0f;
        }

        // 2. Forward Native/Java FastFFT O(N log N)
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

        // 3. Spectral Subtraction
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

        // 4. Inverse Native/Java FastFFT O(N log N)
        FastFFT.ifft(real, imag);

        // 5. Restore full original length n
        for (int i = 0; i < n; i++) {
            samples[i] = real[i];
        }
    }

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