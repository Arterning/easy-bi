@echo off
setlocal
set JAVA_HOME=C:\Users\ningh\.jdks\openjdk-22.0.1
set MAVEN_HOME=%~dp0apache-maven-3.9.9

echo === Building BI API jar ===
call "%MAVEN_HOME%\bin\mvn.cmd" -f api\pom.xml clean package -DskipTests -q
if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    exit /b %ERRORLEVEL%
)
echo === Build success ===
echo Jar: api\target\bi-api-1.0.0.jar
