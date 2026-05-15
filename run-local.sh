#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Start wecms-api-main locally with the right JDK + env vars.
# Fails fast (with a clear message) if anything is misconfigured.
# ---------------------------------------------------------------------------
set -eu

# --- 1. Pin JAVA_HOME to Java 21 if Temurin is present at the default path ---
DEFAULT_JDK="/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot"
if [ -d "$DEFAULT_JDK" ]; then
    export JAVA_HOME="$DEFAULT_JDK"
elif [ -z "${JAVA_HOME:-}" ]; then
    echo "[FAIL] JAVA_HOME is not set and Temurin JDK 21 is not at $DEFAULT_JDK" >&2
    echo "       Install a JDK 21 (Temurin preferred) and/or set JAVA_HOME." >&2
    exit 1
fi

# --- 2. Optional env vars — application-local.yml has safe local defaults ---
if [ -z "${WECMS_DB_PASSWORD:-}" ]; then
    echo "[INFO] WECMS_DB_PASSWORD not set — using default from application-local.yml (password: 1234)"
fi
if [ -z "${WECMS_JWT_SECRET:-}" ]; then
    echo "[INFO] WECMS_JWT_SECRET not set — using default from application-local.yml"
fi

# --- 3. Sanity-check local PostgreSQL is reachable ---
if ! (exec 3<>/dev/tcp/localhost/5432) 2>/dev/null; then
    echo "[FAIL] Local PostgreSQL at localhost:5432 is not reachable." >&2
    echo "       Make sure PostgreSQL is running before rerunning." >&2
    exit 1
fi
exec 3>&- 2>/dev/null || true

# --- 4. Run Spring Boot ---
echo "Starting wecms-api-main on port 8080 (profile: local)..."
exec ./gradlew bootRun --args='--spring.profiles.active=local'
