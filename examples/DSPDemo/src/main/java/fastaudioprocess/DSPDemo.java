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
 * DSPDemo showcasing VAD, Pre-Emphasis, SIMD Normalization, Log-Mel Spectrogram,
 * Pitch-Shifting, and Channel Mixing with audible playback.
 */
public class DSPDemo {
    private static final int SAMPLE_RATE = 16000;
    private static final int DURATION_SEC = 2;
    private static final int NUM_SAMPLES = SAMPLE_RATE * DURATION_SEC;

    public static void main(String[] args) {
        System.out.println("=== FastAudioProcess DSP & Preprocessing Demo ===");

        try {
            // 1. Generate two synthetic audio tracks (Sine waves)
            System.out.println("\n--- Step 1: Synthesizing Audio Channels ---");
            float[] track1 = new float[NUM_SAMPLES];
            float[] track2 = new float[NUM_SAMPLES];
            
            for (int i = 0; i < NUM_SAMPLES; i++) {
                // Track 1: 220Hz Sine Wave (low pitch)
                track1[i] = (float) Math.sin(2.0 * Math.PI * 220.0 * i / SAMPLE_RATE) * 0.4f;
                // Track 2: 440Hz Sine Wave (high pitch)
                track2[i] = (float) Math.sin(2.0 * Math.PI * 440.0 * i / SAMPLE_RATE) * 0.4f;
            }
            System.out.println("Generated two 2-second tracks (220Hz and 440Hz).");

            // 2. Mix channels using SIMD
            System.out.println("\n--- Step 2: Mixing Channels (SIMD-Accelerated) ---");
            float[][] channels = { track1, track2 };
            float[] weights = { 0.5f, 0.5f };
            float[] mixed = FastAudioProcess.mixChannels(channels, weights);
            System.out.println("Tracks mixed successfully.");

            // 3. Peak Normalization using SIMD
            System.out.println("\n--- Step 3: Peak Normalizing mixed track to 0.9 ---");
            FastAudioProcess.normalize(mixed, 0.9f);
            System.out.println("Normalized mixed track.");

            // Write files for playback
            File tempMixedFile = File.createTempFile("dsp_mixed_", ".wav");
            tempMixedFile.deleteOnExit();
            writeWavFile(mixed, SAMPLE_RATE, tempMixedFile);
            System.out.println("Saved mixed track to: " + tempMixedFile.getAbsolutePath());

            // 4. Pitch Shifting by -5 semitones (lower tone)
            System.out.println("\n--- Step 4: Pitch Shifting (-5 Semitones) ---");
            float[] pitchShifted = FastAudioProcess.pitchShiftResample(mixed, -5.0f);
            File tempPitchFile = File.createTempFile("dsp_pitch_", ".wav");
            tempPitchFile.deleteOnExit();
            writeWavFile(pitchShifted, SAMPLE_RATE, tempPitchFile);
            System.out.println("Saved pitch-shifted track to: " + tempPitchFile.getAbsolutePath());

            // 5. Pre-Emphasis high-pass filtering (0.97 coefficient)
            System.out.println("\n--- Step 5: Applying Pre-Emphasis Filter ---");
            float[] preEmphasized = mixed.clone();
            FastAudioProcess.preEmphasis(preEmphasized, 0.97f);
            File tempPreEmphasisFile = File.createTempFile("dsp_preemphasis_", ".wav");
            tempPreEmphasisFile.deleteOnExit();
            writeWavFile(preEmphasized, SAMPLE_RATE, tempPreEmphasisFile);
            System.out.println("Saved pre-emphasized track to: " + tempPreEmphasisFile.getAbsolutePath());

            // 6. Log-Mel Spectrogram extraction
            System.out.println("\n--- Step 6: Extracting Log-Mel Spectrogram ---");
            float[][] melSpec = FastAudioProcess.logMelSpectrogram(mixed, SAMPLE_RATE, 512, 256, 40);
            System.out.printf("Log-Mel Spectrogram extracted. Frames: %d, Mel Bins: %d\n", melSpec.length, melSpec[0].length);

            // 7. Energy-based VAD frames
            System.out.println("\n--- Step 7: Frame-based VAD Energy Analysis ---");
            short[] pcmShorts = new short[NUM_SAMPLES];
            for (int i = 0; i < NUM_SAMPLES; i++) {
                pcmShorts[i] = (short) (mixed[i] * 32767.0f);
            }
            int frameSize = 160; // 10ms frames at 16kHz
            System.out.println("First 5 frames VAD energies:");
            for (int f = 0; f < 5; f++) {
                float energy = FastAudioProcess.computeFrameEnergy(pcmShorts, f * frameSize, frameSize);
                System.out.printf("  Frame %d Energy: %.5f (Voice Active: %b)\n", f, energy, (energy > 0.005f));
            }

            // 8. Audible Playback using FastAudioPlayer
            System.out.println("\n--- Step 8: Playback Demonstration ---");
            FastAudioPlayer player = new FastAudioPlayer();

            System.out.println("🔊 Playing mixed track (220Hz + 440Hz, normalized)...");
            player.load(tempMixedFile);
            player.play();
            Thread.sleep(2000);
            player.stop();

            System.out.println("🔊 Playing pitch-shifted track (shifted down by 5 semitones)...");
            player.load(tempPitchFile);
            player.play();
            Thread.sleep(2200);
            player.stop();

            System.out.println("🔊 Playing pre-emphasized track (high-pass filtered)...");
            player.load(tempPreEmphasisFile);
            player.play();
            Thread.sleep(2000);
            player.stop();

            player.close();
            System.out.println("\n=== All DSP operations completed successfully ===");

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
