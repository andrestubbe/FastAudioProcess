package fastaudioprocess.demo;

import fastaudioprocess.FastAudioProcess;

public class Demo {
    public static void main(String[] args) {
        System.out.println("--- FastAudioProcess 0.1.1 Demo ---");
        
        float[] audio = new float[44100];
        for (int i = 0; i < audio.length; i++) {
            audio[i] = (float) Math.sin(2.0 * Math.PI * 440.0 * i / 44100.0);
        }
        
        float pitch = FastAudioProcess.detectPitchNative(audio, 44100);
        System.out.printf("[+] Generated 1 second 440Hz sine wave. Detected Pitch: %.2f Hz%n", pitch);
        System.out.println("✔ Audio processing complete.");
    }
}
