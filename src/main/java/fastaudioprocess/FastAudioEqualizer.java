package fastaudioprocess;

/**
 * Stateful 3-band Streaming Equalizer preserving IIR filter memory across consecutive blocks.
 */
public final class FastAudioEqualizer {

    private float lp = 0.0f;
    private float hp = 0.0f;
    private float prevInput = 0.0f;

    private float bassGain = 1.0f;
    private float midGain = 1.0f;
    private float trebleGain = 1.0f;

    public FastAudioEqualizer() {
        setGains(0.0f, 0.0f, 0.0f);
    }

    public void setGains(float bassGainDb, float midGainDb, float trebleGainDb) {
        this.bassGain = (float) Math.pow(10.0, bassGainDb / 20.0);
        this.midGain = (float) Math.pow(10.0, midGainDb / 20.0);
        this.trebleGain = (float) Math.pow(10.0, trebleGainDb / 20.0);
    }

    public void process(float[] samples) {
        if (samples == null || samples.length == 0) return;
        float alphaL = 0.15f; 
        float alphaH = 0.75f; 

        for (int i = 0; i < samples.length; i++) {
            float input = samples[i];
            lp = lp + alphaL * (input - lp);
            float bass = lp;
            hp = alphaH * (hp + input - prevInput);
            prevInput = input;
            float treble = hp;
            float mid = input - bass - treble;
            samples[i] = bass * bassGain + mid * midGain + treble * trebleGain;
        }
    }

    public void reset() {
        this.lp = 0.0f;
        this.hp = 0.0f;
        this.prevInput = 0.0f;
    }
}