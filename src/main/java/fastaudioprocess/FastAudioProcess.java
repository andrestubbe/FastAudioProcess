package fastaudioprocess;

import java.io.*;
import javax.sound.sampled.*;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorSpecies;

/**
 * FastAudioProcess - High-performance audio processing, format conversions, and resampling.
 */
public final class FastAudioProcess {

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
}
