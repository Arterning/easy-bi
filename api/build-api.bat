@echo off
setlocal
set MAVEN_HOME=..\apache-maven-3.9.9

echo === Building BI API jar ===
call "%MAVEN_HOME%\bin\mvn.cmd" -f pom.xml clean package -DskipTests -q
if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    exit /b %ERRORLEVEL%
)
echo === Build success ===
echo Jar: target\bi-api-1.0.0.jar
