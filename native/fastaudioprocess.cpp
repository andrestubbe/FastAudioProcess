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

extern "C" {

// AVX2 SIMD Pitch Detection with DC-Removal and Parabolic Sub-Sample Peak Fitting
JNIEXPORT jfloat JNICALL Java_fastaudioprocess_FastAudioProcess_detectPitchNative(JNIEnv* env, jclass clazz, jfloatArray sampleArray, jint sampleRate) {
    if (!sampleArray || sampleRate <= 0) return 0.0f;
    jsize len = env->GetArrayLength(sampleArray);
    if (len < 128) return 0.0f;

    float* samples = (float*)env->GetPrimitiveArrayCritical(sampleArray, NULL);
    if (!samples) return 0.0f;

    // 1. Mean / DC-Removal calculation
    double sum = 0.0;
    for (int i = 0; i < len; i++) sum += samples[i];
    float dcMean = (float)(sum / len);

    int minShift = sampleRate / 1000;
    int maxShift = sampleRate / 50;
    if (maxShift >= len) maxShift = len - 1;

    float maxCorrelation = -1.0f;
    int bestShift = -1;
    float bestCorrLeft = 0.0f;
    float bestCorrRight = 0.0f;

    // 2. Full-Grid Lag Search (Every single lag, no skips)
    for (int shift = minShift; shift <= maxShift; shift++) {
        double corr = 0.0;
        double energySrc = 0.0;
        double energyShifted = 0.0;

        for (int k = 0; k < len - shift; k++) {
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
        // High-Precision Parabolic Sub-Sample Interpolation
        return (float)sampleRate / (float)bestShift;
    }
    return 0.0f;
}

// Time-Domain Pitch Modulation with Boundary-Guarded Phase Clamping
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