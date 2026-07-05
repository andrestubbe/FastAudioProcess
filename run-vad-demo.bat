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
call mvn exec:exec

cd ..\..
echo.
echo ========================================
echo Demo Execution Complete
echo ========================================
pause
