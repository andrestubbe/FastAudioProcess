package fastaudioprocess;

import fastaudioprocess.FastAudioProcess;
import fastaudioprocess.SileroVAD;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * VADDemo showing stateful speech detection using the Silero VAD ONNX model.
 */
public class VADDemo {

    public static void main(String[] args) {
        System.out.println("=== FastAudioProcess Silero VAD Demo ===");

        try {
            // 1. Download/Load Silero VAD ONNX model
            File modelFile = new File("silero_vad.onnx");
            if (!modelFile.exists()) {
                System.out.println("Downloading Silero VAD model (1.8MB) from GitHub...");
                URL url = new URL("https://github.com/snakers4/silero-vad/raw/master/src/silero_vad/data/silero_vad.onnx");
                try (BufferedInputStream bis = new BufferedInputStream(url.openStream());
                     FileOutputStream fis = new FileOutputStream(modelFile)) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = bis.read(buffer, 0, 4096)) != -1) {
                        fis.write(buffer, 0, count);
                    }
                }
                System.out.println("Model downloaded successfully.");
            } else {
                System.out.println("Using local model: " + modelFile.getAbsolutePath());
            }

            // 2. Load opinions.mp3 and convert to WAV
            File mp3File = new File("../../opinions.mp3");
            if (!mp3File.exists()) {
                mp3File = new File("../opinions.mp3");
            }
            if (!mp3File.exists()) {
                mp3File = new File("opinions.mp3");
            }

            if (!mp3File.exists()) {
                System.out.println("[ERROR] opinions.mp3 not found. Run the pitch demo first or put opinions.mp3 in root.");
                return;
            }

            System.out.println("Found MP3: " + mp3File.getAbsolutePath());
            System.out.println("Converting MP3 to WAV (44.1kHz Stereo)...");
            File wav44k = FastAudioProcess.mp3ToWav(mp3File);

            // 3. Read 44.1kHz samples and resample to 16kHz Mono in memory
            float[] audio44k = readWavSamples(wav44k);
            System.out.printf("Loaded %d samples at 44100 Hz. Resampling to 16000 Hz...\n", audio44k.length);
            float[] samples = resample(audio44k, 44100f, 16000f);
            System.out.printf("Resampled to %d samples at 16000 Hz.\n", samples.length);

            // Check if audio has non-zero signal
            float maxSample = 0f;
            for (float s : samples) {
                if (Math.abs(s) > maxSample) maxSample = Math.abs(s);
            }
            System.out.printf("Max audio sample amplitude: %.5f\n", maxSample);

            // Normalize audio to avoid low volume issues in VAD
            if (maxSample > 0.0001f) {
                float targetPeak = 0.8f;
                float scale = targetPeak / maxSample;
                for (int i = 0; i < samples.length; i++) {
                    samples[i] *= scale;
                }
                System.out.println("Normalized audio to 0.8 peak amplitude.");
            }

            // 4. Initialize Silero VAD
            System.out.println("Initializing Silero VAD...");
            try (SileroVAD vad = new SileroVAD(modelFile.getAbsolutePath())) {
                
                int frameSize = 512; // Required frame size
                int step = 512;      // Non-overlapping step
                int totalFrames = samples.length / frameSize;
                
                System.out.println("\n--- Step 5: Streaming VAD Speech Probability Timeline ---");
                System.out.println("Time (sec) | Speech Probability | Speech Detected");
                System.out.println("---------------------------------------------");

                float threshold = 0.5f;
                int printInterval = 5; // Print every 5 frames (~160ms) to avoid spamming
                
                for (int f = 0; f < totalFrames; f++) {
                    float[] frame = new float[frameSize];
                    System.arraycopy(samples, f * step, frame, 0, frameSize);
                    
                    float prob = vad.detectSpeech(frame, 16000);
                    
                    if (f % printInterval == 0) {
                        double timeSec = (double) (f * step) / 16000.0;
                        System.out.printf("   %5.2fs   |        %5.2f       |      %b\n", timeSec, prob, (prob > threshold));
                    }
                }
            }

            System.out.println("\n=== Silero VAD Demo complete ===");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static float[] resample(float[] input, float sourceRate, float targetRate) {
        if (sourceRate == targetRate) return input;
        double factor = (double) sourceRate / targetRate;
        int targetLength = (int) (input.length / factor);
        float[] output = new float[targetLength];
        for (int i = 0; i < targetLength; i++) {
            double srcIndex = i * factor;
            int base = (int) srcIndex;
            double frac = srcIndex - base;
            if (base < input.length - 1) {
                output[i] = (float) ((1.0 - frac) * input[base] + frac * input[base + 1]);
            } else {
                output[i] = input[input.length - 1];
            }
        }
        return output;
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
}
