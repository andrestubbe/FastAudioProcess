package fastaudioprocess;

/**
 * High-speed stationary noise cancellation and dynamic noise gate filters powered by FastFFT.
 */
public final class FastAudioDenoise {

    private static final ThreadLocal<float[]> TL_REAL = ThreadLocal.withInitial(() -> new float[4096]);
    private static final ThreadLocal<float[]> TL_IMAG = ThreadLocal.withInitial(() -> new float[4096]);
    private static final ThreadLocal<float[]> TL_MAG  = ThreadLocal.withInitial(() -> new float[4096]);

    private FastAudioDenoise() {
    }

    public static void suppressNoise(float[] samples, int sampleRate, float reductionFactor, float spectralFloor) {
        if (samples == null || samples.length < 32) return;
        int n = samples.length;

        int fftSize = 1;
        while ((fftSize << 1) <= n) fftSize <<= 1;

        float[] real = TL_REAL.get();
        float[] imag = TL_IMAG.get();
        float[] mag  = TL_MAG.get();

        if (real.length < fftSize) {
            real = new float[fftSize * 2];
            imag = new float[fftSize * 2];
            mag  = new float[fftSize * 2];
            TL_REAL.set(real);
            TL_IMAG.set(imag);
            TL_MAG.set(mag);
        }

        for (int i = 0; i < fftSize; i++) {
            float w = (float) (0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (fftSize - 1))));
            real[i] = samples[i] * w;
            imag[i] = 0.0f;
        }

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

        FastFFT.ifft(real, imag);

        for (int i = 0; i < fftSize; i++) {
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