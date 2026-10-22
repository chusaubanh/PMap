@echo off
chcp 65001 >nul
title PMap - Ban Do va Radar Xe May Toan Quoc
echo ========================================================
echo   🏍️ KHỞI CHẠY PMAP WEB HUD - BẢN ĐỒ RADAR XE MÁY
echo ========================================================
echo.
echo Đang kiểm tra Node.js...

where node >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo Đang khởi động máy chủ cục bộ PMap tại http://localhost:5500...
    start http://localhost:5500
    node "%~dp0server.js"
) else (
    echo Đang mở trực tiếp giao diện PMap...
    start "" "%~dp0web-hud\index.html"
    pause
)
