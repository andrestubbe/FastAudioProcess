package fastaudioprocess;

import fastaudioprocess.FastAudioProcess;
import fastaudio.FastAudioPlayer;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * PitchDemo demonstrating native JNI Autocorrelation-based Pitch Detection
 * and time-domain Pitch Shifting (speed-preserved) for both synthetic tones
 * and opinions.mp3.
 */
public class PitchDemo {
    private static final int SAMPLE_RATE = 16000;
    private static final int DURATION_SEC = 2;
    private static final int NUM_SAMPLES = SAMPLE_RATE * DURATION_SEC;

    public static void main(String[] args) {
        System.out.println("=== FastAudioProcess Native Pitch Detection & Shifting Demo ===");

        try {
            // Part 1: Original Synthetic Tone Demo
            System.out.println("\n========================================");
            System.out.println("PART 1: Synthetic Tone Pitch Demo (330Hz - E4)");
            System.out.println("========================================");

            float[] originalAudio = new float[NUM_SAMPLES];
            for (int i = 0; i < NUM_SAMPLES; i++) {
                originalAudio[i] = (float) Math.sin(2.0 * Math.PI * 330.0 * i / SAMPLE_RATE) * 0.5f;
            }
            
            float detectedOrig = FastAudioProcess.detectPitchNative(originalAudio, SAMPLE_RATE);
            System.out.printf("Estimated pitch of original tone: %.1f Hz (expected ~330.0 Hz)\n", detectedOrig);

            File originalFile = File.createTempFile("pitch_synth_orig_", ".wav");
            originalFile.deleteOnExit();
            writeWavFile(originalAudio, SAMPLE_RATE, originalFile);

            float[] shiftedUpSynth = originalAudio.clone();
            FastAudioProcess.pitchShiftNative(shiftedUpSynth, 4.0f, SAMPLE_RATE);
            float detectedUpSynth = FastAudioProcess.detectPitchNative(shiftedUpSynth, SAMPLE_RATE);
            System.out.printf("Estimated pitch of shifted-up tone: %.1f Hz (expected ~415.3 Hz)\n", detectedUpSynth);

            File shiftedUpSynthFile = File.createTempFile("pitch_synth_up_", ".wav");
            shiftedUpSynthFile.deleteOnExit();
            writeWavFile(shiftedUpSynth, SAMPLE_RATE, shiftedUpSynthFile);

            float[] shiftedDownSynth = originalAudio.clone();
            FastAudioProcess.pitchShiftNative(shiftedDownSynth, -5.0f, SAMPLE_RATE);
            float detectedDownSynth = FastAudioProcess.detectPitchNative(shiftedDownSynth, SAMPLE_RATE);
            System.out.printf("Estimated pitch of shifted-down tone: %.1f Hz (expected ~246.9 Hz)\n", detectedDownSynth);

            File shiftedDownSynthFile = File.createTempFile("pitch_synth_down_", ".wav");
            shiftedDownSynthFile.deleteOnExit();
            writeWavFile(shiftedDownSynth, SAMPLE_RATE, shiftedDownSynthFile);

            FastAudioPlayer player = new FastAudioPlayer();

            System.out.println("🔊 Playing original synthetic tone (330Hz, E4)...");
            player.load(originalFile);
            player.play();
            Thread.sleep(1500);
            player.stop();

            System.out.println("🔊 Playing pitch-shifted synthetic tone (+4 semitones, G#4)...");
            player.load(shiftedUpSynthFile);
            player.play();
            Thread.sleep(1500);
            player.stop();

            System.out.println("🔊 Playing pitch-shifted synthetic tone (-5 semitones, B3)...");
            player.load(shiftedDownSynthFile);
            player.play();
            Thread.sleep(1500);
            player.stop();


            // Part 2: opinions.mp3 Pitch Demo
            System.out.println("\n========================================");
            System.out.println("PART 2: opinions.mp3 Speech Pitch Demo");
            System.out.println("========================================");

            File mp3File = new File("../../opinions.mp3");
            if (!mp3File.exists()) {
                mp3File = new File("../opinions.mp3");
            }
            if (!mp3File.exists()) {
                mp3File = new File("opinions.mp3");
            }

            if (mp3File.exists()) {
                System.out.println("Found MP3: " + mp3File.getAbsolutePath());
                System.out.println("Converting MP3 to WAV...");
                File opinionsWav = FastAudioProcess.mp3ToWav(mp3File);

                float[] opinionsAudio = readWavSamples(opinionsWav);
                int opinionsRate = 44100;
                System.out.printf("Loaded %d speech samples at %d Hz.\n", opinionsAudio.length, opinionsRate);

                float detectedOpinions = FastAudioProcess.detectPitchNative(opinionsAudio, opinionsRate);
                System.out.printf("Estimated pitch of speech: %.1f Hz\n", detectedOpinions);

                System.out.println("Pitch Shifting speech UP (+4 Semitones)...");
                float[] opinionsUp = opinionsAudio.clone();
                FastAudioProcess.pitchShiftNative(opinionsUp, 4.0f, opinionsRate);
                File opinionsUpFile = File.createTempFile("pitch_opinions_up_", ".wav");
                opinionsUpFile.deleteOnExit();
                writeWavFile(opinionsUp, opinionsRate, opinionsUpFile);

                System.out.println("Pitch Shifting speech DOWN (-4 Semitones)...");
                float[] opinionsDown = opinionsAudio.clone();
                FastAudioProcess.pitchShiftNative(opinionsDown, -4.0f, opinionsRate);
                File opinionsDownFile = File.createTempFile("pitch_opinions_down_", ".wav");
                opinionsDownFile.deleteOnExit();
                writeWavFile(opinionsDown, opinionsRate, opinionsDownFile);

                System.out.println("🔊 Playing original speech...");
                player.load(opinionsWav);
                player.play();
                Thread.sleep(4000);
                player.stop();

                System.out.println("🔊 Playing speech shifted UP (+4 semitones)...");
                player.load(opinionsUpFile);
                player.play();
                Thread.sleep(4000);
                player.stop();

                System.out.println("🔊 Playing speech shifted DOWN (-4 semitones)...");
                player.load(opinionsDownFile);
                player.play();
                Thread.sleep(4000);
                player.stop();
            } else {
                System.out.println("opinions.mp3 not found. Skipping Part 2.");
            }

            player.close();
            System.out.println("\n=== Pitch Demo complete ===");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static float[] readWavSamples(File wavFile) throws Exception {
        AudioInputStream ais = AudioSystem.getAudioInputStream(wavFile);
        AudioFormat format = ais.getFormat();
        byte[] bytes = ais.readAllBytes();
        ais.close();
        
        int channels = format.getChannels();
        int bytesPerSample = format.getSampleSizeInBits() / 8;
        int numSamples = bytes.length / (channels * bytesPerSample);
        float[] samples = new float[numSamples];
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        
        for (int i = 0; i < numSamples; i++) {
            float sum = 0;
            for (int c = 0; c < channels; c++) {
                short sVal = bb.getShort();
                sum += sVal / 32768.0f;
            }
            samples[i] = sum / channels;
        }
        return samples;
    }

    private static void writeWavFile(float[] samples, int sampleRate, File file) throws Exception {
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        byte[] byteData = new byte[samples.length * 2];
        ByteBuffer buffer = ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN);
        for (float sample : samples) {
            float clamped = Math.max(-1.0f, Math.min(1.0f, sample));
            short sVal = (short) (clamped * 32767.0f);
            buffer.putShort(sVal);
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(byteData);
             AudioInputStream ais = new AudioInputStream(bais, format, samples.length)) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, file);
        }
    }
}
