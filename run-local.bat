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

REM --- 2. Require .env.local — it is the ONLY source of configuration ---
REM  Since the 2026-07-30 config migration every placeholder in the yml files is a bare
REM  ${VAR} with no default (docs/deployment/config-bare-placeholder-migration.md), so all
REM  76 variables come from .env.local. It is loaded by the bootRun task in build.gradle,
REM  NOT here: cmd cannot hold an empty environment variable (`set "VAR="` deletes it) and
REM  several variables are legitimately empty. Gradle's ProcessBuilder env map can.
set "ENV_FILE=%~dp0.env.local"
if not exist "%ENV_FILE%" (
    echo.
    echo [FAIL] %ENV_FILE% not found.
    echo        The yml files carry no defaults — the app cannot start without it.
    echo        Copy .env.local.example to .env.local, then fill in every value (keys only).
    exit /b 1
)

REM --- 3. Sanity-check the DB in .env.local is reachable (host is remote + VPN-gated) ---
powershell -NoProfile -Command "$u = (Select-String -Path '%ENV_FILE%' -Pattern '^WECMS_DB_URL=' | Select-Object -First 1).Line -replace '^WECMS_DB_URL=',''; if ($u -notmatch '//([^:/]+):(\d+)/') { Write-Host '[WARN] WECMS_DB_URL not parseable — skipping reachability check'; exit 0 }; $h = $matches[1]; $p = $matches[2]; if (-not (Test-NetConnection -ComputerName $h -Port $p -InformationLevel Quiet -WarningAction SilentlyContinue)) { Write-Host \"[FAIL] PostgreSQL at ${h}:${p} is not reachable.\"; exit 1 }"
if errorlevel 1 (
    echo        The shared dev DB ^(172.168.5.172^) needs the office network or VPN.
    echo        Check the connection, then rerun.
    exit /b 1
)

REM --- 4. Run Spring Boot ---
echo Starting wecms-api on port 8080 (profile: local)...
call gradlew.bat bootRun --args="--spring.profiles.active=local"

endlocal
