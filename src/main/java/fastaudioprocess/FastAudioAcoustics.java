package fastaudioprocess;

/**
 * Acoustic Feature Extraction and Voice Activity Recognition primitives powered by FastFFT.
 * <p>
 * Supplies essential mathematical metrics for Voice Activity Detectors (e.g. FastVAD),
 * wake-word classifiers, and audio event recognition models.
 * </p>
 */
public final class FastAudioAcoustics {

    private FastAudioAcoustics() {
    }

    /**
     * Computes the Spectral Crest Factor (Peak / RMS) of an audio frame.
     * High values (&gt; 4.0) identify sharp impulsive transient spikes (claps, clicks, cutlery clatter),
     * while typical speech and music exhibit moderate values (2.0 - 3.5).
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
     * @return normalized zero-crossing rate from 0.0 (DC / low-frequency) to 1.0 (Nyquist noise)
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
        for (int lag = minLag; lag <= maxL; lag += 2) {
            double sumLag = 0.0;
            double sumBase = 0.0;
            for (int i = 0; i < n - lag; i++) {
                sumLag += (samples[i] * samples[i + lag]);
                sumBase += (samples[i] * samples[i]);
            }
            if (sumBase > 1e-6) {
                double norm = sumLag / sumBase;
                if (norm > maxNormAutocorr) {
                    maxNormAutocorr = norm;
                }
            }
        }
        return (float) maxNormAutocorr;
    }

    /**
     * Generates a Log-Mel Spectrogram representation using O(N log N) FastFFT.
     * Maps linear FFT frequency bins onto Mel-frequency scale bins.
     *
     * @param samples    raw float audio samples
     * @param sampleRate sample rate in Hz
     * @param fftSize    window FFT size (e.g. 512)
     * @param hopSize    step size between windows (e.g. 160)
     * @param melBins    number of Mel frequency filter banks (e.g. 80)
     * @return 2D array of [numFrames][melBins] log-mel energies
     */
    public static float[][] logMelSpectrogram(float[] samples, int sampleRate, int fftSize, int hopSize, int melBins) {
        if (samples == null || samples.length < fftSize || fftSize <= 0 || hopSize <= 0 || melBins <= 0) {
            return new float[0][0];
        }
        int len = samples.length;
        int numFrames = (len - fftSize) / hopSize + 1;
        if (numFrames <= 0) return new float[0][0];

        float[][] melSpec = new float[numFrames][melBins];

        float[] window = new float[fftSize];
        for (int i = 0; i < fftSize; i++) {
            window[i] = (float) (0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (fftSize - 1))));
        }

        float[] real = new float[fftSize];
        float[] imag = new float[fftSize];
        int specSize = fftSize / 2 + 1;
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

            for (int m = 0; m < melBins; m++) {
                int centerLinear = kIndexForMel(m, sampleRate, fftSize, melBins);
                float energy = 0.0f;
                int width = Math.max(1, specSize / melBins);
                int startBin = Math.max(0, centerLinear - width);
                int endBin = Math.min(specSize - 1, centerLinear + width);
                for (int k = startBin; k <= endBin; k++) {
                    energy += mag[k];
                }
                melSpec[f][m] = (float) Math.log(Math.max(1e-5f, energy));
            }
        }
        return melSpec;
    }

    private static int kIndexForMel(int melBin, int sampleRate, int fftSize, int melBins) {
        double minMel = 0.0;
        double maxMel = 2595.0 * Math.log10(1.0 + (sampleRate / 2.0) / 700.0);
        double mel = minMel + ((double) melBin / melBins) * (maxMel - minMel);
        double freq = 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0);
        return (int) Math.round((freq * fftSize) / sampleRate);
    }
}