#!/usr/bin/env bash
# =============================================================================
# apply-haengsung-env.sh
# -----------------------------------------------------------------------------
# Safely set the HAENGSUNG Oracle env vars in the wecms-api env files.
#
#   .env.local           Oracle OFF, creds blank, schedulers OFF
#   .env.sit             Oracle ON (172.168.5.23:1521/DEV, user wecms), schedulers OFF
#   .env.local.example   same as .env.local
#   .env.sit.example     Oracle OFF, placeholder URL/user, schedulers OFF
#
# Safety guarantees:
#   * Never prints any HAENGSUNG_ORACLE_PASSWORD value.
#   * Never overwrites an EXISTING HAENGSUNG_ORACLE_PASSWORD (any file).
#   * Keeps all three scheduler *_ENABLED flags = false.
#   * Backs up every file it touches as <file>.bak.<timestamp> first.
#   * Touches only HAENGSUNG_* keys — unrelated variables are left intact.
#   * Updates a key in place if present (comments preserved); appends if missing.
#
# Requires GNU sed (Git Bash on Windows / Linux). Run manually:
#   bash tmp/apply-haengsung-env.sh
# =============================================================================
set -eu

# Resolve the project root as the parent of this script's tmp/ dir, so the
# script works regardless of the current working directory.
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$SCRIPT_DIR")"
cd "$ROOT"

TS="$(date +%Y%m%d%H%M%S)"
CRON_DEFAULT='0 */5 * * * *'
HEADER_MARK='# HAENGSUNG Oracle ERP Integration'

updated_files=""   # space-separated list for the summary
skipped_files=""

# --- helpers ----------------------------------------------------------------

# Ensure the file ends with a newline before we append anything.
ensure_trailing_newline() {
  f="$1"
  [ -s "$f" ] || return 0
  if [ -n "$(tail -c1 "$f")" ]; then printf '\n' >> "$f"; fi
}

# Append the section header once (only if it is not already present).
append_header_once() {
  f="$1"
  if ! grep -qF "$HEADER_MARK" "$f"; then
    {
      printf '\n'
      printf '# =============================================================================\n'
      printf '%s\n' "$HEADER_MARK"
      printf '# =============================================================================\n'
    } >> "$f"
  fi
}

# set_kv FILE KEY VALUE
#   Replace the whole `KEY=...` line if present (preserving surrounding
#   comments/lines); otherwise append it under the section header.
#   NOTE: never call this for HAENGSUNG_ORACLE_PASSWORD.
set_kv() {
  f="$1"; key="$2"; val="$3"
  if grep -qE "^${key}=" "$f"; then
    # Escape the replacement for sed: backslash, the '#' delimiter, and '&'.
    esc="$(printf '%s' "$val" | sed -e 's/[\\#&]/\\&/g')"
    sed -i "s#^${key}=.*#${key}=${esc}#" "$f"
  else
    append_header_once "$f"
    printf '%s=%s\n' "$key" "$val" >> "$f"
  fi
}

# ensure_password_key FILE
#   Guarantee the password KEY exists, but NEVER overwrite an existing value.
#   If the key is absent, append it blank (value to be filled in by a human).
ensure_password_key() {
  f="$1"
  if ! grep -qE '^HAENGSUNG_ORACLE_PASSWORD=' "$f"; then
    append_header_once "$f"
    printf 'HAENGSUNG_ORACLE_PASSWORD=\n' >> "$f"
  fi
}

# password_status FILE -> echoes only "SET" or "EMPTY" (never the value).
password_status() {
  f="$1"
  line="$(grep -E '^HAENGSUNG_ORACLE_PASSWORD=' "$f" | head -n1 || true)"
  val="${line#HAENGSUNG_ORACLE_PASSWORD=}"
  if [ -n "$val" ]; then printf 'SET'; else printf 'EMPTY'; fi
}

# Apply the six scheduler keys (identical for every file): all OFF.
apply_schedulers() {
  f="$1"
  set_kv "$f" HAENGSUNG_SCHEDULED_IMPORT_ENABLED     false
  set_kv "$f" HAENGSUNG_SCHEDULED_IMPORT_CRON        "$CRON_DEFAULT"
  set_kv "$f" HAENGSUNG_EXCHANGE_RATE_SYNC_ENABLED   false
  set_kv "$f" HAENGSUNG_EXCHANGE_RATE_SYNC_CRON       "$CRON_DEFAULT"
  set_kv "$f" HAENGSUNG_CARD_ACCOUNT_SYNC_ENABLED    false
  set_kv "$f" HAENGSUNG_CARD_ACCOUNT_SYNC_CRON        "$CRON_DEFAULT"
}

backup() {
  f="$1"
  cp -p "$f" "${f}.bak.${TS}"
  printf '  backup: %s.bak.%s\n' "$f" "$TS"
}

# guard: process a file only if it exists; else record as skipped.
exists_or_skip() {
  f="$1"
  if [ -f "$f" ]; then return 0; fi
  printf '  SKIP (not found): %s\n' "$f"
  skipped_files="$skipped_files $f"
  return 1
}

# --- .env.local & .env.local.example ----------------------------------------
# Oracle connection info populated like the Postgres block (non-secret host +
# user); feature OFF by default and PASSWORD never overwritten. Flip
# HAENGSUNG_ORACLE_ENABLED=true by hand when you want the laptop to connect.
apply_local_style() {
  f="$1"
  exists_or_skip "$f" || return 0
  backup "$f"
  ensure_trailing_newline "$f"
  set_kv "$f" HAENGSUNG_ORACLE_ENABLED  false
  set_kv "$f" HAENGSUNG_ORACLE_URL       'jdbc:oracle:thin:@//172.168.5.23:1521/DEV'
  set_kv "$f" HAENGSUNG_ORACLE_USERNAME  wecms
  ensure_password_key "$f"          # never overwrites an existing password
  apply_schedulers "$f"
  updated_files="$updated_files $f"
  printf '  updated: %s (Oracle URL+user set, ENABLED=false, password preserved, schedulers OFF)\n' "$f"
}

# --- example templates (.env.example / .env.sit.example / .env.uat.example /
#     .env.prod.example) : Oracle OFF, placeholder URL/user -----------------
apply_example_placeholder_style() {
  f="$1"
  exists_or_skip "$f" || return 0
  backup "$f"
  ensure_trailing_newline "$f"
  set_kv "$f" HAENGSUNG_ORACLE_ENABLED  false
  set_kv "$f" HAENGSUNG_ORACLE_URL       'jdbc:oracle:thin:@//<oracle-host>:1521/<service>'
  set_kv "$f" HAENGSUNG_ORACLE_USERNAME  '<oracle-user>'
  ensure_password_key "$f"          # placeholder stays blank; preserved if present
  apply_schedulers "$f"
  updated_files="$updated_files $f"
  printf '  updated: %s (Oracle OFF, placeholder URL/user, schedulers OFF)\n' "$f"
}

# --- .env.sit : Oracle ON, real host/user, PASSWORD PRESERVED ---------------
apply_sit_style() {
  f="$1"
  exists_or_skip "$f" || return 0
  backup "$f"
  ensure_trailing_newline "$f"
  set_kv "$f" HAENGSUNG_ORACLE_ENABLED  true
  set_kv "$f" HAENGSUNG_ORACLE_URL       'jdbc:oracle:thin:@//172.168.5.23:1521/DEV'
  set_kv "$f" HAENGSUNG_ORACLE_USERNAME  wecms
  ensure_password_key "$f"          # NEVER overwritten
  apply_schedulers "$f"
  updated_files="$updated_files $f"
  printf '  updated: %s (Oracle ON, schedulers OFF)\n' "$f"
  status="$(password_status "$f")"
  printf '  .env.sit HAENGSUNG_ORACLE_PASSWORD: %s\n' "$status"
  if [ "$status" = "EMPTY" ]; then
    printf 'HAENGSUNG_ORACLE_PASSWORD is empty; please fill it manually.\n'
  fi
}

# --- run --------------------------------------------------------------------
printf 'Project root: %s\n' "$ROOT"
printf 'Timestamp:    %s\n\n' "$TS"

printf '[.env.local]\n';          apply_local_style               ".env.local"
printf '[.env.local.example]\n';  apply_local_style               ".env.local.example"
printf '[.env.example]\n';        apply_example_placeholder_style ".env.example"
printf '[.env.sit.example]\n';    apply_example_placeholder_style ".env.sit.example"
printf '[.env.uat.example]\n';    apply_example_placeholder_style ".env.uat.example"
printf '[.env.prod.example]\n';   apply_example_placeholder_style ".env.prod.example"
printf '[.env.sit]\n';            apply_sit_style                 ".env.sit"

# --- safe summary -----------------------------------------------------------
printf '\n=== Summary ===\n'
printf 'Updated files:%s\n' "${updated_files:- none}"
printf 'Skipped files:%s\n' "${skipped_files:- none}"
printf 'Schedulers: all *_ENABLED set to false (import / exchange-rate / card-account).\n'
printf 'Oracle ENABLED=true only in .env.sit; false in .env.local / *.example.\n'
printf 'Passwords: existing values preserved; none printed.\n'
