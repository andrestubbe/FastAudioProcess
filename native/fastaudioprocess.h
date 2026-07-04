#ifndef FASTAUDIOPROCESS_H
#define FASTAUDIOPROCESS_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

// Export declarations (Matches fastaudioprocess.def)
JNIEXPORT void JNICALL Java_fastaudioprocess_FastAudioProcess_doSomethingNative(JNIEnv* env, jobject obj);

#ifdef __cplusplus
}
#endif

#endif // FASTAUDIOPROCESS_H
