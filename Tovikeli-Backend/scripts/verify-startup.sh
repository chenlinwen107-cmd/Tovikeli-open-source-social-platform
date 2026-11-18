#!/data/data/com.termux/files/usr/bin/bash
# Repeatable production startup verification for ContentPlatformBackend.
#
# Usage:
#   BASE=http://127.0.0.1:8080 \
#   DB_URL=jdbc:postgresql://127.0.0.1:5432/contentplatform_prod \
#   ./scripts/verify-startup.sh
#
# BASE and DB_URL may also be passed as the first and second arguments.
# No credentials are read from this file; DB_URL is expected to reference a
# local trust/peer authentication setup or to be used together with PGPASSWORD
# supplied by the caller's environment.
set -uo pipefail

BASE="${1:-${BASE:-http://127.0.0.1:8080}}"
DB_URL="${2:-${DB_URL:-}}"

PASS=0
FAIL=0
ok()  { printf '  [ OK ] %-46s %s\n' "$1" "$2"; PASS=$((PASS + 1)); }
bad() { printf '  [FAIL] %-46s %s\n' "$1" "$2"; FAIL=$((FAIL + 1)); }
chk() { if [ "$2" = "$3" ]; then ok "$1" "$3"; else bad "$1" "expected [$2] got [$3]"; fi; }
code() { curl -s -o /dev/null -w '%{http_code}' --max-time 8 "$@"; }

echo "=== 1. HTTP surface ($BASE) ==="
chk "GET /" "200" "$(code "$BASE/")"

if [ -n "$DB_URL" ]; then
  chk "GET /api/v1/contents" "200" "$(code "$BASE/api/v1/contents?limit=1")"
  # pg tools want a libpq URI/keyword string, not a JDBC URL.
  DSN="${DB_URL#jdbc:}"
  echo "=== 2. Flyway schema history ==="
  TOTAL="$(psql "$DSN" -tAc 'SELECT count(*) FROM flyway_schema_history;' 2>/dev/null)"
  BAD="$(psql "$DSN" -tAc 'SELECT count(*) FROM flyway_schema_history WHERE success = false;' 2>/dev/null)"
  LATEST="$(psql "$DSN" -tAc 'SELECT max(version) FROM flyway_schema_history;' 2>/dev/null)"
  chk "migration rows present" "yes" "$([ "${TOTAL:-0}" -ge 1 ] && echo yes || echo no)"
  chk "no failed migrations" "0" "${BAD:-?}"
  echo "  [INFO] applied migrations=$TOTAL latest version=$LATEST"

  echo "=== 3. Required tables ==="
  for t in content author tag platform_user content_interaction content_comment spring_session spring_session_attributes; do
    FOUND="$(psql "$DSN" -tAc "SELECT count(*) FROM information_schema.tables WHERE table_name='$t';" 2>/dev/null)"
    chk "table $t" "1" "${FOUND:-0}"
  done
else
  echo "=== 2-3. Database checks skipped (DB_URL not set) ==="
fi

printf '\nSTARTUP VERIFICATION: pass=%d fail=%d\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ]
