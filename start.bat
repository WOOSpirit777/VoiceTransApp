@echo off
echo Starting VoiceTransApp...
adb shell am start -n com.aibabel.voicetrans/.ui.MainActivity
if %errorlevel% neq 0 (
    echo [ERROR] Failed to start app! Check device connection.
    pause
    exit /b 1
)
echo App started successfully.
timeout /t 2 >nul
