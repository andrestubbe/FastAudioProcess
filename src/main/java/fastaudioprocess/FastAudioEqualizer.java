package fastaudioprocess;

/**
 * Stateful 3-Band Streaming Equalizer preserving IIR filter memory across consecutive blocks.
 * <p>
 * Employs Low-pass and High-pass crossover filters to split incoming audio into Bass, Midrange,
 * and Treble frequency bands with independent decibel gain adjustments. Preserving internal filter
 * memory prevents audible pops and discontinuities across streaming chunk boundaries.
 * </p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * FastAudioEqualizer eq = new FastAudioEqualizer();
 * eq.setGains(+4.0f, -2.0f, +1.5f); // Bass +4dB, Mid -2dB, Treble +1.5dB
 *
 * while (stream.hasMoreChunks()) {
 *     float[] chunk = stream.readChunk();
 *     eq.process(chunk); // Filtered in-place with continuous state
 * }
 * }</pre>
 */
public final class FastAudioEqualizer {

    private float lp = 0.0f;
    private float hp = 0.0f;
    private float prevInput = 0.0f;

    private float bassGain = 1.0f;
    private float midGain = 1.0f;
    private float trebleGain = 1.0f;

    /**
     * Creates a new stateful equalizer with flat (0.0 dB) frequency response.
     */
    public FastAudioEqualizer() {
        setGains(0.0f, 0.0f, 0.0f);
    }

    /**
     * Configures the decibel gain adjustments for Bass, Midrange, and Treble frequency bands.
     *
     * @param bassGainDb   bass gain adjustment in decibels (e.g. +3.0f or -6.0f)
     * @param midGainDb    midrange gain adjustment in decibels (e.g. 0.0f)
     * @param trebleGainDb treble gain adjustment in decibels (e.g. +2.0f)
     */
    public void setGains(float bassGainDb, float midGainDb, float trebleGainDb) {
        this.bassGain = (float) Math.pow(10.0, bassGainDb / 20.0);
        this.midGain = (float) Math.pow(10.0, midGainDb / 20.0);
        this.trebleGain = (float) Math.pow(10.0, trebleGainDb / 20.0);
    }

    /**
     * Processes an audio frame in-place, applying 3-band equalization while updating internal filter states.
     *
     * @param samples audio sample array to equalize in-place (silently ignored if null or empty)
     */
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

    /**
     * Resets internal low-pass and high-pass memory states to zero.
     * Call when seeking or switching audio streams to prevent transition pops.
     */
    public void reset() {
        this.lp = 0.0f;
        this.hp = 0.0f;
        this.prevInput = 0.0f;
    }
}