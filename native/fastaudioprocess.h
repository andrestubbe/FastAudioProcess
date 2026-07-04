#ifndef FASTAUDIOPROCESS_H
#define FASTAUDIOPROCESS_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

// Export declarations (Matches fastaudioprocess.def)
JNIEXPORT void JNICALL Java_fastaudioprocess_FastAudioProcess_doSomethingNative(JNIEnv* env, jobject obj);
JNIEXPORT jfloat JNICALL Java_fastaudioprocess_FastAudioProcess_detectPitchNative(JNIEnv* env, jclass clazz, jfloatArray sampleArray, jint sampleRate);
JNIEXPORT void JNICALL Java_fastaudioprocess_FastAudioProcess_pitchShiftNative(JNIEnv* env, jclass clazz, jfloatArray sampleArray, jfloat semitones, jint sampleRate);

#ifdef __cplusplus
}
#endif

#endif // FASTAUDIOPROCESS_H
