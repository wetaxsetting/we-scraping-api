@echo off
rem ============================================================
rem  fix-flyway-checksums.bat
rem
rem  One-time repair: update the V86 and V87 checksum records
rem  in flyway_schema_history on the shared dev DB so they match
rem  the current local file content.
rem
rem  Why needed: the comment header in V86/V87 was edited after
rem  those migrations were already applied (V84->V86, V85->V87
rem  rename + comment update), causing Flyway to report:
rem    Migration checksum mismatch for migration version 86
rem      Applied to database : 1205819954
rem      Resolved locally    : -1421003009
rem    Migration checksum mismatch for migration version 87
rem      Applied to database : 1540872974
rem      Resolved locally    : 225307222
rem
rem  Usage: run once from a developer machine that can reach
rem         172.168.5.172:5432.  After this script succeeds,
rem         bootRun will pass Flyway validation.
rem ============================================================

set PGHOST=172.168.5.172
set PGPORT=5432
set PGDATABASE=wecmsdevdb
set PGUSER=wecmsdev_owner
set PGPASSWORD=P2CKJpDAnO#vUM146W$

rem Try psql from common installation paths
set PSQL=
for %%P in (
    "C:\Program Files\PostgreSQL\17\bin\psql.exe"
    "C:\Program Files\PostgreSQL\16\bin\psql.exe"
    "C:\Program Files\PostgreSQL\15\bin\psql.exe"
    "C:\Program Files\PostgreSQL\14\bin\psql.exe"
    "C:\Program Files\PostgreSQL\13\bin\psql.exe"
    "C:\Program Files\PostgreSQL\12\bin\psql.exe"
) do (
    if exist %%P set PSQL=%%~P
)

if "%PSQL%"=="" (
    echo ERROR: psql.exe not found in common PostgreSQL installation paths.
    echo.
    echo Please run the following SQL manually against wecmsdevdb:
    echo.
    echo   UPDATE flyway_schema_history SET checksum = -1421003009 WHERE version = '86';
    echo   UPDATE flyway_schema_history SET checksum =  225307222  WHERE version = '87';
    echo.
    exit /b 1
)

echo Using psql at: %PSQL%
echo Connecting to %PGHOST%:%PGPORT%/%PGDATABASE% as %PGUSER% ...

"%PSQL%" -h %PGHOST% -p %PGPORT% -d %PGDATABASE% -U %PGUSER% -c ^
  "UPDATE flyway_schema_history SET checksum = -1421003009 WHERE version = '86'; UPDATE flyway_schema_history SET checksum = 225307222 WHERE version = '87';"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo SUCCESS: V86 and V87 checksums updated. You can now run bootRun.
) else (
    echo.
    echo FAILED: Check the connection details above and try again.
    exit /b 1
)
