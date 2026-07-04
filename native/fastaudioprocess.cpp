#include "fastaudioprocess.h"
#include <windows.h>
#include <stdio.h>

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
