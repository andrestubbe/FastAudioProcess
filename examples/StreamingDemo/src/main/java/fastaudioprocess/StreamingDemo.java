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
 * StreamingDemo demonstrating FrameChunker, block-based Equalizer, and Noise Gate
 * with audible WASAPI playback.
 */
public class StreamingDemo {
    private static final int SAMPLE_RATE = 16000;
    private static final int CHUNK_SIZE = 512;
    private static final int HOP_SIZE = 256;
    private static final int RUN_SAMPLES = SAMPLE_RATE * 3; // 3 seconds

    public static void main(String[] args) {
        System.out.println("=== FastAudioProcess Streaming & Audio FX Demo ===");

        try {
            // 1. Generate synth audio with quiet segments (to test Noise Gate) and a base frequency
            System.out.println("\n--- Step 1: Synthesizing Audio (3 seconds) ---");
            float[] sourceAudio = new float[RUN_SAMPLES];
            for (int i = 0; i < RUN_SAMPLES; i++) {
                // Introduce quiet segments (silence between tones)
                if ((i > SAMPLE_RATE * 0.8 && i < SAMPLE_RATE * 1.2) || 
                    (i > SAMPLE_RATE * 1.8 && i < SAMPLE_RATE * 2.2)) {
                    // Quiet background hiss
                    sourceAudio[i] = (float) (Math.random() * 0.005);
                } else {
                    // 150Hz sine wave (low bass) + 800Hz sine wave (mid/treble)
                    sourceAudio[i] = (float) (Math.sin(2.0 * Math.PI * 150.0 * i / SAMPLE_RATE) * 0.2f + 
                                              Math.sin(2.0 * Math.PI * 800.0 * i / SAMPLE_RATE) * 0.2f);
                }
            }
            File originalFile = File.createTempFile("streaming_orig_", ".wav");
            originalFile.deleteOnExit();
            writeWavFile(sourceAudio, SAMPLE_RATE, originalFile);
            System.out.println("Saved original track to: " + originalFile.getAbsolutePath());

            // 2. Initialize FrameChunker and push samples in chunks
            System.out.println("\n--- Step 2: Running Frame-Chunking Pipeline ---");
            FastAudioProcess.FrameChunker chunker = new FastAudioProcess.FrameChunker(CHUNK_SIZE, HOP_SIZE);
            float[] processedAudio = new float[RUN_SAMPLES];
            int writePtr = 0;

            // Stream simulation: Feed audio in blocks of 1024 samples
            int inputBlockSize = 1024;
            for (int i = 0; i < RUN_SAMPLES; i += inputBlockSize) {
                int size = Math.min(inputBlockSize, RUN_SAMPLES - i);
                float[] block = new float[size];
                System.arraycopy(sourceAudio, i, block, 0, size);
                
                // Push block to streaming chunker
                chunker.push(block);

                // Pull overlapping chunks (e.g. 512 samples with 256 sample hops)
                float[] chunk;
                while ((chunk = chunker.nextChunk()) != null) {
                    // Apply heavy BASS BOOST (+15dB bass, -12dB treble) using Equalizer
                    FastAudioProcess.apply3BandEqualizer(chunk, 15.0f, 0.0f, -12.0f);
                    
                    // Apply Noise Gate to filter out the hiss during quiet parts
                    FastAudioProcess.applyNoiseGate(chunk, -30.0f, -60.0f);

                    // Re-assemble overlapping chunks using simple overlap-add or copy
                    int copyLen = Math.min(HOP_SIZE, RUN_SAMPLES - writePtr);
                    System.arraycopy(chunk, 0, processedAudio, writePtr, copyLen);
                    writePtr += copyLen;
                }
            }

            File processedFile = File.createTempFile("streaming_proc_", ".wav");
            processedFile.deleteOnExit();
            writeWavFile(processedAudio, SAMPLE_RATE, processedFile);
            System.out.println("Saved processed (EQ Bass-Boosted + Gated) track to: " + processedFile.getAbsolutePath());

            // 3. Play both files sequentially
            System.out.println("\n--- Step 3: Playback Demonstration ---");
            FastAudioPlayer player = new FastAudioPlayer();

            System.out.println("🔊 Playing original track (containing hiss in quiet spots)...");
            player.load(originalFile);
            player.play();
            Thread.sleep(3200);
            player.stop();

            System.out.println("🔊 Playing processed track (Bass-Boosted and Hiss gated out)...");
            player.load(processedFile);
            player.play();
            Thread.sleep(3200);
            player.stop();

            player.close();
            System.out.println("\n=== Streaming & FX Demo complete ===");

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
