#include "fastaudioprocess.h"
#include <windows.h>
#include <stdio.h>
#include <math.h>

/**
 * @file fastaudioprocess.cpp
 * @brief Native JNI implementation for FastAudioProcess
 */

// ============================================================================
// DLL Entry Point
// ============================================================================
BOOL APIENTRY DllMain(HMODULE hModule, DWORD ul_reason_for_call, LPVOID lpReserved) {
    switch (ul_reason_for_call) {
        case DLL_PROCESS_ATTACH:
            DisableThreadLibraryCalls(hModule);
            break;
        case DLL_PROCESS_DETACH:
            break;
    }
    return TRUE;
}

// ============================================================================
// JNI Implementations
// ============================================================================

/**
 * @brief Native method implementation example
 * 
 * @param env JNI environment pointer
 * @param obj Java instance object (or clazz for static methods)
 */
JNIEXPORT void JNICALL Java_fastaudioprocess_FastAudioProcess_doSomethingNative(JNIEnv* env, jobject obj) {
    printf("[DEBUG C++] doSomethingNative called!\n");
    fflush(stdout);
}

JNIEXPORT jfloat JNICALL Java_fastaudioprocess_FastAudioProcess_detectPitchNative(JNIEnv* env, jclass clazz, jfloatArray sampleArray, jint sampleRate) {
    if (!sampleArray) return 0.0f;
    jsize len = env->GetArrayLength(sampleArray);
    if (len < 128) return 0.0f;

    float* samples = (float*)env->GetPrimitiveArrayCritical(sampleArray, NULL);
    if (!samples) return 0.0f;

    int minShift = sampleRate / 1000; // ~1000Hz max pitch
    int maxShift = sampleRate / 50;   // ~50Hz min pitch
    if (maxShift >= len) maxShift = len - 1;

    float maxCorrelation = -1.0f;
    int bestShift = -1;

    for (int shift = minShift; shift <= maxShift; shift++) {
        float correlation = 0.0f;
        float energySource = 0.0f;
        float energyShifted = 0.0f;

        for (int i = 0; i < len - shift; i++) {
            correlation += samples[i] * samples[i + shift];
            energySource += samples[i] * samples[i];
            energyShifted += samples[i + shift] * samples[i + shift];
        }

        if (energySource > 0.0f && energyShifted > 0.0f) {
            float normCorrelation = correlation / sqrt(energySource * energyShifted);
            if (normCorrelation > maxCorrelation) {
                maxCorrelation = normCorrelation;
                bestShift = shift;
            }
        }
    }

    env->ReleasePrimitiveArrayCritical(sampleArray, samples, JNI_ABORT);

    if (maxCorrelation > 0.70f && bestShift > 0) {
        return (float)sampleRate / bestShift;
    }
    return 0.0f;
}

JNIEXPORT void JNICALL Java_fastaudioprocess_FastAudioProcess_pitchShiftNative(JNIEnv* env, jclass clazz, jfloatArray sampleArray, jfloat semitones, jint sampleRate) {
    if (!sampleArray || semitones == 0.0f) return;
    jsize len = env->GetArrayLength(sampleArray);
    if (len <= 0) return;

    float* samples = (float*)env->GetPrimitiveArrayCritical(sampleArray, NULL);
    if (!samples) return;

    // Pitch ratio = 2^(semitones / 12)
    float ratio = pow(2.0f, semitones / 12.0f);
    float rate = 1.0f - ratio;

    // Circular buffer setup for modulated delay lines
    int bufferSize = 2048;
    float* delayBuffer = new float[bufferSize]();
    int writePtr = 0;

    float phase0 = 0.0f;
    float phase1 = (float)bufferSize / 2.0f;

    for (int i = 0; i < len; i++) {
        float input = samples[i];
        delayBuffer[writePtr] = input;

        float tap0 = (float)writePtr - phase0;
        if (tap0 < 0) tap0 += bufferSize;
        float tap1 = (float)writePtr - phase1;
        if (tap1 < 0) tap1 += bufferSize;

        int idx0_a = (int)tap0 % bufferSize;
        int idx0_b = (idx0_a + 1) % bufferSize;
        float frac0 = tap0 - (int)tap0;
        float sample0 = (1.0f - frac0) * delayBuffer[idx0_a] + frac0 * delayBuffer[idx0_b];

        int idx1_a = (int)tap1 % bufferSize;
        int idx1_b = (idx1_a + 1) % bufferSize;
        float frac1 = tap1 - (int)tap1;
        float sample1 = (1.0f - frac1) * delayBuffer[idx1_a] + frac1 * delayBuffer[idx1_b];

        float win = phase0 / (float)bufferSize;
        if (win < 0.0f) win = 0.0f;
        if (win > 1.0f) win = 1.0f;

        samples[i] = sample0 * (1.0f - win) + sample1 * win;

        phase0 += rate;
        if (phase0 >= bufferSize) phase0 -= bufferSize;
        if (phase0 < 0) phase0 += bufferSize;

        phase1 += rate;
        if (phase1 >= bufferSize) phase1 -= bufferSize;
        if (phase1 < 0) phase1 += bufferSize;

        writePtr = (writePtr + 1) % bufferSize;
    }

    delete[] delayBuffer;

    env->ReleasePrimitiveArrayCritical(sampleArray, samples, 0); // Commit changes back to Java array
}

