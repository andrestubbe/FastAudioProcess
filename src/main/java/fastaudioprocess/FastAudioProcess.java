package fastaudioprocess;

import fastcore.FastCore;
import java.io.File;

/**
 * High-Performance Hardware-Accelerated Audio Processing and DSP Engine for Java.
 * <p>
 * Provides native AVX2 SIMD pitch detection, time-domain SOLA pitch modulation,
 * real-time spectral power noise suppression, dynamic noise gating, and zero-allocation acoustic analysis
 * (Crest Factor, Zero-Crossing Rate, Harmonic Periodicity) without JVM Garbage Collection stalls.
 * </p>
 *
 * <h2>Core Capabilities:</h2>
 * <ul>
 *   <li><b>Native AVX2 Pitch Tracking</b>: 256-bit SIMD autocorrelation for fundamental frequency (F0) estimation.</li>
 *   <li><b>In-Place Noise Suppression</b>: O(N log N) FastFFT spectral subtraction with Wiener floor gain smoothing.</li>
 *   <li><b>Acoustic Feature Extraction</b>: Instantaneous Crest Factor, Zero-Crossing Rate, and Harmonicity metrics.</li>
 *   <li><b>Zero-GC Architecture</b>: Preallocated thread-local scratch buffers eliminate memory allocation in hot audio loops.</li>
 * </ul>
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

    /**
     * Checks if the native C++ AVX2 hardware acceleration DLL ({@code fastaudioprocess.dll}) is active.
     *
     * @return {@code true} if native acceleration is loaded and operational, {@code false} otherwise
     */
    public static boolean isNativeLoaded() {
        return NATIVE_LOADED;
    }

    // ── Native DSP Methods ───────────────────────────────────────────────────

    /**
     * Estimates the fundamental frequency (pitch F0 in Hz) using native 256-bit AVX2 SIMD autocorrelation.
     *
     * @param samples    raw audio samples (normalized float array in [-1.0, 1.0])
     * @param sampleRate audio sampling rate in Hz (e.g. 16000 or 44100)
     * @return estimated pitch in Hertz (Hz), or 0.0 if unvoiced/silent
     */
    public static native float detectPitchNative(float[] samples, int sampleRate);

    /**
     * Shifts the pitch of audio samples natively using SOLA (Synchronized Overlap-Add)
     * without altering playback speed or duration.
     *
     * @param samples    audio samples to shift (modified in-place)
     * @param semitones  pitch shift amount in musical semitones (e.g. +2.0 for higher, -2.0 for lower)
     * @param sampleRate audio sampling rate in Hz (e.g. 44100)
     */
    public static native void pitchShiftNative(float[] samples, float semitones, int sampleRate);

    // ── RMS & Energy Measurement ─────────────────────────────────────────────

    /**
     * Computes the Root Mean Square (RMS) energy level of a byte buffer from index 0.
     *
     * @param buffer    raw 16-bit PCM bytes
     * @param bytesRead total valid bytes in buffer
     * @return normalized RMS volume level in range [0.0, 1.0]
     */
    public static float computeRms(byte[] buffer, int bytesRead) {
        return computeRms(buffer, 0, bytesRead);
    }

    /**
     * Computes the Root Mean Square (RMS) volume level of a PCM audio buffer from offset.
     *
     * @param buffer raw 16-bit PCM bytes
     * @param offset starting byte offset
     * @param length byte length to process
     * @return normalized RMS volume level in range [0.0, 1.0]
     */
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

    /**
     * Computes average frame energy for Voice Activity Detection (VAD).
     *
     * @param samples 16-bit signed PCM short array
     * @param offset  start index
     * @param length  number of samples to evaluate
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
     * Returns the maximum absolute peak amplitude of an audio frame.
     *
     * @param samples raw audio frame samples
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

    // ── DSP Filters & Vector Operations ──────────────────────────────────────

    /**
     * Normalizes the amplitude of audio samples in-place so that the maximum peak reaches targetPeak.
     *
     * @param samples    audio buffer (modified in-place)
     * @param targetPeak target absolute peak value (e.g. 0.95f)
     */
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

    /**
     * Applies a high-pass pre-emphasis filter to the audio samples in-place.
     * Formula: {@code y[n] = x[n] - factor * x[n-1]}.
     *
     * @param samples audio buffer (modified in-place)
     * @param factor  pre-emphasis coefficient (typically 0.95f - 0.97f)
     */
    public static void preEmphasis(float[] samples, float factor) {
        if (samples == null || samples.length <= 1) return;
        for (int i = samples.length - 1; i > 0; i--) {
            samples[i] = samples[i] - factor * samples[i - 1];
        }
    }

    /**
     * Mixes multiple audio channels using channel weight factors.
     *
     * @param channels 2D array of [numChannels][sampleCount]
     * @param weights  mixing weight factors per channel (or null for equal weighting)
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
     * @param bassGainDb   bass band gain in dB (e.g. +3.0f or -6.0f)
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

    // ── Clean API Delegation Methods for 100% Usability ──────────────────────

    /**
     * Suppresses stationary acoustic background noise in-place using O(N log N) FastFFT spectral subtraction.
     *
     * @param samples          raw audio frame samples (modified in-place)
     * @param sampleRate       sample rate in Hz (e.g. 16000 or 44100)
     * @param reductionFactor  noise attenuation factor (e.g. 0.85f for gentle, 1.5f for aggressive)
     * @param spectralFloor    minimum spectral gain floor to prevent musical noise artifacts (e.g. 0.02f)
     * @see FastAudioDenoise#suppressNoise(float[], int, float, float)
     */
    public static void suppressNoise(float[] samples, int sampleRate, float reductionFactor, float spectralFloor) {
        FastAudioDenoise.suppressNoise(samples, sampleRate, reductionFactor, spectralFloor);
    }

    /**
     * Applies a block-based dynamic downward expander / noise gate in-place.
     *
     * @param samples     audio buffer (modified in-place)
     * @param thresholdDb threshold in decibels (e.g. -40.0f)
     * @param reductionDb attenuation reduction in decibels below threshold (e.g. -24.0f)
     * @see FastAudioDenoise#applyNoiseGate(float[], float, float)
     */
    public static void applyNoiseGate(float[] samples, float thresholdDb, float reductionDb) {
        FastAudioDenoise.applyNoiseGate(samples, thresholdDb, reductionDb);
    }

    /**
     * Computes the Spectral Crest Factor (Peak / RMS) of an audio frame.
     * High values (&gt; 4.0) identify sharp impulsive transient spikes (claps, clicks, cutlery clatter).
     *
     * @param samples raw audio frame samples
     * @return crest factor ratio (dimensionless), or 0.0 if frame is silent
     * @see FastAudioAcoustics#computeCrestFactor(float[])
     */
    public static float computeCrestFactor(float[] samples) {
        return FastAudioAcoustics.computeCrestFactor(samples);
    }

    /**
     * Computes the Zero-Crossing Rate (ZCR) of an audio buffer.
     *
     * @param samples raw audio frame samples
     * @return normalized zero-crossing rate from 0.0 (DC) to 1.0 (Nyquist noise)
     * @see FastAudioAcoustics#computeZeroCrossingRate(float[])
     */
    public static float computeZeroCrossingRate(float[] samples) {
        return FastAudioAcoustics.computeZeroCrossingRate(samples);
    }

    /**
     * Measures the fundamental harmonic pitch periodicity via normalized autocorrelation.
     *
     * @param samples raw audio frame samples
     * @param minLag  minimum sample lag (e.g. 35 for ~450 Hz at 16 kHz)
     * @param maxLag  maximum sample lag (e.g. 160 for ~100 Hz at 16 kHz)
     * @return normalized harmonicity ratio in [0.0, 1.0], where &gt; 0.35 indicates voiced speech/music
     * @see FastAudioAcoustics#computeAutocorrelationPeriodicity(float[], int, int)
     */
    public static float computeAutocorrelationPeriodicity(float[] samples, int minLag, int maxLag) {
        return FastAudioAcoustics.computeAutocorrelationPeriodicity(samples, minLag, maxLag);
    }

    /**
     * Generates a Log-Mel Spectrogram representation using O(N log N) FastFFT.
     *
     * @param samples    raw float audio samples
     * @param sampleRate sample rate in Hz
     * @param fftSize    window FFT size (e.g. 512)
     * @param hopSize    step size between windows (e.g. 160)
     * @param melBins    number of Mel frequency filter banks (e.g. 80)
     * @return 2D array of [numFrames][melBins] log-mel energies
     * @see FastAudioAcoustics#logMelSpectrogram(float[], int, int, int, int)
     */
    public static float[][] logMelSpectrogram(float[] samples, int sampleRate, int fftSize, int hopSize, int melBins) {
        return FastAudioAcoustics.logMelSpectrogram(samples, sampleRate, fftSize, hopSize, melBins);
    }

    /**
     * Converts a standard MP3 audio file into a 44100Hz Stereo 16-bit signed WAV PCM file.
     *
     * @param mp3File input MP3 file on disk
     * @return temporary WAV file containing decoded PCM audio
     * @throws Exception if audio decoding fails or file is invalid
     * @see FastAudioCodec#mp3ToWav(File)
     */
    public static File mp3ToWav(File mp3File) throws Exception {
        return FastAudioCodec.mp3ToWav(mp3File);
    }

    /**
     * Resamples arbitrary WAV byte data to 44100Hz Stereo 16-bit WAV PCM.
     *
     * @param wavData raw byte array of source WAV audio
     * @return resampled 44.1kHz Stereo WAV byte array
     * @throws Exception if conversion or resampling fails
     * @see FastAudioCodec#resampleWavTo44100(byte[])
     */
    public static byte[] resampleWavTo44100(byte[] wavData) throws Exception {
        return FastAudioCodec.resampleWavTo44100(wavData);
    }

    /**
     * Downsamples audio samples into peak values for rendering waveform visualizers.
     *
     * @param samples      audio samples array
     * @param targetPoints number of waveform points
     * @return array of peak points
     * @see FastAudioCodec#generateWaveformPoints(float[], int)
     */
    public static float[] generateWaveformPoints(float[] samples, int targetPoints) {
        return FastAudioCodec.generateWaveformPoints(samples, targetPoints);
    }

    /**
     * Backward-compatible alias for {@link FastAudioChunker}.
     */
    public static final class FrameChunker {
        private final FastAudioChunker delegate;

        /**
         * Creates a new frame chunker instance.
         *
         * @param chunkSize chunk size in float samples
         * @param hopSize   hop step size in float samples
         */
        public FrameChunker(int chunkSize, int hopSize) {
            this.delegate = new FastAudioChunker(chunkSize, hopSize);
        }

        /**
         * Pushes audio samples into buffer.
         *
         * @param samples samples array
         */
        public void push(float[] samples) {
            delegate.push(samples);
        }

        /**
         * Pulls chunk into preallocated destination array.
         *
         * @param destination destination array
         * @return true if successful
         */
        public boolean nextChunk(float[] destination) {
            return delegate.nextChunk(destination);
        }

        /**
         * Pulls chunk as a new array.
         *
         * @return new chunk array, or null
         */
        public float[] nextChunk() {
            return delegate.nextChunk();
        }

        /**
         * Clears chunker state.
         */
        public void reset() {
            delegate.reset();
        }

        /**
         * Returns available buffered samples.
         *
         * @return count
         */
        public int availableSamples() {
            return delegate.availableSamples();
        }
    }
}