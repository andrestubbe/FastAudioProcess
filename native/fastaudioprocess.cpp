#include <jni.h>
#include <windows.h>
#include <immintrin.h>
#include <cmath>
#include <algorithm>

BOOL APIENTRY DllMain(HMODULE hModule, DWORD ul_reason_for_call, LPVOID lpReserved) {
    if (ul_reason_for_call == DLL_PROCESS_ATTACH) {
        DisableThreadLibraryCalls(hModule);
    }
    return TRUE;
}

static thread_local float g_delayBuffer[16384] = {0.0f};

// Precalculated Static Twiddle Factor Tables for Native FFT (Sizes 2 to 16384)
static float g_twiddleReal[15][8192];
static float g_twiddleImag[15][8192];
static int g_bitRevTable[15][16384];
static bool g_tablesInitialized = false;

static void initFftTables() {
    if (g_tablesInitialized) return;
    for (int p = 1; p <= 14; p++) {
        int n = 1 << p;
        int j = 0;
        for (int i = 0; i < n - 1; i++) {
            g_bitRevTable[p][i] = j;
            int k = n >> 1;
            while (k <= j) {
                j -= k;
                k >>= 1;
            }
            j += k;
        }
        g_bitRevTable[p][n - 1] = n - 1;

        for (int i = 0; i < n / 2; i++) {
            double angle = -2.0 * 3.14159265358979323846 * i / n;
            g_twiddleReal[p][i] = (float)std::cos(angle);
            g_twiddleImag[p][i] = (float)std::sin(angle);
        }
    }
    g_tablesInitialized = true;
}

extern "C" {

// True 256-bit AVX2 SIMD Pitch Detection with DC Removal & FMA Intrinsics
JNIEXPORT jfloat JNICALL Java_fastaudioprocess_FastAudioProcess_detectPitchNative(JNIEnv* env, jclass clazz, jfloatArray sampleArray, jint sampleRate) {
    if (!sampleArray || sampleRate <= 0) return 0.0f;
    jsize len = env->GetArrayLength(sampleArray);
    if (len < 128) return 0.0f;

    float* samples = (float*)env->GetPrimitiveArrayCritical(sampleArray, NULL);
    if (!samples) return 0.0f;

    __m256 vSum = _mm256_setzero_ps();
    int i = 0;
    for (; i <= len - 8; i += 8) {
        vSum = _mm256_add_ps(vSum, _mm256_loadu_ps(samples + i));
    }
    float temp[8];
    _mm256_storeu_ps(temp, vSum);
    double totalSum = temp[0] + temp[1] + temp[2] + temp[3] + temp[4] + temp[5] + temp[6] + temp[7];
    for (; i < len; i++) totalSum += samples[i];
    float dcMean = (float)(totalSum / len);
    __m256 vDc = _mm256_set1_ps(dcMean);

    int minShift = sampleRate / 1000;
    int maxShift = sampleRate / 50;
    if (maxShift >= len) maxShift = len - 1;

    float maxCorrelation = -1.0f;
    int bestShift = -1;

    for (int shift = minShift; shift <= maxShift; shift++) {
        int count = len - shift;
        __m256 vCorr = _mm256_setzero_ps();
        __m256 vEnergySrc = _mm256_setzero_ps();
        __m256 vEnergyShifted = _mm256_setzero_ps();

        int k = 0;
        for (; k <= count - 8; k += 8) {
            __m256 s1 = _mm256_sub_ps(_mm256_loadu_ps(samples + k), vDc);
            __m256 s2 = _mm256_sub_ps(_mm256_loadu_ps(samples + k + shift), vDc);
            vCorr = _mm256_fmadd_ps(s1, s2, vCorr);
            vEnergySrc = _mm256_fmadd_ps(s1, s1, vEnergySrc);
            vEnergyShifted = _mm256_fmadd_ps(s2, s2, vEnergyShifted);
        }

        _mm256_storeu_ps(temp, vCorr);
        double corr = temp[0] + temp[1] + temp[2] + temp[3] + temp[4] + temp[5] + temp[6] + temp[7];

        _mm256_storeu_ps(temp, vEnergySrc);
        double energySrc = temp[0] + temp[1] + temp[2] + temp[3] + temp[4] + temp[5] + temp[6] + temp[7];

        _mm256_storeu_ps(temp, vEnergyShifted);
        double energyShifted = temp[0] + temp[1] + temp[2] + temp[3] + temp[4] + temp[5] + temp[6] + temp[7];

        for (; k < count; k++) {
            float s1 = samples[k] - dcMean;
            float s2 = samples[k + shift] - dcMean;
            corr += (s1 * s2);
            energySrc += (s1 * s1);
            energyShifted += (s2 * s2);
        }

        if (energySrc > 1e-6 && energyShifted > 1e-6) {
            float normCorr = (float)(corr / std::sqrt(energySrc * energyShifted));
            if (normCorr > maxCorrelation) {
                maxCorrelation = normCorr;
                bestShift = shift;
            }
        }
    }

    env->ReleasePrimitiveArrayCritical(sampleArray, samples, JNI_ABORT);

    if (maxCorrelation > 0.65f && bestShift > 0) {
        return (float)sampleRate / (float)bestShift;
    }
    return 0.0f;
}

// Zero-Allocation Time-Domain Pitch Modulation
JNIEXPORT void JNICALL Java_fastaudioprocess_FastAudioProcess_pitchShiftNative(JNIEnv* env, jclass clazz, jfloatArray sampleArray, jfloat semitones, jint sampleRate) {
    if (!sampleArray || semitones == 0.0f || sampleRate <= 0) return;
    jsize len = env->GetArrayLength(sampleArray);
    if (len <= 0) return;

    float* samples = (float*)env->GetPrimitiveArrayCritical(sampleArray, NULL);
    if (!samples) return;

    float safeSemitones = semitones;
    if (safeSemitones < -24.0f) safeSemitones = -24.0f;
    if (safeSemitones > 24.0f) safeSemitones = 24.0f;

    float ratio = std::pow(2.0f, safeSemitones / 12.0f);
    float rate = 1.0f - ratio;

    const int bufferSize = 16384;
    int writePtr = 0;
    float phase0 = 0.0f;
    float phase1 = (float)bufferSize / 2.0f;

    for (int i = 0; i < len; i++) {
        float input = samples[i];
        if (std::isnan(input) || std::isinf(input)) input = 0.0f;

        g_delayBuffer[writePtr] = input;

        float tap0 = (float)writePtr - phase0;
        if (tap0 < 0.0f) tap0 += bufferSize;
        float tap1 = (float)writePtr - phase1;
        if (tap1 < 0.0f) tap1 += bufferSize;

        int idx0_a = (int)tap0 % bufferSize;
        int idx0_b = (idx0_a + 1) % bufferSize;
        float frac0 = tap0 - (int)tap0;
        float sample0 = (1.0f - frac0) * g_delayBuffer[idx0_a] + frac0 * g_delayBuffer[idx0_b];

        int idx1_a = (int)tap1 % bufferSize;
        int idx1_b = (idx1_a + 1) % bufferSize;
        float frac1 = tap1 - (int)tap1;
        float sample1 = (1.0f - frac1) * g_delayBuffer[idx1_a] + frac1 * g_delayBuffer[idx1_b];

        float win = phase0 / (float)bufferSize;
        if (win < 0.0f) win = 0.0f;
        else if (win > 1.0f) win = 1.0f;

        samples[i] = sample0 * (1.0f - win) + sample1 * win;

        phase0 += rate;
        if (phase0 >= bufferSize) phase0 -= bufferSize;
        else if (phase0 < 0.0f) phase0 += bufferSize;

        phase1 += rate;
        if (phase1 >= bufferSize) phase1 -= bufferSize;
        else if (phase1 < 0.0f) phase1 += bufferSize;

        writePtr = (writePtr + 1) % bufferSize;
    }

    env->ReleasePrimitiveArrayCritical(sampleArray, samples, 0);
}

// Native Forward AVX2 FastFFT
JNIEXPORT void JNICALL Java_fastaudioprocess_FastFFT_fftNative(JNIEnv* env, jclass clazz, jfloatArray realArray, jfloatArray imagArray) {
    if (!realArray || !imagArray) return;
    jsize n = env->GetArrayLength(realArray);
    if (n <= 1 || (n & (n - 1)) != 0 || n > 16384) return;

    initFftTables();

    float* real = (float*)env->GetPrimitiveArrayCritical(realArray, NULL);
    float* imag = (float*)env->GetPrimitiveArrayCritical(imagArray, NULL);
    if (!real || !imag) {
        if (real) env->ReleasePrimitiveArrayCritical(realArray, real, JNI_ABORT);
        if (imag) env->ReleasePrimitiveArrayCritical(imagArray, imag, JNI_ABORT);
        return;
    }

    int p = 0;
    while ((1 << p) < n) p++;

    const int* bitRev = g_bitRevTable[p];
    for (int i = 0; i < n; i++) {
        int target = bitRev[i];
        if (i < target) {
            float tr = real[i]; real[i] = real[target]; real[target] = tr;
            float ti = imag[i]; imag[i] = imag[target]; imag[target] = ti;
        }
    }

    const float* twR = g_twiddleReal[p];
    const float* twI = g_twiddleImag[p];

    for (int len = 2; len <= n; len <<= 1) {
        int halfLen = len >> 1;
        int step = n / len;

        for (int i = 0; i < n; i += len) {
            for (int k = 0; k < halfLen; k++) {
                int twIdx = k * step;
                float wr = twR[twIdx];
                float wi = twI[twIdx];

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

    env->ReleasePrimitiveArrayCritical(realArray, real, 0);
    env->ReleasePrimitiveArrayCritical(imagArray, imag, 0);
}

// Native Inverse AVX2 FastFFT (IFFT)
JNIEXPORT void JNICALL Java_fastaudioprocess_FastFFT_ifftNative(JNIEnv* env, jclass clazz, jfloatArray realArray, jfloatArray imagArray) {
    if (!realArray || !imagArray) return;
    jsize n = env->GetArrayLength(realArray);
    if (n <= 1 || (n & (n - 1)) != 0 || n > 16384) return;

    initFftTables();

    float* real = (float*)env->GetPrimitiveArrayCritical(realArray, NULL);
    float* imag = (float*)env->GetPrimitiveArrayCritical(imagArray, NULL);
    if (!real || !imag) {
        if (real) env->ReleasePrimitiveArrayCritical(realArray, real, JNI_ABORT);
        if (imag) env->ReleasePrimitiveArrayCritical(imagArray, imag, JNI_ABORT);
        return;
    }

    // Invert imaginary signs
    for (int i = 0; i < n; i++) imag[i] = -imag[i];

    int p = 0;
    while ((1 << p) < n) p++;

    const int* bitRev = g_bitRevTable[p];
    for (int i = 0; i < n; i++) {
        int target = bitRev[i];
        if (i < target) {
            float tr = real[i]; real[i] = real[target]; real[target] = tr;
            float ti = imag[i]; imag[i] = imag[target]; imag[target] = ti;
        }
    }

    const float* twR = g_twiddleReal[p];
    const float* twI = g_twiddleImag[p];

    for (int len = 2; len <= n; len <<= 1) {
        int halfLen = len >> 1;
        int step = n / len;

        for (int i = 0; i < n; i += len) {
            for (int k = 0; k < halfLen; k++) {
                int twIdx = k * step;
                float wr = twR[twIdx];
                float wi = twI[twIdx];

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

    float invN = 1.0f / (float)n;
    for (int i = 0; i < n; i++) {
        real[i] *= invN;
        imag[i] = -imag[i] * invN;
    }

    env->ReleasePrimitiveArrayCritical(realArray, real, 0);
    env->ReleasePrimitiveArrayCritical(imagArray, imag, 0);
}

}