@echo off
call "C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build\vcvarsall.bat" x64
cd /d "C:\Users\andre\Documents\2026-08-17-Work-FastJava\FastAudioProcess\native"
cl.exe /O2 /arch:AVX2 /fp:fast /LD /I"C:\Program Files\java\jdk-21.0.12.1\include" /I"C:\Program Files\java\jdk-21.0.12.1\include\win32" fastaudioprocess.cpp /Fe:fastaudioprocess.dll