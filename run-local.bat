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

REM --- 3. Sanity-check PostgreSQL is reachable on localhost:5434 ---
REM     (Optional — only works if netstat is on the PATH.)
where netstat >nul 2>nul
if %ERRORLEVEL%==0 (
    netstat -an | findstr /c:":5434 " >nul
    if errorlevel 1 (
        echo.
        echo [FAIL] Nothing is listening on localhost:5434.
        echo        Start your local PostgreSQL before rerunning.
        exit /b 1
    )
)

REM --- 4. Run Spring Boot ---
echo Starting wecms-api-main on port 8080 (profile: local)...
call gradlew.bat bootRun --args="--spring.profiles.active=local"

endlocal
