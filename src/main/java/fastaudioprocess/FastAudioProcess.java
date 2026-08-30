package fastaudioprocess;

import fastcore.FastCore;
import java.io.File;

/**
 * High-Performance Hardware-Accelerated Audio Processing and DSP Engine for Java.
 * <p>
 * Unifies native AVX2 SIMD pitch detection, SOLA pitch modulation, real-time FastFFT noise cancellation,
 * and zero-allocation acoustic analysis into a clean, cohesive facade.
 * </p>
 */
public final class FastAudioProcess {

    private static final boolean NATIVE_LOADED;

    static {
        boolean loaded = false;
        try {
            FastCore.loadLibrary("fastaudioprocess", FastAudioProcess.class);
            loaded = true;
        } catch (Throwable t) {
            loaded = false;
        }
        NATIVE_LOADED = loaded;
    }

    private FastAudioProcess() {
    }

    public static boolean isNativeLoaded() {
        return NATIVE_LOADED;
    }

    // ── Native DSP Methods ───────────────────────────────────────────────────

    public static native float detectPitchNative(float[] samples, int sampleRate);

    public static native void pitchShiftNative(float[] samples, float semitones, int sampleRate);

    // ── RMS & Energy Measurement ─────────────────────────────────────────────

    public static float computeRms(byte[] buffer, int bytesRead) {
        return computeRms(buffer, 0, bytesRead);
    }

    public static float computeRms(byte[] buffer, int offset, int length) {
        if (buffer == null || length <= 0) return 0.0f;
        int count = length / 2;
        if (count == 0) return 0.0f;

        double sum = 0.0;
        for (int i = 0; i < count; i++) {
            int byteIndex = offset + (i * 2);
            short sample = (short) ((buffer[byteIndex + 1] << 8) | (buffer[byteIndex] & 0xff));
            sum += (double) sample * (double) sample;
        }

        return (float) (Math.sqrt(sum / count) / 32768.0);
    }

    public static float computeFrameEnergy(short[] samples, int offset, int length) {
        if (samples == null || length <= 0) return 0.0f;
        double sum = 0.0;
        for (int i = 0; i < length; i++) {
            double val = samples[offset + i] / 32768.0;
            sum += val * val;
        }
        return (float) (sum / length);
    }

    public static float getFramePeak(float[] samples) {
        if (samples == null || samples.length == 0) return 0.0f;
        float max = 0.0f;
        for (float s : samples) {
            float abs = Math.abs(s);
            if (abs > max) max = abs;
        }
        return max;
    }

    // ── DSP Filters & Vector Operations ──────────────────────────────────────

    public static void normalize(float[] samples, float targetPeak) {
        if (samples == null || samples.length == 0 || targetPeak <= 0.0f) return;
        float maxVal = 0.0f;
        for (float s : samples) {
            float abs = Math.abs(s);
            if (abs > maxVal) maxVal = abs;
        }
        if (maxVal == 0.0f) return;

        float scale = targetPeak / maxVal;
        for (int i = 0; i < samples.length; i++) {
            samples[i] *= scale;
        }
    }

    public static void preEmphasis(float[] samples, float factor) {
        if (samples == null || samples.length <= 1) return;
        for (int i = samples.length - 1; i > 0; i--) {
            samples[i] = samples[i] - factor * samples[i - 1];
        }
    }

    public static float[] mixChannels(float[][] channels, float[] weights) {
        if (channels == null || channels.length == 0) return new float[0];
        int numChannels = channels.length;
        int len = channels[0].length;
        float[] output = new float[len];

        for (int i = 0; i < len; i++) {
            float sum = 0.0f;
            for (int c = 0; c < numChannels; c++) {
                float w = (weights != null && c < weights.length) ? weights[c] : 1.0f / numChannels;
                sum += channels[c][i] * w;
            }
            output[i] = sum;
        }

        return output;
    }

    public static float[] pitchShiftResample(float[] samples, float semitones) {
        if (samples == null || samples.length == 0 || semitones == 0.0f) return samples;
        double factor = Math.pow(2.0, semitones / 12.0);
        int newLen = (int) (samples.length / factor);
        float[] output = new float[newLen];
        for (int i = 0; i < newLen; i++) {
            double srcIndex = i * factor;
            int base = (int) srcIndex;
            double frac = srcIndex - base;
            if (base < samples.length - 1) {
                output[i] = (float) ((1.0 - frac) * samples[base] + frac * samples[base + 1]);
            } else {
                output[i] = samples[samples.length - 1];
            }
        }
        return output;
    }

    public static void apply3BandEqualizer(float[] samples, float bassGainDb, float midGainDb, float trebleGainDb) {
        if (samples == null || samples.length == 0) return;
        float bassGain = (float) Math.pow(10.0, bassGainDb / 20.0);
        float midGain = (float) Math.pow(10.0, midGainDb / 20.0);
        float trebleGain = (float) Math.pow(10.0, trebleGainDb / 20.0);

        float lp = 0.0f;
        float hp = 0.0f;
        float alphaL = 0.15f; 
        float alphaH = 0.75f; 

        for (int i = 0; i < samples.length; i++) {
            float input = samples[i];
            lp = lp + alphaL * (input - lp);
            float bass = lp;
            hp = alphaH * (hp + input - (i > 0 ? samples[i-1] : input));
            float treble = hp;
            float mid = input - bass - treble;
            samples[i] = bass * bassGain + mid * midGain + treble * trebleGain;
        }
    }

    // ── Clean API Delegation Methods for 100% Usability ──────────────────────

    public static void suppressNoise(float[] samples, int sampleRate, float reductionFactor, float spectralFloor) {
        FastAudioDenoise.suppressNoise(samples, sampleRate, reductionFactor, spectralFloor);
    }

    public static void applyNoiseGate(float[] samples, float thresholdDb, float reductionDb) {
        FastAudioDenoise.applyNoiseGate(samples, thresholdDb, reductionDb);
    }

    public static float computeCrestFactor(float[] samples) {
        return FastAudioAcoustics.computeCrestFactor(samples);
    }

    public static float computeZeroCrossingRate(float[] samples) {
        return FastAudioAcoustics.computeZeroCrossingRate(samples);
    }

    public static float computeAutocorrelationPeriodicity(float[] samples, int minLag, int maxLag) {
        return FastAudioAcoustics.computeAutocorrelationPeriodicity(samples, minLag, maxLag);
    }

    public static float[][] logMelSpectrogram(float[] samples, int sampleRate, int fftSize, int hopSize, int melBins) {
        return FastAudioAcoustics.logMelSpectrogram(samples, sampleRate, fftSize, hopSize, melBins);
    }

    public static File mp3ToWav(File mp3File) throws Exception {
        return FastAudioCodec.mp3ToWav(mp3File);
    }

    public static byte[] resampleWavTo44100(byte[] wavData) throws Exception {
        return FastAudioCodec.resampleWavTo44100(wavData);
    }

    public static float[] generateWaveformPoints(float[] samples, int targetPoints) {
        return FastAudioCodec.generateWaveformPoints(samples, targetPoints);
    }

    /**
     * Backward-compatible alias for FastAudioChunker.
     */
    public static final class FrameChunker {
        private final FastAudioChunker delegate;

        public FrameChunker(int chunkSize, int hopSize) {
            this.delegate = new FastAudioChunker(chunkSize, hopSize);
        }

        public void push(float[] samples) {
            delegate.push(samples);
        }

        public boolean nextChunk(float[] destination) {
            return delegate.nextChunk(destination);
        }

        public float[] nextChunk() {
            return delegate.nextChunk();
        }

        public void reset() {
            delegate.reset();
        }

        public int availableSamples() {
            return delegate.availableSamples();
        }
    }
}