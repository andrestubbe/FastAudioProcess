package fastaudioprocess;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FastAudioProcessTest {

    @Test
    public void testNoiseSuppressionReducesNoiseEnergy() {
        int n = 512;
        float[] dirty = new float[n];
        
        java.util.Random rnd = new java.util.Random(42);
        for (int i = 0; i < n; i++) {
            float signal = (float) Math.sin(2.0 * Math.PI * 250.0 * i / 16000.0) * 0.5f;
            float noise = (rnd.nextFloat() - 0.5f) * 0.3f;
            dirty[i] = signal + noise;
        }

        float beforeRms = computeRms(dirty);
        FastAudioDenoise.suppressNoise(dirty, 16000, 1.2f, 0.05f);
        float afterRms = computeRms(dirty);

        assertTrue(afterRms < beforeRms);
        assertTrue(afterRms > 0.1f);
    }

    @Test
    public void testNoiseGateAttenuatesSilence() {
        float[] quiet = new float[256];
        for (int i = 0; i < 256; i++) {
            quiet[i] = 0.001f;
        }

        FastAudioDenoise.applyNoiseGate(quiet, -30.0f, -40.0f);
        for (float s : quiet) {
            assertTrue(s < 0.0001f, "Sub-threshold signal must be attenuated by noise gate");
        }
    }

    @Test
    public void testAcousticFeatureExtraction() {
        int n = 320;
        float[] sineWave = new float[n];
        for (int i = 0; i < n; i++) {
            sineWave[i] = (float) Math.sin(2.0 * Math.PI * 250.0 * i / 16000.0) * 0.8f;
        }

        float crest = FastAudioAcoustics.computeCrestFactor(sineWave);
        assertTrue(crest >= 1.3f && crest <= 1.6f, "Crest factor of sine wave must be ~1.414");

        float zcr = FastAudioAcoustics.computeZeroCrossingRate(sineWave);
        assertTrue(zcr < 0.10f, "Low frequency sine wave must have small ZCR");

        float periodicity = FastAudioAcoustics.computeAutocorrelationPeriodicity(sineWave, 35, 160);
        assertTrue(periodicity > 0.70f, "Harmonic sine wave must have high periodicity");
    }

    private static float computeRms(float[] samples) {
        double sum = 0.0;
        for (float s : samples) sum += s * s;
        return (float) Math.sqrt(sum / samples.length);
    }
}