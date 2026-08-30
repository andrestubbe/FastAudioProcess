package fastaudioprocess;

/**
 * High-Performance in-place Cooley-Tukey Radix-2 Fast Fourier Transform (FFT) and Inverse FFT (IFFT).
 * <p>
 * Computes frequency-domain and time-domain signal representations with {@code O(N log N)} algorithmic complexity.
 * Utilizes precomputed bit-reversal permutation tables and trigonometric twiddle factor plans
 * for zero-allocation deterministic execution, automatically delegating to native AVX2 SIMD kernels when available.
 * </p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * float[] real = new float[512];
 * float[] imag = new float[512];
 *
 * // Fill real with audio samples...
 * FastFFT.fft(real, imag);  // Forward FFT -> converts to frequency spectrum
 * FastFFT.ifft(real, imag); // Inverse IFFT -> converts back to time-domain
 * }</pre>
 */
public final class FastFFT {

    private static final int MAX_SUPPORTED_SIZE = 16384;
    private static final int[][] BIT_REVERSAL_PLANS = new int[15][];
    private static final float[][] TWIDDLE_REAL_PLANS = new float[15][];
    private static final float[][] TWIDDLE_IMAG_PLANS = new float[15][];

    static {
        for (int p = 1; p <= 14; p++) {
            int n = 1 << p;
            int[] bitRev = new int[n];
            int j = 0;
            for (int i = 0; i < n - 1; i++) {
                bitRev[i] = j;
                int k = n >> 1;
                while (k <= j) {
                    j -= k;
                    k >>= 1;
                }
                j += k;
            }
            bitRev[n - 1] = n - 1;
            BIT_REVERSAL_PLANS[p] = bitRev;

            float[] twR = new float[n / 2];
            float[] twI = new float[n / 2];
            for (int i = 0; i < n / 2; i++) {
                double angle = -2.0 * Math.PI * i / n;
                twR[i] = (float) Math.cos(angle);
                twI[i] = (float) Math.sin(angle);
            }
            TWIDDLE_REAL_PLANS[p] = twR;
            TWIDDLE_IMAG_PLANS[p] = twI;
        }
    }

    private FastFFT() {
    }

    /**
     * Native AVX2 hardware-accelerated in-place forward Radix-2 FFT kernel.
     *
     * @param real real component array (length must be a power of 2)
     * @param imag imaginary component array (same length as real)
     */
    public static native void fftNative(float[] real, float[] imag);

    /**
     * Computes the forward in-place Radix-2 FFT of complex buffers ({@code real}, {@code imag}).
     * Converts time-domain audio samples into discrete frequency spectrum bins.
     *
     * @param real real component array (modified in-place, length must be a power of 2 &lt;= 16384)
     * @param imag imaginary component array (modified in-place, same length as real)
     */
    public static void fft(float[] real, float[] imag) {
        if (real == null || imag == null || real.length <= 1) return;

        if (FastAudioProcess.isNativeLoaded()) {
            try {
                fftNative(real, imag);
                return;
            } catch (UnsatisfiedLinkError ignored) {
            }
        }

        int n = real.length;
        int p = Integer.numberOfTrailingZeros(n);
        if ((1 << p) != n || p > 14) return; // Non power of two safeguard

        int[] bitRev = BIT_REVERSAL_PLANS[p];
        for (int i = 0; i < n; i++) {
            int target = bitRev[i];
            if (i < target) {
                float tr = real[i]; real[i] = real[target]; real[target] = tr;
                float ti = imag[i]; imag[i] = imag[target]; imag[target] = ti;
            }
        }

        for (int len = 2; len <= n; len <<= 1) {
            int halfLen = len >> 1;
            int step = n / len;
            for (int i = 0; i < n; i += len) {
                for (int k = 0; k < halfLen; k++) {
                    int twIdx = k * step;
                    float wr = TWIDDLE_REAL_PLANS[p][twIdx];
                    float wi = TWIDDLE_IMAG_PLANS[p][twIdx];

                    int posA = i + k;
                    int posB = i + k + halfLen;

                    float br = real[posB];
                    float bi = imag[posB];
                    float tr = wr * br - wi * bi;
                    float ti = wr * bi + wi * br;

                    real[posB] = real[posA] - tr;
                    imag[posB] = imag[posA] - ti;
                    real[posA] = real[posA] + tr;
                    imag[posA] = imag[posA] + ti;
                }
            }
        }
    }

    /**
     * Computes the inverse in-place Radix-2 IFFT of complex buffers ({@code real}, {@code imag}).
     * Converts frequency-domain spectrum bins back into time-domain audio samples.
     *
     * @param real real component array (modified in-place, length must be a power of 2)
     * @param imag imaginary component array (modified in-place, same length as real)
     */
    public static void ifft(float[] real, float[] imag) {
        if (real == null || imag == null || real.length <= 1) return;
        int n = real.length;

        for (int i = 0; i < n; i++) imag[i] = -imag[i];

        fft(real, imag);

        float invN = 1.0f / n;
        for (int i = 0; i < n; i++) {
            real[i] *= invN;
            imag[i] = -imag[i] * invN;
        }
    }
}