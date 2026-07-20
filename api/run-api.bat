@echo off
setlocal
set JAVA_HOME=C:\Users\ningh\.jdks\openjdk-22.0.1
set JAR=api\target\bi-api-1.0.0.jar

if not exist "%JAR%" (
    echo Jar not found, building first...
    call build-api.bat
    if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%
)

echo === Starting BI API ===
echo Port: 8080
echo H2 Console: http://localhost:8080/h2-console
echo Heap: -Xms512m -Xmx4g
echo.

java -Xms512m -Xmx4g -Dfile.encoding=UTF-8 -jar "%JAR%"
