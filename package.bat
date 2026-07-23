@echo off
set JAVA_HOME=C:\Users\ningh\.jdks\openjdk-22.0.1
set MAVEN_HOME=%~dp0apache-maven-3.9.9
set VERSION=1.0.0

echo.
echo ============================================
echo   easy-bi Package Script
echo ============================================
echo.

echo [1/4] Building frontend...
call pnpm --dir web build
if errorlevel 1 goto :fail_frontend

echo.
echo [2/4] Copying frontend to backend...
if exist api\src\main\resources\static rmdir /s /q api\src\main\resources\static
xcopy web\dist api\src\main\resources\static\ /E /Q /Y >nul
echo       Done.

echo.
echo [3/4] Building Spring Boot jar...
call "%MAVEN_HOME%\bin\mvn.cmd" -f api\pom.xml clean package -DskipTests -q
if errorlevel 1 goto :fail_jar
echo       Done.

echo.
echo [4/4] Assembling portable distribution...
taskkill /f /im javaw.exe >nul 2>&1
timeout /t 1 /nobreak >nul
if exist dist rmdir /s /q dist
mkdir dist\easy-bi

REM Copy the fat jar
copy api\target\bi-api-%VERSION%.jar dist\easy-bi\easy-bi.jar >nul

REM Create minimal JRE with jlink
echo       Creating JRE (jlink)...
call jlink --output dist\easy-bi\jre --add-modules java.base,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.security.jgss,java.sql,java.transaction.xa,java.xml,jdk.unsupported,jdk.management --strip-debug --no-man-pages --no-header-files --compress zip-6 2>nul
if errorlevel 1 goto :fail_jlink

REM Copy run.bat
copy %~dp0run-template.bat dist\easy-bi\run.bat >nul

echo.
echo ============================================
echo   Package complete!
echo   Folder: dist\easy-bi\
echo ============================================
echo.
echo   Contents:
echo     easy-bi.jar     Application
echo     jre\            Java Runtime (bundled)
echo     run.bat         Double-click to start
echo.
echo   To distribute: zip the dist\easy-bi\ folder
echo   Client: unzip, double-click run.bat
echo.
goto :end

:fail_frontend
echo Frontend build failed!
exit /b 1

:fail_jar
echo Jar build failed!
exit /b 1

:fail_jlink
echo jlink failed! Check JAVA_HOME: %JAVA_HOME%
exit /b 1

:end
