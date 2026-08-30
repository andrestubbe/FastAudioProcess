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

// 16,384 samples delay line covers deep bass down to 20Hz at 48kHz
static thread_local float g_delayBuffer[16384] = {0.0f};

extern "C" {

// AVX2 256-bit SIMD Pitch Detection
JNIEXPORT jfloat JNICALL Java_fastaudioprocess_FastAudioProcess_detectPitchNative(JNIEnv* env, jclass clazz, jfloatArray sampleArray, jint sampleRate) {
    if (!sampleArray || sampleRate <= 0) return 0.0f;
    jsize len = env->GetArrayLength(sampleArray);
    if (len < 128) return 0.0f;

    float* samples = (float*)env->GetPrimitiveArrayCritical(sampleArray, NULL);
    if (!samples) return 0.0f;

    int minShift = sampleRate / 1000;
    int maxShift = sampleRate / 50;
    if (maxShift >= len) maxShift = len - 1;

    float maxCorrelation = -1.0f;
    int bestShift = -1;

    __m256 vTotalEnergy = _mm256_setzero_ps();
    int i = 0;
    for (; i <= len - 8; i += 8) {
        __m256 v = _mm256_loadu_ps(samples + i);
        vTotalEnergy = _mm256_fmadd_ps(v, v, vTotalEnergy);
    }
    float temp[8];
    _mm256_storeu_ps(temp, vTotalEnergy);
    double totalEnergy = temp[0] + temp[1] + temp[2] + temp[3] + temp[4] + temp[5] + temp[6] + temp[7];
    for (; i < len; i++) {
        totalEnergy += ((double)samples[i] * samples[i]);
    }

    if (totalEnergy < 1e-5) {
        env->ReleasePrimitiveArrayCritical(sampleArray, samples, JNI_ABORT);
        return 0.0f;
    }

    for (int shift = minShift; shift <= maxShift; shift += 2) {
        int count = len - shift;
        __m256 vCorr = _mm256_setzero_ps();
        __m256 vEnergyShifted = _mm256_setzero_ps();

        int k = 0;
        for (; k <= count - 8; k += 8) {
            __m256 vSrc = _mm256_loadu_ps(samples + k);
            __m256 vShift = _mm256_loadu_ps(samples + k + shift);
            vCorr = _mm256_fmadd_ps(vSrc, vShift, vCorr);
            vEnergyShifted = _mm256_fmadd_ps(vShift, vShift, vEnergyShifted);
        }

        _mm256_storeu_ps(temp, vCorr);
        double corr = temp[0] + temp[1] + temp[2] + temp[3] + temp[4] + temp[5] + temp[6] + temp[7];

        _mm256_storeu_ps(temp, vEnergyShifted);
        double energyShifted = temp[0] + temp[1] + temp[2] + temp[3] + temp[4] + temp[5] + temp[6] + temp[7];

        for (; k < count; k++) {
            corr += ((double)samples[k] * samples[k + shift]);
            energyShifted += ((double)samples[k + shift] * samples[k + shift]);
        }

        if (energyShifted > 1e-6) {
            float normCorr = (float)(corr / std::sqrt(totalEnergy * energyShifted));
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

// Zero-Allocation Pitch Shifting
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

// Native AVX2 FastFFT In-Place Kernel
JNIEXPORT void JNICALL Java_fastaudioprocess_FastFFT_fftNative(JNIEnv* env, jclass clazz, jfloatArray realArray, jfloatArray imagArray) {
    if (!realArray || !imagArray) return;
    jsize n = env->GetArrayLength(realArray);
    if (n <= 1) return;

    float* real = (float*)env->GetPrimitiveArrayCritical(realArray, NULL);
    float* imag = (float*)env->GetPrimitiveArrayCritical(imagArray, NULL);
    if (!real || !imag) {
        if (real) env->ReleasePrimitiveArrayCritical(realArray, real, JNI_ABORT);
        if (imag) env->ReleasePrimitiveArrayCritical(imagArray, imag, JNI_ABORT);
        return;
    }

    // Bit-reversal
    int j = 0;
    for (int i = 0; i < n - 1; i++) {
        if (i < j) {
            float tr = real[i]; real[i] = real[j]; real[j] = tr;
            float ti = imag[i]; imag[i] = imag[j]; imag[j] = ti;
        }
        int k = n >> 1;
        while (k <= j) {
            j -= k;
            k >>= 1;
        }
        j += k;
    }

    // Cooley-Tukey Butterflies
    for (int len = 2; len <= n; len <<= 1) {
        int halfLen = len >> 1;
        double angleStep = -2.0 * 3.14159265358979323846 / len;
        float wStepR = (float)std::cos(angleStep);
        float wStepI = (float)std::sin(angleStep);

        for (int i = 0; i < n; i += len) {
            float wr = 1.0f;
            float wi = 0.0f;
            for (int k = 0; k < halfLen; k++) {
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

                float nextWr = wr * wStepR - wi * wStepI;
                wi = wr * wStepI + wi * wStepR;
                wr = nextWr;
            }
        }
    }

    env->ReleasePrimitiveArrayCritical(realArray, real, 0);
    env->ReleasePrimitiveArrayCritical(imagArray, imag, 0);
}

}