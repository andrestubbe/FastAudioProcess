package fastaudioprocess;

import fastcore.FastCore;

/**
 * Core DSP audio processing and time-domain manipulation engine.
 * <p>
 * Provides native AVX2 pitch detection (autocorrelation), time-domain SOLA pitch shifting,
 * RMS energy calculation, multi-channel mixing, amplitude normalization, and 3-band EQ filtering.
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
        // Static utility class
    }

    /**
     * Returns true if the native C++ AVX2 hardware acceleration DLL is active.
     *
     * @return true if native acceleration is loaded, false otherwise
     */
    public static boolean isNativeLoaded() {
        return NATIVE_LOADED;
    }

    /**
     * Estimates the fundamental frequency (pitch F0 in Hz) using native SIMD autocorrelation.
     *
     * @param samples    raw audio samples in [-1.0, 1.0]
     * @param sampleRate audio sampling rate in Hz (e.g. 16000 or 44100)
     * @return estimated pitch in Hertz (Hz), or 0.0 if unvoiced/silent
     */
    public static native float detectPitchNative(float[] samples, int sampleRate);

    /**
     * Shifts pitch of audio samples natively using SOLA without altering playback duration.
     *
     * @param samples    audio samples to shift (modified in-place)
     * @param semitones  pitch shift in musical semitones (+/-)
     * @param sampleRate audio sampling rate in Hz
     */
    public static native void pitchShiftNative(float[] samples, float semitones, int sampleRate);

    /**
     * Computes Root Mean Square (RMS) energy level of a byte buffer.
     *
     * @param buffer    raw 16-bit PCM bytes
     * @param bytesRead total valid bytes in buffer
     * @return normalized RMS volume level in range [0.0, 1.0]
     */
    public static float computeRms(byte[] buffer, int bytesRead) {
        return computeRms(buffer, 0, bytesRead);
    }

    /**
     * Computes Root Mean Square (RMS) volume level of a PCM audio buffer from offset.
     *
     * @param buffer raw 16-bit PCM bytes
     * @param offset starting byte offset
     * @param length byte length to process
     * @return normalized RMS volume level in range [0.0, 1.0]
     */
    public static float computeRms(byte[] buffer, int offset, int length) {
        int count = length / 2;
        if (count == 0) return 0f;

        double sum = 0.0;
        for (int i = 0; i < count; i++) {
            int byteIndex = offset + (i * 2);
            short sample = (short) ((buffer[byteIndex + 1] << 8) | (buffer[byteIndex] & 0xff));
            sum += (double) sample * (double) sample;
        }

        return (float) (Math.sqrt(sum / count) / 32768.0);
    }

    /**
     * Computes average frame energy from short samples.
     *
     * @param samples 16-bit PCM short array
     * @param offset  start index
     * @param length  number of samples
     * @return normalized mean square energy in [0.0, 1.0]
     */
    public static float computeFrameEnergy(short[] samples, int offset, int length) {
        if (samples == null || length <= 0) return 0.0f;
        double sum = 0.0;
        for (int i = 0; i < length; i++) {
            double val = samples[offset + i] / 32768.0;
            sum += val * val;
        }
        return (float) (sum / length);
    }

    /**
     * Returns the maximum absolute peak value of an audio frame.
     *
     * @param samples audio frame samples
     * @return peak absolute value in range [0.0, 1.0+]
     */
    public static float getFramePeak(float[] samples) {
        if (samples == null || samples.length == 0) return 0.0f;
        float max = 0.0f;
        for (float s : samples) {
            float abs = Math.abs(s);
            if (abs > max) max = abs;
        }
        return max;
    }

    /**
     * Normalizes amplitude of audio samples in-place so peak reaches targetPeak.
     *
     * @param samples    audio buffer (modified in-place)
     * @param targetPeak target absolute peak value (e.g. 0.95f)
     */
    public static void normalize(float[] samples, float targetPeak) {
        if (samples == null || samples.length == 0 || targetPeak <= 0) return;
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

    /**
     * Applies a high-pass pre-emphasis filter to the audio samples in-place.
     * Formula: y[n] = x[n] - factor * x[n-1]
     *
     * @param samples audio buffer (modified in-place)
     * @param factor  pre-emphasis coefficient (typically 0.95 - 0.97)
     */
    public static void preEmphasis(float[] samples, float factor) {
        if (samples == null || samples.length <= 1) return;
        for (int i = samples.length - 1; i > 0; i--) {
            samples[i] = samples[i] - factor * samples[i - 1];
        }
    }

    /**
     * Mixes multiple audio channels using weights.
     *
     * @param channels 2D array of [numChannels][sampleCount]
     * @param weights  mixing weight factors per channel
     * @return blended audio array
     */
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

    /**
     * Resamples audio samples linearly to shift pitch by specified semitones.
     *
     * @param samples   input audio samples
     * @param semitones semitones shift (+/-)
     * @return resampled audio array
     */
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

    /**
     * Real-time 3-band Equalizer utilizing Low-pass and High-pass crossover filters.
     *
     * @param samples      audio buffer (modified in-place)
     * @param bassGainDb   bass band gain in dB
     * @param midGainDb    midrange band gain in dB
     * @param trebleGainDb treble band gain in dB
     */
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
}