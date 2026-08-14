@echo off
chcp 65001 >nul

echo ========================================
echo FastAudioProcess Native Library Builder
echo ========================================

set LIB_NAME=fastaudioprocess

set "VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
if exist "%VSWHERE%" (
    for /f "usebackq tokens=*" %%i in (`"%VSWHERE%" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do (
        set "VS_PATH=%%i"
    )
)

if not defined VS_PATH (
    if exist "C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build\vcvarsall.bat" (
        set "VS_PATH=C:\Program Files\Microsoft Visual Studio\18\Community"
    ) else if exist "C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat" (
        set "VS_PATH=C:\Program Files\Microsoft Visual Studio\2022\Community"
    )
)

if not defined VS_PATH (
    echo ERROR: Visual Studio not found!
    exit /b 1
)

echo Found Visual Studio at: %VS_PATH%

if not defined JAVA_HOME (
    if exist "C:\Program Files\Java\jdk-25.0.3" (
        set "JAVA_HOME=C:\Program Files\Java\jdk-25.0.3"
    ) else if exist "C:\Program Files\Java\jdk-21.0.12" (
        set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.12"
    ) else if exist "C:\Program Files\Java\latest" (
        set "JAVA_HOME=C:\Program Files\Java\latest"
    )
)

if not defined JAVA_HOME (
    echo ERROR: JAVA_HOME not set!
    exit /b 1
)

echo Using JAVA_HOME: %JAVA_HOME%

if exist "%VS_PATH%\VC\Auxiliary\Build\vcvars64.bat" (
    call "%VS_PATH%\VC\Auxiliary\Build\vcvars64.bat"
) else if exist "%VS_PATH%\VC\Auxiliary\Build\vcvarsall.bat" (
    call "%VS_PATH%\VC\Auxiliary\Build\vcvarsall.bat" x64
)

if not exist build mkdir build

cl.exe /O2 /arch:AVX2 /D_CRT_SECURE_NO_WARNINGS /W3 /MD /EHsc /LD ^
   /I "%JAVA_HOME%\include" ^
   /I "%JAVA_HOME%\include\win32" ^
   /Fo:build\ ^
   /Fe:build\%LIB_NAME%.dll ^
   native\*.cpp ^
   user32.lib gdi32.lib shcore.lib advapi32.lib dwmapi.lib

if %ERRORLEVEL% == 0 (
    if not exist src\main\resources\native mkdir src\main\resources\native
    if not exist src\main\resources\win32-x86-64 mkdir src\main\resources\win32-x86-64
    copy build\fastaudioprocess.dll src\main\resources\native\fastaudioprocess.dll /Y
    copy build\fastaudioprocess.dll src\main\resources\win32-x86-64\fastaudioprocess.dll /Y
    copy build\fastaudioprocess.dll target\classes\native\fastaudioprocess.dll /Y 2>nul
    echo.
    echo [SUCCESS] DLL built and copied to resources!
) else (
    echo.
    echo [FAILED] Compilation failed.
    exit /b 1
)
