package fastaudioprocess;

import java.io.*;
import javax.sound.sampled.*;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorSpecies;

/**
 * FastAudioProcess - High-performance audio processing, format conversions, and resampling.
 */
public final class FastAudioProcess {

    private static final String LIBRARY_NAME = "fastaudioprocess";
    static {
        loadNativeLibrary();
    }

    private static void loadNativeLibrary() {
        try {
            System.loadLibrary(LIBRARY_NAME);
        } catch (UnsatisfiedLinkError e) {
            try {
                String libResource = "/native/" + LIBRARY_NAME + ".dll";
                try (InputStream in = FastAudioProcess.class.getResourceAsStream(libResource)) {
                    if (in == null) {
                        libResource = "/" + LIBRARY_NAME + ".dll";
                        try (InputStream in2 = FastAudioProcess.class.getResourceAsStream(libResource)) {
                            if (in2 != null) {
                                loadFromStream(in2);
                            }
                        }
                    } else {
                        loadFromStream(in);
                    }
                }
            } catch (Exception ex) {
                System.err.println("Note: Native fastaudioprocess library could not be loaded: " + ex.getMessage());
            }
        }
    }

    private static void loadFromStream(InputStream in) throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("fastaudioprocess");
        java.nio.file.Path tempLib = tempDir.resolve("fastaudioprocess.dll");
        java.nio.file.Files.copy(in, tempLib, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        tempLib.toFile().deleteOnExit();
        tempDir.toFile().deleteOnExit();
        System.load(tempLib.toString());
    }

    /**
     * Estimates the fundamental frequency (pitch) of voice samples using native autocorrelation.
     */
    public static native float detectPitchNative(float[] samples, int sampleRate);

    /**
     * Shifts the pitch of the samples by the specified semitones natively without changing the speed/duration (using SOLA).
     */
    public static native void pitchShiftNative(float[] samples, float semitones, int sampleRate);


    /**
     * Converts a standard MP3 file to 44100Hz Stereo 16-bit WAV PCM format.
     */
    public static File mp3ToWav(File mp3File) throws Exception {
        if (!mp3File.exists()) {
            throw new FileNotFoundException("Source MP3 not found: " + mp3File.getAbsolutePath());
        }
        File tempWav = File.createTempFile("process_sound_", ".wav");
        tempWav.deleteOnExit();

        try (AudioInputStream mp3Stream = AudioSystem.getAudioInputStream(mp3File)) {
            AudioFormat baseFormat = mp3Stream.getFormat();
            // Target format: Standard 44100Hz, 16-bit, Stereo, Signed PCM
            AudioFormat decodedFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                44100.0f,
                16,
                2,
                4,
                44100.0f,
                false
            );

            // Specify frame length to avoid "stream length not specified" error
            long frameLength = mp3Stream.getFrameLength();
            try (AudioInputStream pcmStream = AudioSystem.getAudioInputStream(decodedFormat, mp3Stream);
                 AudioInputStream lengthSpecifiedStream = new AudioInputStream(pcmStream, decodedFormat, frameLength)) {
                AudioSystem.write(lengthSpecifiedStream, AudioFileFormat.Type.WAVE, tempWav);
            }
        }
        return tempWav;
    }

    /**
     * Resamples any arbitrary WAV byte array (like Piper outputs) to 44100Hz Stereo 16-bit WAV.
     */
    public static byte[] resampleWavTo44100(byte[] wavData) throws Exception {
        if (wavData == null || wavData.length < 44) return wavData;

        try (ByteArrayInputStream bais = new ByteArrayInputStream(wavData);
             AudioInputStream sourceStream = AudioSystem.getAudioInputStream(bais)) {
             
            AudioFormat sourceFormat = sourceStream.getFormat();
            
            // Standard target format
            AudioFormat targetFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                44100.0f,
                16,
                2,
                4,
                44100.0f,
                false
            );

            // Compute target frame length: (sourceFrameLength * targetSampleRate) / sourceSampleRate
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

    private static final VectorSpecies<Float> SPECIES = jdk.incubator.vector.FloatVector.SPECIES_PREFERRED;
    private static final ThreadLocal<float[]> THREAD_LOCAL_BUFFER = ThreadLocal.withInitial(() -> new float[16384]);

    public static float computeRms(byte[] buffer, int bytesRead) {
        return computeRms(buffer, 0, bytesRead);
    }

    /**
     * Computes the Root Mean Square (RMS) volume level of a PCM audio buffer from offset.
     * Accelerated using the Java 17 Vector API (SIMD).
     */
    public static float computeRms(byte[] buffer, int offset, int length) {
        int count = length / 2;
        if (count == 0) return 0f;

        float[] samples = THREAD_LOCAL_BUFFER.get();
        if (samples.length < count) {
            samples = new float[count * 2];
            THREAD_LOCAL_BUFFER.set(samples);
        }

        for (int i = 0; i < count; i++) {
            int byteIndex = offset + (i * 2);
            samples[i] = (short) ((buffer[byteIndex + 1] << 8) | (buffer[byteIndex] & 0xff));
        }

        int i = 0;
        int limit = count - (count % SPECIES.length());
        
        jdk.incubator.vector.FloatVector sumVector = jdk.incubator.vector.FloatVector.zero(SPECIES);
        
        // SIMD vector loop: process N samples at a time
        for (; i < limit; i += SPECIES.length()) {
            jdk.incubator.vector.FloatVector v = jdk.incubator.vector.FloatVector.fromArray(SPECIES, samples, i);
            sumVector = sumVector.add(v.mul(v));
        }
        
        // Reduce the SIMD vector to a single scalar sum
        float sum = sumVector.reduceLanes(jdk.incubator.vector.VectorOperators.ADD);
        
        // Scalar tail loop for remaining elements
        for (; i < count; i++) {
            sum += samples[i] * samples[i];
        }
        
        return (float) (Math.sqrt((double) sum / count) / 32768.0);
    }

    /**
     * Applies a high-pass pre-emphasis filter to the audio samples in-place.
     * Formula: y[n] = x[n] - factor * x[n-1]
     */
    public static void preEmphasis(float[] samples, float factor) {
        if (samples == null || samples.length <= 1) return;
        for (int i = samples.length - 1; i > 0; i--) {
            samples[i] = samples[i] - factor * samples[i - 1];
        }
    }

    /**
     * Normalizes the amplitude of the audio samples in-place so that the peak reaches targetPeak.
     * Accelerated using the Java 17 Vector API (SIMD).
     */
    public static void normalize(float[] samples, float targetPeak) {
        if (samples == null || samples.length == 0 || targetPeak <= 0) return;
        int len = samples.length;
        
        // Find absolute maximum peak
        float maxVal = 0.0f;
        int i = 0;
        int limit = len - (len % SPECIES.length());
        
        jdk.incubator.vector.FloatVector maxVector = jdk.incubator.vector.FloatVector.zero(SPECIES);
        for (; i < limit; i += SPECIES.length()) {
            jdk.incubator.vector.FloatVector v = jdk.incubator.vector.FloatVector.fromArray(SPECIES, samples, i);
            maxVector = maxVector.max(v.abs());
        }
        
        maxVal = maxVector.reduceLanes(jdk.incubator.vector.VectorOperators.MAX);
        for (; i < len; i++) {
            float absVal = Math.abs(samples[i]);
            if (absVal > maxVal) {
                maxVal = absVal;
            }
        }
        
        if (maxVal == 0.0f) return;
        
        // Scale elements using Vector multiplication
        float scale = targetPeak / maxVal;
        jdk.incubator.vector.FloatVector scaleVector = jdk.incubator.vector.FloatVector.broadcast(SPECIES, scale);
        
        i = 0;
        for (; i < limit; i += SPECIES.length()) {
            jdk.incubator.vector.FloatVector v = jdk.incubator.vector.FloatVector.fromArray(SPECIES, samples, i);
            v.mul(scaleVector).intoArray(samples, i);
        }
        for (; i < len; i++) {
            samples[i] *= scale;
        }
    }

    /**
     * Computes the average frame energy for Voice Activity Detection (VAD).
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
     * Generates a Log-Mel Spectrogram representation of the audio samples.
     * Maps linear FFT frequency bins onto Mel-frequency scale bins.
     */
    public static float[][] logMelSpectrogram(float[] samples, int sampleRate, int fftSize, int hopSize, int melBins) {
        int len = samples.length;
        int numFrames = (len - fftSize) / hopSize + 1;
        if (numFrames <= 0) return new float[0][0];
        
        float[][] melSpec = new float[numFrames][melBins];
        
        // Compute Hann window
        float[] window = new float[fftSize];
        for (int i = 0; i < fftSize; i++) {
            window[i] = (float) (0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (fftSize - 1))));
        }
        
        // DFT and Mel filtering
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
     * Resamples the audio samples to shift the pitch by the specified semitones.
     * Positive semitones speed up and raise pitch, negative slow down and lower pitch.
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
     * Mixes multiple audio channels using weights.
     * Accelerated using the Java 17 Vector API (SIMD).
     */
    public static float[] mixChannels(float[][] channels, float[] weights) {
        if (channels == null || channels.length == 0) return new float[0];
        int numChannels = channels.length;
        int len = channels[0].length;
        float[] output = new float[len];
        
        int i = 0;
        int limit = len - (len % SPECIES.length());
        
        for (; i < limit; i += SPECIES.length()) {
            jdk.incubator.vector.FloatVector mixVector = jdk.incubator.vector.FloatVector.zero(SPECIES);
            for (int c = 0; c < numChannels; c++) {
                float w = (weights != null && c < weights.length) ? weights[c] : 1.0f / numChannels;
                jdk.incubator.vector.FloatVector wVector = jdk.incubator.vector.FloatVector.broadcast(SPECIES, w);
                jdk.incubator.vector.FloatVector cVector = jdk.incubator.vector.FloatVector.fromArray(SPECIES, channels[c], i);
                mixVector = mixVector.add(cVector.mul(wVector));
            }
            mixVector.intoArray(output, i);
        }
        
        for (; i < len; i++) {
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

        public FrameChunker(int chunkSize, int hopSize) {
            this.chunkSize = chunkSize;
            this.hopSize = hopSize;
            this.buffer = new float[chunkSize * 8];
        }

        public synchronized void push(float[] samples) {
            if (samples == null) return;
            for (float s : samples) {
                buffer[writeIndex] = s;
                writeIndex = (writeIndex + 1) % buffer.length;
                if (count < buffer.length) {
                    count++;
                } else {
                    // Buffer overrun, drop oldest samples
                    readIndex = (readIndex + 1) % buffer.length;
                }
            }
        }

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
        
        public synchronized int availableSamples() {
            return count;
        }
    }

    /**
     * Block-based Noise Gate to attenuate signals below the threshold.
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

    /**
     * Downsamples a large array of float samples into exactly targetPoints peak values
     * (the absolute maximum in each segment) for rendering / timeline visualization.
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

    /**
     * Returns the maximum absolute peak value of a single audio frame.
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

}
