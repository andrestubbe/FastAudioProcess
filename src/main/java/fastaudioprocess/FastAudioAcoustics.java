package fastaudioprocess;

/**
 * Acoustic Feature Extraction and Neural Mel-Spectrogram Synthesizer powered by FastFFT.
 * <p>
 * Implements high-accuracy triangular Mel-filterbanks and zero-allocation frame analysis
 * for speech recognition, Voice Activity Detection, and acoustic event tagging.
 * </p>
 */
public final class FastAudioAcoustics {

    private FastAudioAcoustics() {
    }

    /**
     * Computes the Spectral Crest Factor (Peak / RMS) of an audio frame.
     * High values (&gt; 4.0) identify sharp impulsive transient spikes (claps, clicks, cutlery clatter).
     *
     * @param samples raw audio frame samples
     * @return crest factor ratio (dimensionless), or 0.0 if frame is silent
     */
    public static float computeCrestFactor(float[] samples) {
        if (samples == null || samples.length == 0) return 0.0f;
        float peak = 0.0f;
        double sumSq = 0.0;
        for (float s : samples) {
            float abs = Math.abs(s);
            if (abs > peak) peak = abs;
            sumSq += (s * s);
        }
        double rms = Math.sqrt(sumSq / samples.length);
        if (rms < 1e-6) return 0.0f;
        return (float) (peak / rms);
    }

    /**
     * Computes the Zero-Crossing Rate (ZCR) of an audio buffer.
     * Normalized ratio in range [0.0, 1.0] representing sign changes per sample.
     *
     * @param samples raw audio frame samples
     * @return normalized zero-crossing rate from 0.0 (DC) to 1.0 (Nyquist noise)
     */
    public static float computeZeroCrossingRate(float[] samples) {
        if (samples == null || samples.length <= 1) return 0.0f;
        int zeroCrossings = 0;
        float prev = samples[0];
        for (int i = 1; i < samples.length; i++) {
            float cur = samples[i];
            if ((cur >= 0.0f && prev < 0.0f) || (cur < 0.0f && prev >= 0.0f)) {
                zeroCrossings++;
            }
            prev = cur;
        }
        return (float) zeroCrossings / (float) (samples.length - 1);
    }

    /**
     * Measures fundamental harmonic pitch periodicity via normalized autocorrelation.
     *
     * @param samples raw audio frame samples
     * @param minLag  minimum sample lag (e.g. 35 for ~450 Hz at 16 kHz)
     * @param maxLag  maximum sample lag (e.g. 160 for ~100 Hz at 16 kHz)
     * @return normalized harmonicity ratio in [0.0, 1.0], where &gt; 0.35 indicates voiced speech/music
     */
    public static float computeAutocorrelationPeriodicity(float[] samples, int minLag, int maxLag) {
        if (samples == null || samples.length <= minLag) return 0.0f;
        int n = samples.length;
        int maxL = Math.min(maxLag, n / 2);
        
        double r0 = 0.0;
        for (float s : samples) r0 += (s * s);
        if (r0 < 1e-6) return 0.0f;

        double maxNormAutocorr = 0.0;
        for (int lag = minLag; lag <= maxL; lag++) {
            double sumLag = 0.0;
            double sumBase = 0.0;
            double sumShift = 0.0;
            for (int i = 0; i < n - lag; i++) {
                float s0 = samples[i];
                float sL = samples[i + lag];
                sumLag += (s0 * sL);
                sumBase += (s0 * s0);
                sumShift += (sL * sL);
            }
            double energy = Math.sqrt(sumBase * sumShift);
            if (energy > 1e-6) {
                double norm = sumLag / energy;
                if (norm > maxNormAutocorr) {
                    maxNormAutocorr = norm;
                }
            }
        }
        return (float) Math.min(1.0, Math.max(0.0, maxNormAutocorr));
    }

    /**
     * Computes high-accuracy Log-Mel Spectrogram using standard triangular Mel filterbanks.
     *
     * @param samples    raw audio samples
     * @param sampleRate sample rate in Hz (e.g. 16000)
     * @param fftSize    window FFT size (e.g. 512)
     * @param hopSize    step size between frames (e.g. 160)
     * @param melBins    number of Mel frequency bands (e.g. 80)
     * @return 2D float array of [numFrames][melBins]
     */
    public static float[][] logMelSpectrogram(float[] samples, int sampleRate, int fftSize, int hopSize, int melBins) {
        if (samples == null || samples.length < fftSize || fftSize <= 0 || hopSize <= 0 || melBins <= 0) {
            return new float[0][0];
        }
        int len = samples.length;
        int numFrames = (len - fftSize) / hopSize + 1;
        if (numFrames <= 0) return new float[0][0];

        float[][] melSpec = new float[numFrames][melBins];

        // 1. Precalculate Hann window
        float[] window = new float[fftSize];
        for (int i = 0; i < fftSize; i++) {
            window[i] = (float) (0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (fftSize - 1))));
        }

        // 2. Precalculate Triangular Mel Filterbank center points
        int specSize = fftSize / 2 + 1;
        double minMel = 0.0;
        double maxMel = 2595.0 * Math.log10(1.0 + (sampleRate / 2.0) / 700.0);
        int[] melFilterCenters = new int[melBins + 2];
        for (int i = 0; i < melBins + 2; i++) {
            double mel = minMel + ((double) i / (melBins + 1)) * (maxMel - minMel);
            double freq = 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0);
            melFilterCenters[i] = (int) Math.round((freq * fftSize) / sampleRate);
        }

        float[] real = new float[fftSize];
        float[] imag = new float[fftSize];
        float[] mag = new float[specSize];

        for (int f = 0; f < numFrames; f++) {
            int startIdx = f * hopSize;
            for (int i = 0; i < fftSize; i++) {
                real[i] = samples[startIdx + i] * window[i];
                imag[i] = 0.0f;
            }

            FastFFT.fft(real, imag);

            for (int k = 0; k < specSize; k++) {
                float r = real[k];
                float im = imag[k];
                mag[k] = (float) Math.sqrt(r * r + im * im);
            }

            // Apply Triangular Mel Filter Weights
            for (int m = 0; m < melBins; m++) {
                int left = melFilterCenters[m];
                int center = melFilterCenters[m + 1];
                int right = melFilterCenters[m + 2];
                float energy = 0.0f;

                for (int k = left; k < center && k < specSize; k++) {
                    float weight = (float) (k - left) / Math.max(1, center - left);
                    energy += mag[k] * weight;
                }
                for (int k = center; k <= right && k < specSize; k++) {
                    float weight = (float) (right - k) / Math.max(1, right - center);
                    energy += mag[k] * weight;
                }

                melSpec[f][m] = (float) Math.log(Math.max(1e-5f, energy));
            }
        }
        return melSpec;
    }
}