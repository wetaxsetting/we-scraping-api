# VAT Indicator Test Agent

Con AI kiểm thử **logic tính chỉ tiêu** Tờ khai thuế GTGT (Mẫu 01/GTGT, TT80) của
backend `wecms-api`. Dùng Claude Agent SDK để *sinh case biên thông minh*; một
**oracle Python độc lập** (không phải model) mới phán đúng/sai — nên model không
thể bịa kết quả kỳ vọng.

Vì `VatIndicatorCalculator` không có endpoint riêng, agent chạm nó qua API thật:
`PUT /api/v1/addon/vat-declarations/{sid}` rồi đọc lại `data.indicators` trong
`GET /{sid}`.

## Kiến trúc

```
agent.py (Claude Agent SDK)   ── model CHỌN input ──►  tool evaluate_case
                                                            │
run_deterministic.py (no AI)  ──────────────────────►  harness.judge_case
                                                            │  ┌─ api_client.py → API thật (login/PUT/detail)
                                                            └──┤
                                                               └─ oracle.py    → công thức TT80 (GROUND TRUTH)
```

- `oracle.py` — ma trận công thức TT80 viết lại độc lập (ground truth).
- `api_client.py` — login, tạo 1 draft dùng chung, PUT chỉ tiêu, đọc detail.
- `harness.py` — chạy 1 case qua API + oracle, đối chiếu tới từng đồng.
- `run_deterministic.py` — fuzzer **không cần AI/API key**: 13 case biên tĩnh + N case ngẫu nhiên (seed cố định).
- `agent.py` — tầng fuzzing bằng Claude Agent SDK; model chỉ chọn input.

## Chuẩn bị

1. **Bật VPN** tới `172.168.5.172` (DB dev) và **chạy backend**:
   ```bash
   cd wecms-api && ./gradlew bootRun --args='--spring.profiles.active=local'
   ```
   (Cần `.env` + JWT secret riêng — xem yêu cầu boot của wecms-api.)

2. **Cài Python deps** (Python 3.10+):
   ```bash
   cd wecms-api/tools/vat-test-agent
   python -m venv .venv && . .venv/Scripts/activate   # Windows: .venv\Scripts\activate
   pip install -r requirements.txt
   ```

3. **Cấu hình env**: `cp .env.example .env` rồi điền. `run_deterministic.py` không
   cần `ANTHROPIC_API_KEY`; `agent.py` thì cần.

## Chạy

**Khói-test pipeline (không tốn token, chạy trước):**
```bash
python run_deterministic.py 50      # 13 case biên + 50 case ngẫu nhiên
```
Exit code 0 = tất cả PASS; 1 = có FAIL (in ô lệch + expected/actual).

**Con AI (smart fuzzing):**
```bash
python agent.py                     # ngân sách VAT_AGENT_MAX_CASES case
```
Case gãy được ghi vào `findings.json`.

## Vòng lặp làm việc khuyến nghị

1. Chạy `run_deterministic.py` để chắc pipeline + oracle khớp backend hiện tại.
2. Chạy `agent.py` để nó *khám phá* case biên bạn chưa nghĩ tới.
3. Mỗi finding trong `findings.json` → **đóng băng** thành 1 dòng tham số trong
   `VatIndicatorCalculatorTest` (JUnit) để hồi quy deterministic, không tốn token.

## Ghi chú

- Draft dùng chung được **soft-delete** cuối mỗi lần chạy; partial unique index
  `tax_declarations_uk01 WHERE del_yn='N'` (fix 2026-08-04) cho phép tạo lại cùng
  kỳ ở lần chạy sau — đây chính là lý do vòng lặp create→delete→create hoạt động.
- Chống carryover: mỗi case gửi tường minh toàn bộ ô non-source (kể cả `0`); ô nguồn
  `ct25/ct31/ct33` chỉ gửi khi `override_source=true`, còn lại calculator tự suy từ
  `ct24/ct30/ct32`.
- API của `claude-agent-sdk` có thể khác theo phiên bản; nếu import lỗi, đối chiếu
  README của gói bạn cài (mục custom tools / in-process MCP server) và chỉnh `agent.py`.
- Oracle phải bám sát `VatIndicatorCalculator.java`. Khi công thức backend đổi, cập
  nhật `oracle.py` tương ứng (nếu không, mọi case sẽ FAIL vì oracle lệch, không phải backend).
```
