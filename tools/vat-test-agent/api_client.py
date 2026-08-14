"""
Client HTTP mỏng cho VAT Declaration API — login, tạo 1 draft dùng chung,
PUT chỉ tiêu, đọc detail. Chỉ tiêu đi/về đều là chuỗi integer-VND (wire snake_case
qua Jackson SNAKE_CASE toàn cục).

Vì calculator KHÔNG có endpoint riêng, ta chạm nó qua PUT /{sid} rồi đọc lại
data.indicators trong GET /{sid}.

Chiến lược tránh carryover: tái dùng 1 draft, mỗi case gửi TƯỜNG MINH toàn bộ ô
non-source (kể cả 0). Ô nguồn ct25/ct31/ct33 chỉ gửi ở chế độ override; ở chế độ
derive chúng luôn được calculator tính lại từ ct24/ct30/ct32 (đã gửi) nên giá trị
cũ không ảnh hưởng.
"""
import os
import httpx


def _load_dotenv():
    """Nạp file .env cạnh script vào os.environ (không đè biến đã set sẵn).
    Tối giản — không cần python-dotenv."""
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env")
    if not os.path.exists(path):
        return
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, val = line.partition("=")
            key, val = key.strip(), val.strip().strip('"').strip("'")
            os.environ.setdefault(key, val)


_load_dotenv()

BASE = os.environ.get("WECMS_API_BASE", "http://localhost:8080")
USER_ID = os.environ.get("WECMS_USER_ID", "testuser")
USER_PW = os.environ.get("WECMS_USER_PW", "Password!1")
# VPN tới DB dev có thể chậm/chập chờn -> timeout rộng, chỉnh qua WECMS_HTTP_TIMEOUT.
TIMEOUT = float(os.environ.get("WECMS_HTTP_TIMEOUT", "120"))
DECL_YEAR = int(os.environ.get("WECMS_DECL_YEAR", "2025"))

# Ô non-source luôn gửi mỗi case (tường minh, kể cả "0") để không carryover.
_ALWAYS_SEND = [
    "ct21", "ct22", "ct23", "ct23a", "ct24", "ct24a", "ct26",
    "ct29", "ct30", "ct32", "ct32a", "ct33a",
    "ct37", "ct38", "ct39a", "ct40b", "ct42",
]


class VatApiError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(f"{code}: {message}")
        self.code = code
        self.message = message


class VatApiClient:
    def __init__(self):
        self._http = httpx.Client(base_url=BASE, timeout=TIMEOUT)
        self._token = None
        self.sid = None

    # --- lifecycle --------------------------------------------------------
    def login(self):
        r = self._http.post("/api/v1/auth/login",
                            json={"user_id": USER_ID, "user_pw": USER_PW})
        r.raise_for_status()
        body = r.json()
        if body["status"]["error"]:
            raise VatApiError(body["status"]["code"], body["status"]["message"])
        self._token = body["data"]["access_token"]
        self._http.headers["Authorization"] = f"Bearer {self._token}"

    def create_draft(self, decl_year=DECL_YEAR):
        """Dò 1 kỳ TRỐNG để tạo draft: thử M 1..12 rồi Q 1..4. Bỏ qua kỳ đã có
        declaration active (WBE_VAT_DECL_DUPLICATE_PERIOD)."""
        attempts = [("M", v) for v in range(1, 13)] + [("Q", v) for v in range(1, 5)]
        for period_type, period_value in attempts:
            try:
                body = self._post("/api/v1/addon/vat-declarations", {
                    "period_type": period_type,
                    "period_value": period_value,
                    "decl_year": decl_year,
                })
                self.sid = body["sid"]
                return self.sid
            except VatApiError as e:
                if e.code == "WBE_VAT_DECL_DUPLICATE_PERIOD":
                    continue
                raise
        raise VatApiError("WBE_VAT_DECL_DUPLICATE_PERIOD",
                          f"Tất cả kỳ của năm {decl_year} đều đã có declaration active — "
                          f"đổi WECMS_DECL_YEAR sang năm khác hoặc xoá bớt phiếu draft.")

    def delete_draft(self):
        if self.sid is not None:
            self._http.delete(f"/api/v1/addon/vat-declarations/{self.sid}")
            self.sid = None

    def close(self):
        self._http.close()

    # --- one round-trip ---------------------------------------------------
    def evaluate(self, inputs: dict, override_source: bool):
        """
        Gửi 1 case vào API. Trả về:
          {"error": <code>}                    nếu API trả BusinessException
          {"indicators": {cell: int, ...}}     nếu thành công (các ô DERIVED)
        `inputs`: dict {cell -> int}. `override_source`: có gửi ct25/ct31/ct33 không.
        """
        payload = {}
        for cell in _ALWAYS_SEND:
            payload[cell] = str(int(inputs.get(cell, 0) or 0))
        if override_source:
            for cell in ("ct25", "ct31", "ct33"):
                payload[cell] = str(int(inputs.get(cell, 0) or 0))
        # PUT: nếu vi phạm business rule -> BusinessException (HTTP 200, status.error=true).
        put = self._raw_put(f"/api/v1/addon/vat-declarations/{self.sid}", payload)
        if put["status"]["error"]:
            return {"error": put["status"]["code"]}
        ind = put["data"]["indicators"]
        return {"indicators": {k: _to_int(v) for k, v in ind.items()}}

    def provided_set(self, override_source: bool) -> set:
        """Tập ô 'client gửi' — để oracle chọn nhánh derive/override cho ct25/31/33."""
        s = set(_ALWAYS_SEND)
        if override_source:
            s |= {"ct25", "ct31", "ct33"}
        return s

    # --- helpers ----------------------------------------------------------
    def _post(self, path, body):
        return self._unwrap(self._http.post(path, json=body))

    def _raw_put(self, path, body):
        r = self._http.put(path, json=body)
        r.raise_for_status()
        return r.json()

    def _unwrap(self, r):
        r.raise_for_status()
        body = r.json()
        if body["status"]["error"]:
            raise VatApiError(body["status"]["code"], body["status"]["message"])
        return body["data"]


def _to_int(v):
    if v is None or v == "":
        return 0
    return int(str(v).strip())
