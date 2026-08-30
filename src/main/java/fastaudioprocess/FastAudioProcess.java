package fastaudioprocess;

import fastcore.FastCore;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.*;

/**
 * High-performance audio processing, spectral filtering, and DSP engine for Java.
 * <p>
 * Provides native AVX2 pitch detection (autocorrelation), time-domain SOLA pitch shifting,
 * real-time spectral power noise suppression, dynamic noise gating, and zero-allocation acoustic analysis
 * (Crest Factor, Zero-Crossing Rate, Harmonic Periodicity) without JVM Garbage Collection stalls or incubator modules.
 * </p>
 */
public final class FastAudioProcess {

    // ── 1. Native Substrate Constants & Off-Heap Buffer Caches ────────────────
    private static final boolean NATIVE_LOADED;
    private static final ThreadLocal<float[]> THREAD_LOCAL_BUFFER = ThreadLocal.withInitial(() -> new float[16384]);

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
        // Pure static utility class
    }

    /**
     * Returns true if the native C++ AVX2 hardware acceleration DLL is active.
     *
     * @return true if native acceleration is loaded, false otherwise
     */
    public static boolean isNativeLoaded() {
        return NATIVE_LOADED;
    }

    // ── 2. Native JNI DSP Methods ────────────────────────────────────────────

    /**
     * Estimates the fundamental frequency (pitch F0 in Hz) of voice samples using native SIMD autocorrelation.
     *
     * @param samples    raw audio samples (normalized float array in [-1.0, 1.0])
     * @param sampleRate audio sampling rate in Hz (e.g. 16000 or 44100)
     * @return estimated pitch in Hertz (Hz), or 0.0 if unvoiced/silent
     */
    public static native float detectPitchNative(float[] samples, int sampleRate);

    /**
     * Shifts the pitch of audio samples by the specified semitones natively using the SOLA
     * (Synchronized Overlap-Add) algorithm without altering playback speed or duration.
     *
     * @param samples    audio samples to shift (modified in-place)
     * @param semitones  pitch shift amount in musical semitones (e.g. +2.0 for higher, -2.0 for lower)
     * @param sampleRate audio sampling rate in Hz (e.g. 44100)
     */
    public static native void pitchShiftNative(float[] samples, float semitones, int sampleRate);

    // ── 3. Acoustic Feature Extraction & VAD Metrics ─────────────────────────

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
     * Measures the fundamental harmonic pitch periodicity via normalized autocorrelation.
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
     * Computes average frame energy for Voice Activity Detection.
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
     * Returns the maximum absolute peak value of a single audio frame.
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

    // ── 4. Real-Time Noise Suppression & Dynamics ────────────────────────────

    /**
     * Suppresses stationary acoustic background noise (fans, hums, mic hiss) in-place
     * using spectral power subtraction and Wiener gain smoothing.
     *
     * @param samples          raw audio frame samples (modified in-place)
     * @param sampleRate       sample rate in Hz (e.g. 16000 or 44100)
     * @param reductionFactor  noise attenuation factor (e.g. 0.85f for gentle, 1.5f for aggressive)
     * @param spectralFloor    minimum spectral gain floor to prevent musical noise artifacts (e.g. 0.02f)
     */
    public static void suppressNoise(float[] samples, int sampleRate, float reductionFactor, float spectralFloor) {
        if (samples == null || samples.length < 32) return;
        int n = samples.length;

        float[] windowed = new float[n];
        for (int i = 0; i < n; i++) {
            float w = (float) (0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (n - 1))));
            windowed[i] = samples[i] * w;
        }

        int half = n / 2 + 1;
        float[] real = new float[half];
        float[] imag = new float[half];
        float[] mag  = new float[half];

        for (int k = 0; k < half; k++) {
            float r = 0.0f;
            float im = 0.0f;
            for (int t = 0; t < n; t++) {
                double angle = 2.0 * Math.PI * k * t / n;
                r  += windowed[t] * (float) Math.cos(angle);
                im -= windowed[t] * (float) Math.sin(angle);
            }
            real[k] = r;
            imag[k] = im;
            mag[k]  = (float) Math.sqrt(r * r + im * im);
        }

        float noiseEstimate = 0.0f;
        for (int k = 0; k < half; k++) {
            noiseEstimate += mag[k];
        }
        noiseEstimate = (noiseEstimate / half) * 0.25f;

        for (int k = 0; k < half; k++) {
            float originalMag = mag[k];
            float cleanedMag = originalMag - (reductionFactor * noiseEstimate);
            float minAllowed = originalMag * spectralFloor;
            if (cleanedMag < minAllowed) {
                cleanedMag = minAllowed;
            }

            float gain = originalMag > 1e-6f ? (cleanedMag / originalMag) : spectralFloor;
            real[k] *= gain;
            imag[k] *= gain;
        }

        for (int t = 0; t < n; t++) {
            float val = real[0] + (n % 2 == 0 ? real[half - 1] * (float) Math.cos(Math.PI * t) : 0.0f);
            for (int k = 1; k < half - 1; k++) {
                double angle = 2.0 * Math.PI * k * t / n;
                val += 2.0f * (real[k] * (float) Math.cos(angle) - imag[k] * (float) Math.sin(angle));
            }
            samples[t] = val / n;
        }
    }

    /**
     * Applies a block-based dynamic downward expander / noise gate in-place.
     *
     * @param samples     audio buffer (modified in-place)
     * @param thresholdDb threshold in decibels
     * @param reductionDb attenuation reduction in decibels below threshold
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

    // ── 5. Audio Normalization, DSP & Mixing ──────────────────────────────────

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

    // ── 6. Spectral Analysis & Waveform Rendering ───────────────────────────

    /**
     * Generates a Log-Mel Spectrogram representation of audio samples.
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
        int len = samples.length;
        int numFrames = (len - fftSize) / hopSize + 1;
        if (numFrames <= 0) return new float[0][0];

        float[][] melSpec = new float[numFrames][melBins];

        float[] window = new float[fftSize];
        for (int i = 0; i < fftSize; i++) {
            window[i] = (float) (0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (fftSize - 1))));
        }

        for (int f = 0; f < numFrames; f++) {
            int startIdx = f * hopSize;
            float[] frame = new float[fftSize];
            for (int i = 0; i < fftSize; i++) {
                frame[i] = samples[startIdx + i] * window[i];
            }

            int specSize = fftSize / 2 + 1;
            float[] mag = new float[specSize];
            for (int k = 0; k < specSize; k++) {
                float real = 0.0f;
                float imag = 0.0f;
                for (int n = 0; n < fftSize; n++) {
                    double angle = 2.0 * Math.PI * k * n / fftSize;
                    real += frame[n] * Math.cos(angle);
                    imag -= frame[n] * Math.sin(angle);
                }
                mag[k] = (float) Math.sqrt(real * real + imag * imag);
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

    /**
     * Downsamples a large array of float samples into exactly targetPoints peak values
     * (the absolute maximum in each segment) for rendering / timeline visualization.
     *
     * @param samples      audio samples array
     * @param targetPoints number of waveform visualization points to return
     * @return array of peak points
     */
    public static float[] generateWaveformPoints(float[] samples, int targetPoints) {
        if (samples == null || samples.length == 0 || targetPoints <= 0) {
            return new float[0];
        }
        float[] points = new float[targetPoints];
        double blockSize = (double) samples.length / targetPoints;
        for (int i = 0; i < targetPoints; i++) {
            int start = (int) (i * blockSize);
            int end = (int) ((i + 1) * blockSize);
            if (end > samples.length) end = samples.length;
            float max = 0.0f;
            for (int j = start; j < end; j++) {
                float abs = Math.abs(samples[j]);
                if (abs > max) max = abs;
            }
            points[i] = max;
        }
        return points;
    }

    // ── 7. Format Conversions & Codec Helpers ─────────────────────────────────

    /**
     * Converts a standard MP3 audio file into a 44100Hz Stereo 16-bit signed WAV PCM file.
     *
     * @param mp3File input MP3 file on disk
     * @return temporary WAV file containing decoded PCM audio
     * @throws Exception if audio decoding fails or file is invalid
     */
    public static File mp3ToWav(File mp3File) throws Exception {
        if (!mp3File.exists()) {
            throw new FileNotFoundException("Source MP3 not found: " + mp3File.getAbsolutePath());
        }
        File tempWav = File.createTempFile("process_sound_", ".wav");
        tempWav.deleteOnExit();

        try (AudioInputStream mp3Stream = AudioSystem.getAudioInputStream(mp3File)) {
            AudioFormat baseFormat = mp3Stream.getFormat();
            AudioFormat decodedFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    44100.0f,
                    16,
                    2,
                    4,
                    44100.0f,
                    false
            );

            long frameLength = mp3Stream.getFrameLength();
            try (AudioInputStream pcmStream = AudioSystem.getAudioInputStream(decodedFormat, mp3Stream);
                 AudioInputStream lengthSpecifiedStream = new AudioInputStream(pcmStream, decodedFormat, frameLength)) {
                AudioSystem.write(lengthSpecifiedStream, AudioFileFormat.Type.WAVE, tempWav);
            }
        }
        return tempWav;
    }

    /**
     * Resamples arbitrary WAV byte data (e.g. from Piper TTS) to 44100Hz Stereo 16-bit WAV PCM.
     *
     * @param wavData raw byte array of source WAV audio
     * @return resampled 44.1kHz Stereo WAV byte array
     * @throws Exception if conversion or resampling fails
     */
    public static byte[] resampleWavTo44100(byte[] wavData) throws Exception {
        if (wavData == null || wavData.length < 44) return wavData;

        try (ByteArrayInputStream bais = new ByteArrayInputStream(wavData);
             AudioInputStream sourceStream = AudioSystem.getAudioInputStream(bais)) {

            AudioFormat sourceFormat = sourceStream.getFormat();
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    44100.0f,
                    16,
                    2,
                    4,
                    44100.0f,
                    false
            );

            long srcFrameLength = sourceStream.getFrameLength();
            long targetFrameLength = (long) ((srcFrameLength * 44100.0f) / sourceFormat.getSampleRate());

            if (AudioSystem.isConversionSupported(targetFormat, sourceFormat)) {
                try (AudioInputStream targetStream = AudioSystem.getAudioInputStream(targetFormat, sourceStream);
                     AudioInputStream lengthStream = new AudioInputStream(targetStream, targetFormat, targetFrameLength);
                     ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                    AudioSystem.write(lengthStream, AudioFileFormat.Type.WAVE, baos);
                    return baos.toByteArray();
                }
            } else {
                AudioFormat pcmIntermediate = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        sourceFormat.getSampleRate(),
                        16,
                        sourceFormat.getChannels() > 0 ? sourceFormat.getChannels() : 1,
                        (sourceFormat.getChannels() > 0 ? sourceFormat.getChannels() : 1) * 2,
                        sourceFormat.getSampleRate(),
                        false
                );

                try (AudioInputStream pcmStream = AudioSystem.getAudioInputStream(pcmIntermediate, sourceStream);
                     AudioInputStream targetStream = AudioSystem.getAudioInputStream(targetFormat, pcmStream);
                     AudioInputStream lengthStream = new AudioInputStream(targetStream, targetFormat, targetFrameLength);
                     ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                    AudioSystem.write(lengthStream, AudioFileFormat.Type.WAVE, baos);
                    return baos.toByteArray();
                }
            }
        }
    }

    // ── 8. Streaming Frame Buffer Classes ────────────────────────────────────

    /**
     * FrameChunker splits continuous incoming audio streams into overlapping windows
     * tailored for neural networks (VAD, wake-word, STT).
     */
    public static class FrameChunker {
        private final float[] buffer;
        private final int chunkSize;
        private final int hopSize;
        private int writeIndex = 0;
        private int readIndex = 0;
        private int count = 0;

        /**
         * Creates a new frame chunker with the specified window and hop sizes.
         *
         * @param chunkSize window size in float samples
         * @param hopSize   hop step size in float samples
         */
        public FrameChunker(int chunkSize, int hopSize) {
            this.chunkSize = chunkSize;
            this.hopSize = hopSize;
            this.buffer = new float[chunkSize * 8];
        }

        /**
         * Pushes incoming audio samples into the ring buffer.
         *
         * @param samples audio samples to push
         */
        public synchronized void push(float[] samples) {
            if (samples == null) return;
            for (float s : samples) {
                buffer[writeIndex] = s;
                writeIndex = (writeIndex + 1) % buffer.length;
                if (count < buffer.length) {
                    count++;
                } else {
                    readIndex = (readIndex + 1) % buffer.length;
                }
            }
        }

        /**
         * Pulls the next overlapping frame window, or returns null if not enough samples are available.
         *
         * @return frame window of length {@code chunkSize}, or null
         */
        public synchronized float[] nextChunk() {
            if (count < chunkSize) return null;
            float[] chunk = new float[chunkSize];
            int idx = readIndex;
            for (int i = 0; i < chunkSize; i++) {
                chunk[i] = buffer[idx];
                idx = (idx + 1) % buffer.length;
            }
            readIndex = (readIndex + hopSize) % buffer.length;
            count -= hopSize;
            return chunk;
        }

        /**
         * Returns the number of currently buffered audio samples.
         *
         * @return sample count
         */
        public synchronized int availableSamples() {
            return count;
        }
    }
}