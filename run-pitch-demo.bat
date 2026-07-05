@echo off
echo ========================================
echo FastAudioProcess Pitch Demo Runner
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
echo [2/3] Compiling PitchDemo...
cd examples\PitchDemo
call mvn compile >nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Demo compilation failed.
    cd ..\..
    exit /b 1
)

:: 3. Run the Demo
echo.
echo [3/3] Running PitchDemo...
call mvn exec:exec -Dexec.executable="java" -Dexec.args="--add-modules jdk.incubator.vector -classpath %%classpath fastaudioprocess.PitchDemo"

cd ..\..
echo.
echo ========================================
echo Demo Execution Complete
echo ========================================
pause
