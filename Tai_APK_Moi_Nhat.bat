@echo off
chcp 65001 >nul
echo ========================================================
echo   📥 TẢI FILE CÀI ĐẶT PMAP.APK TỪ GITHUB ACTIONS
echo ========================================================
echo.
echo Đang kiểm tra bản build mới nhất trên GitHub chusaubanh/PMap...
gh run download --name PMap-Android-App --dir "%~dp0"
if exist "%~dp0PMap.apk" (
    echo.
    echo [THÀNH CÔNG] File PMap.apk đã được tải về máy của bạn tại:
    echo %~dp0PMap.apk
    echo.
    echo Bạn có thể gửi file PMap.apk qua điện thoại (LocalSend / Zalo / USB) để cài đặt!
) else (
    echo.
    echo [LƯU Ý] GitHub Actions đang trong quá trình biên dịch (khoảng 2 phút).
    echo Vui lòng chờ một chút rồi chạy lại file này!
)
pause
