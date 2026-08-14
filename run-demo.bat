@echo off
echo [FastAudioProcess] Building Native Library...
call compile.bat
if %ERRORLEVEL% NEQ 0 exit /b 1

echo [FastAudioProcess] Building Core Project...
call mvn clean install -DskipTests -q
if %ERRORLEVEL% NEQ 0 exit /b 1

echo [FastAudioProcess] Running Demo...
cd examples\Demo
call mvn package -DskipTests -q
java --add-modules jdk.incubator.vector -cp "target\demo-0.1.1.jar;..\..\target\FastAudioProcess-0.1.1.jar;%USERPROFILE%\.m2\repository\com\googlecode\soundlibs\mp3spi\1.9.5.4\mp3spi-1.9.5.4.jar;%USERPROFILE%\.m2\repository\com\googlecode\soundlibs\tritonus-share\0.3.7.4\tritonus-share-0.3.7.4.jar;%USERPROFILE%\.m2\repository\com\googlecode\soundlibs\jlayer\1.0.1.4\jlayer-1.0.1.4.jar" fastaudioprocess.demo.Demo
cd ..\..
pause
