package fastaudioprocess;

import java.io.*;
import javax.sound.sampled.*;

/**
 * Audio format conversion codecs, WAV resampling, and waveform downsampling.
 */
public final class FastAudioCodec {

    private FastAudioCodec() {
        // Static utility class
    }

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
}