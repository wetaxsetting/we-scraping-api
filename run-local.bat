@echo off
REM ---------------------------------------------------------------------------
REM  Start wecms-api locally with the right JDK + env vars.
REM  Fails fast (with a clear message) if anything is misconfigured.
REM ---------------------------------------------------------------------------

setlocal

REM --- 1. Pin JAVA_HOME to Java 21 (Spring Boot 3.5 needs 17+, project targets 21) ---
if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
) else (
    echo [WARN] jdk-21.0.11.10-hotspot not found at its default Adoptium path.
    echo        If you have another JDK 21, set JAVA_HOME manually and rerun.
    echo        Current JAVA_HOME: %JAVA_HOME%
)

REM --- 2. Optional env vars — application-local.yml has safe local defaults ---
if "%WECMS_DB_PASSWORD%"=="" (
    echo [INFO] WECMS_DB_PASSWORD not set — using default from application-local.yml (password: 1234)
)
if "%WECMS_JWT_SECRET%"=="" (
    echo [INFO] WECMS_JWT_SECRET not set — using default from application-local.yml
)

REM --- 3. Sanity-check local PostgreSQL is reachable ---
powershell -NoProfile -Command "if (-not (Test-NetConnection -ComputerName 'localhost' -Port 5432 -InformationLevel Quiet -WarningAction SilentlyContinue)) { exit 1 }"
if errorlevel 1 (
    echo.
    echo [FAIL] Local PostgreSQL at localhost:5432 is not reachable.
    echo        Make sure PostgreSQL is running before rerunning.
    exit /b 1
)

REM --- 4. Run Spring Boot ---
echo Starting wecms-api on port 8080 (profile: local)...
call gradlew.bat bootRun --args="--spring.profiles.active=local"

endlocal
