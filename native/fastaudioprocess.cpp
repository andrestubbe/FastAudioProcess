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

// Persistent thread-local delay line buffer for zero-alloc pitch shifting
static thread_local float g_delayBuffer[4096] = {0.0f};

extern "C" {

// AVX2 256-bit SIMD Pitch Detection via Vectorized Normalized Autocorrelation
JNIEXPORT jfloat JNICALL Java_fastaudioprocess_FastAudioProcess_detectPitchNative(JNIEnv* env, jclass clazz, jfloatArray sampleArray, jint sampleRate) {
    if (!sampleArray || sampleRate <= 0) return 0.0f;
    jsize len = env->GetArrayLength(sampleArray);
    if (len < 128) return 0.0f;

    float* samples = (float*)env->GetPrimitiveArrayCritical(sampleArray, NULL);
    if (!samples) return 0.0f;

    int minShift = sampleRate / 1000; // ~1000 Hz max pitch
    int maxShift = sampleRate / 50;   // ~50 Hz min pitch
    if (maxShift >= len) maxShift = len - 1;

    float maxCorrelation = -1.0f;
    int bestShift = -1;

    // Precalculate baseline total frame energy using AVX2 SIMD
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

    // Vectorized Pitch Search
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

// Zero-Allocation Pitch Shifting utilizing persistent thread-local delay line
JNIEXPORT void JNICALL Java_fastaudioprocess_FastAudioProcess_pitchShiftNative(JNIEnv* env, jclass clazz, jfloatArray sampleArray, jfloat semitones, jint sampleRate) {
    if (!sampleArray || semitones == 0.0f || sampleRate <= 0) return;
    jsize len = env->GetArrayLength(sampleArray);
    if (len <= 0) return;

    float* samples = (float*)env->GetPrimitiveArrayCritical(sampleArray, NULL);
    if (!samples) return;

    float ratio = std::pow(2.0f, semitones / 12.0f);
    float rate = 1.0f - ratio;

    const int bufferSize = 4096;
    int writePtr = 0;
    float phase0 = 0.0f;
    float phase1 = (float)bufferSize / 2.0f;

    for (int i = 0; i < len; i++) {
        float input = samples[i];
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

    env->ReleasePrimitiveArrayCritical(sampleArray, samples, 0); // Commit changes back to Java array
}

}