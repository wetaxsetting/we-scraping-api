@echo off
REM ---------------------------------------------------------------------------
REM  Start wecms-api-main locally with the right JDK + env vars.
REM  Fails fast (with a clear message) if anything is misconfigured.
REM ---------------------------------------------------------------------------

setlocal

REM --- 1. Pin JAVA_HOME to Java 21 (Spring Boot 3.5 needs 17+, project targets 21) ---
if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"
) else (
    echo [WARN] jdk-21.0.10.7-hotspot not found at its default Adoptium path.
    echo        If you have another JDK 21, set JAVA_HOME manually and rerun.
    echo        Current JAVA_HOME: %JAVA_HOME%
)

REM --- 2. Required env vars ---
if "%WECMS_DB_PASSWORD%"=="" (
    echo.
    echo [FAIL] WECMS_DB_PASSWORD is not set.
    echo        Set it in this shell before rerunning:
    echo            set WECMS_DB_PASSWORD=your-postgres-password
    echo        The password is stored in your team's secure channel — it
    echo        must NOT be committed to git.
    exit /b 1
)

if "%WECMS_JWT_SECRET%"=="" (
    echo.
    echo [FAIL] WECMS_JWT_SECRET is not set.
    echo        Set it in this shell before rerunning (32+ characters):
    echo            set WECMS_JWT_SECRET=some-long-random-string-for-local-dev-only
    exit /b 1
)

REM --- 3. Sanity-check WeCMS dev DB server is reachable ---
powershell -NoProfile -Command "if (-not (Test-NetConnection -ComputerName '172.168.5.172' -Port 5432 -InformationLevel Quiet -WarningAction SilentlyContinue)) { exit 1 }"
if errorlevel 1 (
    echo.
    echo [FAIL] WeCMS dev DB server 172.168.5.172:5432 is not reachable.
    echo        Check VPN / network access before rerunning.
    exit /b 1
)

REM --- 4. Run Spring Boot ---
echo Starting wecms-api-main on port 8080 (profile: local)...
call gradlew.bat bootRun --args="--spring.profiles.active=local"

endlocal
