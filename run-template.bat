@echo off
title easy-bi Server
set JAVA_OPTS=-Xms256m -Xmx2g -Dfile.encoding=UTF-8 -Dspring.profiles.active=prod

:menu
cls
echo.
echo  ============================================
echo    easy-bi Server
echo  ============================================
echo    Port: 8080
echo    Data: %%USERPROFILE%%\.easy-bi\
echo  ============================================
echo.
echo    [1] Start server
echo    [2] Stop server
echo    [3] Restart server
echo    [4] Open browser
echo    [0] Exit
echo.
echo  ============================================
echo.
set /p choice=Select option: 

if "%choice%"=="1" goto start
if "%choice%"=="2" goto stop
if "%choice%"=="3" goto restart
if "%choice%"=="4" goto browser
if "%choice%"=="0" exit /b
goto menu

:start
call :stop >nul 2>&1
cls
echo Starting server...
start "" jre\bin\javaw.exe %JAVA_OPTS% -jar easy-bi.jar
timeout /t 8 /nobreak >nul
echo.
echo Server started. Opening browser...
timeout /t 2 /nobreak >nul
goto browser

:stop
taskkill /f /im javaw.exe >nul 2>&1
exit /b

:restart
call :stop
timeout /t 2 /nobreak >nul
goto start

:browser
start "" http://localhost:8080
goto menu
