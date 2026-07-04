@echo off
echo ========================================
echo FastAudioProcess Silero VAD Demo Runner
echo ========================================

:: 1. Compile and package the parent project
echo.
echo [1/3] Packaging parent library...
call mvn clean package -DskipTests >nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Parent compilation failed.
    exit /b 1
)

:: 2. Compile the Demo project
echo.
echo [2/3] Compiling VADDemo...
cd examples\VADDemo
call mvn compile >nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Demo compilation failed.
    cd ..\..
    exit /b 1
)

:: 3. Run the Demo
echo.
echo [3/3] Running VADDemo...
java --add-modules jdk.incubator.vector -cp "target\classes;C:\Users\andre\.m2\repository\com\github\andrestubbe\FastCore\0.1.0\FastCore-0.1.0.jar;..\..\target\FastAudioProcess-0.1.0.jar;..\..\..\FastAudioPlayer\target\FastAudioPlayer-0.1.0.jar;C:\Users\andre\.m2\repository\com\googlecode\soundlibs\mp3spi\1.9.5.4\mp3spi-1.9.5.4.jar;C:\Users\andre\.m2\repository\com\googlecode\soundlibs\jlayer\1.0.1.4\jlayer-1.0.1.4.jar;C:\Users\andre\.m2\repository\com\googlecode\soundlibs\tritonus-share\0.3.7.4\tritonus-share-0.3.7.4.jar;C:\Users\andre\.m2\repository\com\microsoft\onnxruntime\onnxruntime\1.18.0\onnxruntime-1.18.0.jar" fastaudioprocess.VADDemo

cd ..\..
echo.
echo ========================================
echo Demo Execution Complete
echo ========================================
pause
