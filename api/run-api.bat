@echo off
setlocal
set JAR=target\bi-api-1.0.0.jar

if not exist "%JAR%" (
    echo Jar not found, building first...
    call build-api.bat
    if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%
)

REM ====== AI API Key (set your key here) ======
if "%DEEPSEEK_API_KEY%"=="" (
    echo [WARN] DEEPSEEK_API_KEY is not set. AI features will not work.
    echo        Set it: set DEEPSEEK_API_KEY=sk-your-key-here
    echo.
)

echo === Starting easy-bi ===
echo Port: 8080
echo H2 Console: http://localhost:8080/h2-console
echo Heap: -Xms512m -Xmx4g
echo.

java -Xms512m -Xmx4g -Dfile.encoding=UTF-8 -jar "%JAR%"
