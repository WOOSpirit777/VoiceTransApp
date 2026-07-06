@echo off
echo ========================================
echo   VoiceTransApp Build & Install & Run
echo ========================================

echo.
echo [1/4] Building Debug APK...
cd /d d:\workspace4ai\VoiceTransApp
call .\gradlew assembleDebug
if %errorlevel% neq 0 (
    echo [ERROR] Build failed!
    pause
    exit /b 1
)

echo.
echo [2/4] Finding latest APK...
for /f "delims=" %%f in ('dir /b /s /o-d "app\build\outputs\apk\debug\*.apk"') do (
    set "APK_PATH=%%f"
    goto :found
)
:found
if "%APK_PATH%"=="" (
    echo [ERROR] APK file not found!
    pause
    exit /b 1
)
echo APK path: %APK_PATH%

echo.
echo [3/4] Installing APK to device...
adb install -r "%APK_PATH%"
if %errorlevel% neq 0 (
    echo [ERROR] Install failed! Please check device connection.
    echo Run "adb devices" to see connected devices.
    pause
    exit /b 1
)

echo.
echo [4/4] Starting App...
adb shell am start -n com.aibabel.voicetrans/.ui.MainActivity

echo.
echo ========================================
echo   Done! App started successfully
echo ========================================
pause
