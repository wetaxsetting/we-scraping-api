#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Start wecms-api-main locally with the right JDK + env vars.
# Fails fast (with a clear message) if anything is misconfigured.
# ---------------------------------------------------------------------------
set -eu

# --- 1. Pin JAVA_HOME to Java 21 if Temurin is present at the default path ---
DEFAULT_JDK="/c/Program Files/Eclipse Adoptium/jdk-21.0.10.7-hotspot"
if [ -d "$DEFAULT_JDK" ]; then
    export JAVA_HOME="$DEFAULT_JDK"
elif [ -z "${JAVA_HOME:-}" ]; then
    echo "[FAIL] JAVA_HOME is not set and Temurin JDK 21 is not at $DEFAULT_JDK" >&2
    echo "       Install a JDK 21 (Temurin preferred) and/or set JAVA_HOME." >&2
    exit 1
fi

# --- 2. Required env vars ---
if [ -z "${WECMS_DB_PASSWORD:-}" ]; then
    echo "[FAIL] WECMS_DB_PASSWORD is not set." >&2
    echo "       export WECMS_DB_PASSWORD='your-postgres-password'" >&2
    echo "       (Password is stored in the team's secure channel — do NOT commit it.)" >&2
    exit 1
fi
if [ -z "${WECMS_JWT_SECRET:-}" ]; then
    echo "[FAIL] WECMS_JWT_SECRET is not set." >&2
    echo "       export WECMS_JWT_SECRET='some-long-random-string-32-chars-min'" >&2
    exit 1
fi

# --- 3. Sanity-check PostgreSQL on localhost:5434 ---
if ! (exec 3<>/dev/tcp/localhost/5434) 2>/dev/null; then
    echo "[FAIL] Nothing is listening on localhost:5434." >&2
    echo "       Start your local PostgreSQL before rerunning." >&2
    exit 1
fi
exec 3>&- 2>/dev/null || true

# --- 4. Run Spring Boot ---
echo "Starting wecms-api-main on port 8080 (profile: local)..."
exec ./gradlew bootRun --args='--spring.profiles.active=local'
