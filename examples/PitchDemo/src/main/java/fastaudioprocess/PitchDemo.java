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
 * and time-domain Pitch Shifting (speed-preserved) with audible WASAPI playback.
 */
public class PitchDemo {
    private static final int SAMPLE_RATE = 16000;
    private static final int DURATION_SEC = 2;
    private static final int NUM_SAMPLES = SAMPLE_RATE * DURATION_SEC;

    public static void main(String[] args) {
        System.out.println("=== FastAudioProcess Native Pitch Detection & Shifting Demo ===");

        try {
            // 1. Synthesize target tone (330Hz - E4 note)
            System.out.println("\n--- Step 1: Synthesizing Tone (330Hz - E4) ---");
            float[] originalAudio = new float[NUM_SAMPLES];
            for (int i = 0; i < NUM_SAMPLES; i++) {
                originalAudio[i] = (float) Math.sin(2.0 * Math.PI * 330.0 * i / SAMPLE_RATE) * 0.5f;
            }
            
            // Detect pitch of original audio
            float detectedOrig = FastAudioProcess.detectPitchNative(originalAudio, SAMPLE_RATE);
            System.out.printf("Estimated pitch of original tone: %.1f Hz (expected ~330.0 Hz)\n", detectedOrig);

            File originalFile = File.createTempFile("pitch_orig_", ".wav");
            originalFile.deleteOnExit();
            writeWavFile(originalAudio, SAMPLE_RATE, originalFile);
            System.out.println("Saved original track to: " + originalFile.getAbsolutePath());

            // 2. Pitch shift UP by 4 semitones (to G#4 / ~415Hz)
            System.out.println("\n--- Step 2: Pitch Shifting UP (+4 Semitones) ---");
            float[] shiftedUp = originalAudio.clone();
            FastAudioProcess.pitchShiftNative(shiftedUp, 4.0f, SAMPLE_RATE);

            float detectedUp = FastAudioProcess.detectPitchNative(shiftedUp, SAMPLE_RATE);
            System.out.printf("Estimated pitch of shifted-up tone: %.1f Hz (expected ~415.3 Hz)\n", detectedUp);

            File shiftedUpFile = File.createTempFile("pitch_up_", ".wav");
            shiftedUpFile.deleteOnExit();
            writeWavFile(shiftedUp, SAMPLE_RATE, shiftedUpFile);
            System.out.println("Saved shifted-up track to: " + shiftedUpFile.getAbsolutePath());

            // 3. Pitch shift DOWN by 5 semitones (to B3 / ~247Hz)
            System.out.println("\n--- Step 3: Pitch Shifting DOWN (-5 Semitones) ---");
            float[] shiftedDown = originalAudio.clone();
            FastAudioProcess.pitchShiftNative(shiftedDown, -5.0f, SAMPLE_RATE);

            float detectedDown = FastAudioProcess.detectPitchNative(shiftedDown, SAMPLE_RATE);
            System.out.printf("Estimated pitch of shifted-down tone: %.1f Hz (expected ~246.9 Hz)\n", detectedDown);

            File shiftedDownFile = File.createTempFile("pitch_down_", ".wav");
            shiftedDownFile.deleteOnExit();
            writeWavFile(shiftedDown, SAMPLE_RATE, shiftedDownFile);
            System.out.println("Saved shifted-down track to: " + shiftedDownFile.getAbsolutePath());

            // 4. Audible Playback using WASAPI
            System.out.println("\n--- Step 4: Playback Demonstration ---");
            FastAudioPlayer player = new FastAudioPlayer();

            System.out.println("🔊 Playing original tone (330Hz, E4)...");
            player.load(originalFile);
            player.play();
            Thread.sleep(2000);
            player.stop();

            System.out.println("🔊 Playing pitch-shifted tone (+4 semitones, G#4)...");
            player.load(shiftedUpFile);
            player.play();
            Thread.sleep(2000);
            player.stop();

            System.out.println("🔊 Playing pitch-shifted tone (-5 semitones, B3)...");
            player.load(shiftedDownFile);
            player.play();
            Thread.sleep(2000);
            player.stop();

            player.close();
            System.out.println("\n=== Pitch Demo complete ===");

        } catch (Exception e) {
            e.printStackTrace();
        }
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
