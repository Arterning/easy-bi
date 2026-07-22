@echo off


REM ====== AI API Key (set your key here) ======
if "%DEEPSEEK_API_KEY%"=="" (
    echo [WARN] DEEPSEEK_API_KEY is not set. AI features will not work.
    echo        Set it: set DEEPSEEK_API_KEY=sk-your-key-here
    echo.
)


..\apache-maven-3.9.9\bin\mvn.cmd -f pom.xml spring-boot:run
