@echo off
chcp 65001 >nul
echo ========================================
echo   VoiceTransApp 编译安装启动脚本
echo ========================================

echo.
echo [1/4] 编译 Debug APK...
cd /d d:\workspace4ai\VoiceTransApp
call .\gradlew assembleDebug
if %errorlevel% neq 0 (
    echo [错误] 编译失败！
    pause
    exit /b 1
)

echo.
echo [2/4] 查找最新 APK...
for /f "delims=" %%f in ('dir /b /s /o-d "app\build\outputs\apk\debug\*.apk"') do (
    set "APK_PATH=%%f"
    goto :found
)
:found
if "%APK_PATH%"=="" (
    echo [错误] 未找到 APK 文件！
    pause
    exit /b 1
)
echo APK 路径: %APK_PATH%

echo.
echo [3/4] 安装 APK 到设备...
adb install -r "%APK_PATH%"
if %errorlevel% neq 0 (
    echo [错误] 安装失败！请检查设备是否已连接。
    echo 运行 adb devices 查看已连接设备。
    pause
    exit /b 1
)

echo.
echo [4/4] 启动 App...
adb shell am start -n com.aibabel.voicetrans/.ui.MainActivity

echo.
echo ========================================
echo   完成！App 已启动
echo ========================================
pause
