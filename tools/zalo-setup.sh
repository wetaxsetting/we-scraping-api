#!/usr/bin/env bash
# =============================================================================
# zalo-setup.sh — chạy trọn Phần 2 của ZALO-INTEGRATION-SETUP.md §10
#
# Công cụ dev, KHÔNG phải code sản phẩm. Nó chỉ gọi đúng các endpoint đã có
# dưới /api/v1/settings/zalo-oa, không chứa logic nghiệp vụ nào.
#
# Vì sao cần: luồng kết nối OA có thứ tự bắt buộc (đăng ký -> OAuth -> đổi code
# -> đăng ký template -> kích hoạt -> gửi). Làm tay bằng curl rất dễ nhầm thứ
# tự, mà `code` OAuth chỉ dùng được MỘT LẦN — sai thứ tự là phải quay lại bấm
# đồng ý trên Zalo từ đầu.
#
# Dùng:  ./zalo-setup.sh <lệnh> [tham số]
#        ./zalo-setup.sh help
# =============================================================================
set -uo pipefail

API="${WECMS_API_BASE:-http://localhost:8080}"
ENV_FILE="${WECMS_ENV_FILE:-$(dirname "$0")/../.env}"
# Token phiên KHÔNG bao giờ ghi vào repo — để trong thư mục tạm của OS.
SESSION="${TMPDIR:-/tmp}/wecms-zalo-session"

C_OK=$'\033[32m'; C_ERR=$'\033[31m'; C_WARN=$'\033[33m'; C_DIM=$'\033[2m'; C_OFF=$'\033[0m'
ok()   { printf '%s  OK %s %s\n'   "$C_OK"   "$C_OFF" "$1"; }
bad()  { printf '%s FAIL%s %s\n'   "$C_ERR"  "$C_OFF" "$1"; }
warn() { printf '%s WARN%s %s\n'   "$C_WARN" "$C_OFF" "$1"; }
dim()  { printf '%s%s%s\n'         "$C_DIM"  "$1" "$C_OFF"; }

need() { command -v "$1" >/dev/null 2>&1 || { bad "thiếu lệnh '$1'"; exit 1; }; }
need curl
need python

envval() { [ -f "$ENV_FILE" ] && sed -n "s/^$1=//p" "$ENV_FILE" | head -1 | tr -d '\r'; }

token() {
  if [ -n "${ZALO_TOKEN:-}" ]; then printf '%s' "$ZALO_TOKEN"; return; fi
  [ -f "$SESSION" ] && tr -d '\r\n' < "$SESSION"
}

# Gọi API, in kết quả đã format, trả exit code theo status.error của envelope.
call() {
  local method="$1" path="$2" body="${3:-}"
  local tk; tk="$(token)"
  if [ -z "$tk" ]; then bad "chưa có JWT — chạy: $0 login <user> <password>"; return 1; fi

  local resp
  if [ -n "$body" ]; then
    resp=$(curl -s --max-time 60 -X "$method" "$API$path" \
             -H "Authorization: Bearer $tk" -H 'Content-Type: application/json' -d "$body")
  else
    resp=$(curl -s --max-time 60 -X "$method" "$API$path" -H "Authorization: Bearer $tk")
  fi

  dim "$method $path"
  printf '%s' "$resp" | python -c '
import json,sys
raw=sys.stdin.read()
if not raw.strip():
    print("  (phản hồi rỗng — backend còn chạy không?)"); sys.exit(2)
try: d=json.loads(raw)
except Exception:
    print("  (không phải JSON):"); print(raw[:800]); sys.exit(2)
st=d.get("status") or {}
if st.get("error"):
    print("  LOI  %s: %s" % (st.get("code"), st.get("message")))
    if d.get("trace_id"): print("  trace_id: %s" % d["trace_id"])
    sys.exit(1)
print(json.dumps(d.get("data"), ensure_ascii=False, indent=2))
'
}

cmd_check() {
  echo "=========================================================="
  echo " PREFLIGHT — kiểm tra trước khi chạy Phần 2"
  echo "=========================================================="
  echo
  echo "[1] Backend"
  local code; code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$API/actuator/health" 2>/dev/null)
  if [ "$code" = "200" ]; then ok "$API đang chạy"
  else bad "$API không phản hồi (HTTP $code) — khởi động backend trước"; fi

  echo
  echo "[2] Biến môi trường ($ENV_FILE)"
  if [ ! -f "$ENV_FILE" ]; then
    bad "không thấy $ENV_FILE"
  else
    local en id sec cron
    en=$(envval WECMS_ZALO_ENABLED); id=$(envval WECMS_ZALO_APP_ID)
    sec=$(envval WECMS_ZALO_APP_SECRET); cron=$(envval WECMS_ZALO_TOKEN_REFRESH_CRON)
    [ "$en" = "true" ] && ok "WECMS_ZALO_ENABLED=true" \
      || warn "WECMS_ZALO_ENABLED=${en:-<trống>} — còn false thì MỌI lời gọi Zalo trả WBE_ZALO_DISABLED"
    [ -n "$id"  ] && ok "WECMS_ZALO_APP_ID đã điền" || bad "WECMS_ZALO_APP_ID trống (Bước 3 Phần 1)"
    [ -n "$sec" ] && ok "WECMS_ZALO_APP_SECRET đã điền" || bad "WECMS_ZALO_APP_SECRET trống (Bước 3 Phần 1)"
    [ -n "$cron" ] && ok "WECMS_ZALO_TOKEN_REFRESH_CRON=$cron" \
      || bad "WECMS_ZALO_TOKEN_REFRESH_CRON TRỐNG — để trống làm app CHẾT LÚC KHỞI ĐỘNG. Đặt '-'"
  fi

  echo
  echo "[3] Phiên đăng nhập"
  if [ -n "$(token)" ]; then ok "đã có JWT ($SESSION)"; else warn "chưa login — chạy: $0 login <user> <password>"; fi

  echo
  echo "[4] Kết nối OA hiện có"
  if [ -n "$(token)" ] && [ "$code" = "200" ]; then
    call GET "/api/v1/settings/zalo-oa?page=0&size=20" >/dev/null 2>&1 \
      && call GET "/api/v1/settings/zalo-oa?page=0&size=20" \
      || warn "không đọc được danh sách (tài khoản có phải ADMIN không?)"
  else
    dim "  bỏ qua — cần backend chạy + đã login"
  fi

  echo
  echo "----------------------------------------------------------"
  echo " 4 giá trị phải lấy từ console Zalo (Phần 1) trước khi tiếp:"
  echo "   [ ] App ID        -> WECMS_ZALO_APP_ID"
  echo "   [ ] App Secret    -> WECMS_ZALO_APP_SECRET"
  echo "   [ ] OA ID         -> tham số lệnh register"
  echo "   [ ] template_id   -> tham số lệnh template (phải ĐÃ DUYỆT)"
  echo "----------------------------------------------------------"
}

cmd_login() {
  local u="${1:?cần user_id}" p="${2:?cần password}"
  local resp; resp=$(curl -s --max-time 30 -X POST "$API/api/v1/auth/login" \
        -H 'Content-Type: application/json' \
        -d "$(python -c 'import json,sys; print(json.dumps({"user_id":sys.argv[1],"user_pw":sys.argv[2]}))' "$u" "$p")")
  local tk; tk=$(printf '%s' "$resp" | python -c '
import json,sys
try: d=json.loads(sys.stdin.read())
except Exception: sys.exit(1)
st=d.get("status") or {}
if st.get("error"):
    sys.stderr.write("%s: %s\n" % (st.get("code"), st.get("message"))); sys.exit(1)
data=d.get("data") or {}
tk=data.get("access_token")
if not tk:
    # phản hồi chooser (user thuộc nhiều công ty) không cấp token
    sys.stderr.write("login khong tra access_token — co the la man hinh chon cong ty\n"); sys.exit(1)
sys.stderr.write("auth_gr_cd=%s biz_key=%s\n" % (data.get("auth_gr_cd"), data.get("biz_key")))
print(tk)')
  if [ -z "$tk" ]; then bad "đăng nhập thất bại"; return 1; fi
  printf '%s' "$tk" > "$SESSION"; chmod 600 "$SESSION" 2>/dev/null
  ok "đã lưu JWT vào $SESSION"
  dim "  Lưu ý: các endpoint Zalo yêu cầu ADMIN (settings-core-zalo-oa chưa có trong Menu Entitlement)."
}

cmd_url() {
  local appid; appid=$(envval WECMS_ZALO_APP_ID)
  local redirect="${1:-}"
  [ -z "$appid" ]    && { bad "WECMS_ZALO_APP_ID trống trong $ENV_FILE"; return 1; }
  [ -z "$redirect" ] && { bad "dùng: $0 url <CALLBACK_URL đã khai trong app Zalo>"; return 1; }
  local enc; enc=$(python -c 'import sys,urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$redirect")
  echo
  echo "Mở URL này trên trình duyệt, chọn OA rồi bấm đồng ý:"
  echo
  echo "https://oauth.zaloapp.com/v4/oa/permission?app_id=$appid&redirect_uri=$enc"
  echo
  dim "redirect_uri phải KHỚP TỪNG KÝ TỰ với Callback URL khai trong app Zalo."
  dim "Frontend chưa có route /authorized nên trang đích sẽ 404 — không sao,"
  dim "copy 'code' trên thanh địa chỉ rồi chạy: $0 authorize <OA_ID> <code>"
}

cmd_register() {
  local oa="${1:?cần OA ID}" nm="${2:-WeCMS Test OA}"
  call POST "/api/v1/settings/zalo-oa" \
    "$(python -c 'import json,sys; print(json.dumps({"oa_id":sys.argv[1],"oa_nm":sys.argv[2]}))' "$oa" "$nm")"
}

cmd_authorize() {
  local oa="${1:?cần OA ID}" code="${2:?cần code OAuth}"
  call POST "/api/v1/settings/zalo-oa/$oa/authorize" \
    "$(python -c 'import json,sys; print(json.dumps({"code":sys.argv[1]}))' "$code")"
}

cmd_template() {
  local oa="${1:?cần OA ID}" tid="${2:?cần template_id}" ty="${3:-NOTICE}" lang="${4:-VI}" price="${5:-}"
  call PUT "/api/v1/settings/zalo-oa/$oa/templates" \
    "$(python -c '
import json,sys
b={"tmpl_type":sys.argv[1],"lang_cd":sys.argv[2],"tmpl_id":sys.argv[3],"active":True}
if len(sys.argv)>4 and sys.argv[4]: b["unit_prc"]=float(sys.argv[4])
print(json.dumps(b))' "$ty" "$lang" "$tid" "$price")"
}

cmd_activate() {
  local oa="${1:?cần OA ID}"
  call PUT "/api/v1/settings/zalo-oa/$oa" '{"active":true}'
}

cmd_send() {
  local phone="${1:?cần số E.164, vd 84912345678}" params="${2:-{\}}" ty="${3:-NOTICE}" lang="${4:-VI}"
  case "$phone" in 0*) warn "số bắt đầu bằng 0 — Zalo cần dạng E.164: 84xxxxxxxxx";; esac
  call POST "/api/v1/settings/zalo-oa/test-send" \
    "$(python -c '
import json,sys
print(json.dumps({"phone":sys.argv[1],"tmpl_type":sys.argv[2],"lang_cd":sys.argv[3],
                  "template_data":json.loads(sys.argv[4])}))' "$phone" "$ty" "$lang" "$params")"
}

cmd_status()  { call GET "/api/v1/settings/zalo-oa?page=0&size=20"; }
cmd_history() { call GET "/api/v1/settings/zalo-oa/send-history?page=0&size=${1:-20}"; }

cmd_help() {
cat <<'HELP'
zalo-setup.sh — chạy Phần 2 của ZALO-INTEGRATION-SETUP.md §10

  check                                   preflight: backend, .env, phiên, OA hiện có
  login <user> <password>                 lấy JWT (phải là tài khoản ADMIN)
  url <callback_url>                      in URL cấp quyền OAuth để mở trên trình duyệt
  register <oa_id> [tên]                  Bước 12a — tạo hàng kết nối TRƯỚC khi OAuth
  authorize <oa_id> <code>                Bước 13  — đổi code lấy token (code dùng 1 lần)
  template <oa_id> <template_id> [type] [lang] [đơn_giá]
                                          Bước 14  — đăng ký template đã duyệt
  activate <oa_id>                        Bước 15  — đặt làm OA gửi tin của công ty
  send <phone> '<json params>' [type] [lang]
                                          Bước 16  — gửi thử (TỐN TIỀN THẬT)
  status                                  danh sách OA + template
  history [n]                             log gửi tin, mới nhất trước

Thứ tự bắt buộc: login -> register -> url -> authorize -> template -> activate -> send

Ví dụ trọn luồng:
  ./zalo-setup.sh check
  ./zalo-setup.sh login admin@example.com 'MatKhau!1'
  ./zalo-setup.sh register 1234567890123456789 "WeCMS Test OA"
  ./zalo-setup.sh url https://dev.example.com/authorized
  ./zalo-setup.sh authorize 1234567890123456789 <code-copy-tu-thanh-dia-chi>
  ./zalo-setup.sh template 1234567890123456789 566246 NOTICE VI 400
  ./zalo-setup.sh activate 1234567890123456789
  ./zalo-setup.sh send 84912345678 '{"customer_name":"Nguyen Van A","amount":"500000"}'
  ./zalo-setup.sh history

Biến môi trường:
  WECMS_API_BASE   mặc định http://localhost:8080
  WECMS_ENV_FILE   mặc định ../.env so với script
  ZALO_TOKEN       dùng JWT này thay vì file phiên
HELP
}

case "${1:-help}" in
  check)     shift; cmd_check "$@" ;;
  login)     shift; cmd_login "$@" ;;
  url)       shift; cmd_url "$@" ;;
  register)  shift; cmd_register "$@" ;;
  authorize) shift; cmd_authorize "$@" ;;
  template)  shift; cmd_template "$@" ;;
  activate)  shift; cmd_activate "$@" ;;
  send)      shift; cmd_send "$@" ;;
  status)    shift; cmd_status "$@" ;;
  history)   shift; cmd_history "$@" ;;
  help|-h|--help) cmd_help ;;
  *) bad "lệnh không hợp lệ: $1"; echo; cmd_help; exit 1 ;;
esac
