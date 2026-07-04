package fastaudioprocess;

import fastaudioprocess.FastAudioProcess;
import fastaudio.FastAudioPlayer;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Demo showcasing FastAudioProcess SIMD computations and integration with FastAudioPlayer.
 */
public class Demo {
    public static void main(String[] args) {
        System.out.println("=== FastAudioProcess & FastAudioPlayer Demo ===");

        // 1. Showcase SIMD RMS volume calculation
        System.out.println("\n--- Step 1: SIMD RMS Volume Calculation ---");
        int numSamples = 8000; // 16,000 bytes for 16-bit PCM
        byte[] dummyPcm = new byte[numSamples * 2];
        
        // Generate a 440Hz sine wave as dummy audio data
        double sampleRate = 16000.0;
        double frequency = 440.0;
        ByteBuffer buffer = ByteBuffer.wrap(dummyPcm).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < numSamples; i++) {
            short value = (short) (Math.sin(2.0 * Math.PI * frequency * i / sampleRate) * 16384.0);
            buffer.putShort(value);
        }

        long start = System.nanoTime();
        float rms = FastAudioProcess.computeRms(dummyPcm, dummyPcm.length);
        long end = System.nanoTime();

        System.out.printf("Computed RMS of sine wave: %.4f (expected ~0.3536)\n", rms);
        System.out.printf("SIMD Calculation took: %d ns\n", (end - start));

        // 2. Showcase integration with FastAudioPlayer
        System.out.println("\n--- Step 2: Playback integration with FastAudioPlayer ---");
        
        // Locate a sample WAV file in related directories or fall back
        File wavFile = new File("../../FastAudioCapture/stream-recording.wav");
        if (!wavFile.exists()) {
            wavFile = new File("../FastAudioCapture/stream-recording.wav");
        }

        if (wavFile.exists()) {
            System.out.println("Found sample WAV file: " + wavFile.getAbsolutePath());
            try {
                FastAudioPlayer player = new FastAudioPlayer();
                System.out.println("Loading WAV file into FastAudioPlayer...");
                if (player.load(wavFile)) {
                    System.out.println("Playing audio (duration: " + player.getDuration() + " ms)...");
                    player.play();
                    
                    // Let it play for 3 seconds
                    Thread.sleep(3000);
                    
                    System.out.println("Stopping playback...");
                    player.stop();
                    player.close();
                } else {
                    System.out.println("Failed to load WAV file into player.");
                }
            } catch (Exception e) {
                System.out.println("Error during playback: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("No sample WAV file found at expected path. Skipping audio playback demonstration.");
        }

        System.out.println("\n=== Demo Complete ===");
    }
}
