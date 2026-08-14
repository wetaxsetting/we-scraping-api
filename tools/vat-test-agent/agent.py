"""
Con AI test (Claude Agent SDK) — tầng "fuzzing thông minh" trên cùng pipeline.

Phân vai then chốt: MODEL chỉ CHỌN đầu vào (khám phá không gian input hướng tới
case gãy). ORACLE (Python, harness.judge_case) mới PHÁN đúng/sai — nên model
không thể bịa giá trị kỳ vọng. Mỗi lần model gọi tool `evaluate_case`, nó nhận
lại verdict đã đối chiếu; nó lặp cho tới khi tìm ra mismatch hoặc hết budget.

Chạy:  python agent.py
Cần:   ANTHROPIC_API_KEY + backend chạy (bootRun) + VPN. Xem README.

LƯU Ý: API của claude-agent-sdk có thể khác theo phiên bản cài đặt. Nếu import
lỗi, đối chiếu với README.md của gói `claude-agent-sdk` bạn cài (mục "Custom
tools / in-process MCP server").
"""
import asyncio
import json
import os

from claude_agent_sdk import (
    ClaudeAgentOptions,
    ClaudeSDKClient,
    create_sdk_mcp_server,
    tool,
)

from api_client import VatApiClient
from harness import judge_case
from oracle import EDITABLE

MODEL = os.environ.get("VAT_AGENT_MODEL", "claude-sonnet-5")
MAX_CASES = int(os.environ.get("VAT_AGENT_MAX_CASES", "40"))
FINDINGS_PATH = "findings.json"

# Trạng thái tiến trình chia sẻ với các tool.
_state = {"client": None, "evaluated": 0, "findings": []}


@tool(
    "evaluate_case",
    "Chạy 1 case chỉ tiêu VAT qua API thật và đối chiếu với oracle TT80. "
    "Trả verdict PASS/FAIL đã đối chiếu tới từng đồng. "
    "Bạn CHỈ chọn đầu vào; oracle phán đúng/sai.",
    {
        "inputs": dict,          # {cell: int} — các ô editable, xem danh sách trong prompt
        "override_source": bool,  # có gửi ct25/ct31/ct33 (override) hay để calculator tự suy
    },
)
async def evaluate_case(args):
    inputs = {k: int(v) for k, v in (args.get("inputs") or {}).items() if k in EDITABLE}
    override = bool(args.get("override_source", False))
    v = judge_case(_state["client"], inputs, override)
    _state["evaluated"] += 1
    remaining = MAX_CASES - _state["evaluated"]
    summary = {
        "pass": v["pass"],
        "mismatches": v["mismatches"],
        "expected": v["expected"],
        "actual": v["actual"],
        "cases_remaining": remaining,
    }
    return {"content": [{"type": "text", "text": json.dumps(summary, ensure_ascii=False)}]}


@tool(
    "record_finding",
    "Ghi lại 1 case GÃY đã xác nhận (mismatch thật) để hồi quy về sau.",
    {"title": str, "inputs": dict, "override_source": bool, "detail": str},
)
async def record_finding(args):
    _state["findings"].append({
        "title": args.get("title"),
        "inputs": args.get("inputs"),
        "override_source": args.get("override_source"),
        "detail": args.get("detail"),
    })
    with open(FINDINGS_PATH, "w", encoding="utf-8") as f:
        json.dump(_state["findings"], f, ensure_ascii=False, indent=2)
    return {"content": [{"type": "text", "text": f"đã ghi ({len(_state['findings'])} finding)"}]}


SYSTEM_PROMPT = f"""\
Bạn là agent kiểm thử logic tính chỉ tiêu Tờ khai thuế GTGT Mẫu 01/GTGT (TT80).
Nhiệm vụ: tìm case khiến kết quả tính của backend LỆCH so với luật TT80.

Công thức chuẩn (oracle đã cài — bạn KHÔNG cần tự tính, chỉ cần chọn input):
  [25]=[24]; [31]=floor([30]/20); [33]=floor([32]/10) (ô nguồn, override được)
  [27]=[29]+[30]+[32]+[32a]; [28]=[31]+[33]+[33a]
  [34]=[26]+[27]; [35]=[28]; [36]=[35]-[25]
  hieu_so=[36]-[22]+[37]-[38]-[39a]; [40a]=max(hieu_so,0); [41]=max(-hieu_so,0)
  [40]=[40a]-[40b] (cần [40b]<=[40a]); [43]=[41]-[42] (cần [42]<=[41])
  input âm -> lỗi WBE_VAT_DECL_INVALID_INDICATOR

Ô bạn được đặt (đơn vị VND, số nguyên; ct21 là cờ 0/1):
  {", ".join(EDITABLE)}

Chiến lược ưu tiên các biên:
  - làm tròn floor lẻ (ct30/ct32 không chia hết cho 20/10)
  - override ct25/ct31/ct33 (đặt override_source=true) -> phải giữ nguyên giá trị bạn gửi
  - vi phạm cap: [40b]>[40a], [42]>[41] -> phải trả lỗi
  - input âm -> phải trả lỗi
  - số rất lớn (nghìn tỷ) -> không tràn, khớp tới đồng
  - đổi dấu hieu_so quanh 0 (ranh giới phải-nộp / còn-khấu-trừ)

Quy trình: gọi evaluate_case với input bạn chọn. Nếu verdict FAIL (mismatches
khác rỗng) -> gọi record_finding. Tiếp tục tới khi cases_remaining=0.
Kết thúc: tóm tắt số case đã chạy, số finding, và các ô hay lệch nhất.
"""


async def main():
    client = VatApiClient()
    client.login()
    client.create_draft()
    _state["client"] = client
    print(f"draft sid = {client.sid}")

    server = create_sdk_mcp_server(
        name="vat", version="1.0.0",
        tools=[evaluate_case, record_finding],
    )
    options = ClaudeAgentOptions(
        model=MODEL,
        system_prompt=SYSTEM_PROMPT,
        mcp_servers={"vat": server},
        allowed_tools=["mcp__vat__evaluate_case", "mcp__vat__record_finding"],
        max_turns=MAX_CASES * 2 + 5,
    )

    try:
        async with ClaudeSDKClient(options=options) as agent:
            await agent.query(
                f"Bắt đầu tìm case gãy. Ngân sách {MAX_CASES} case. "
                "Ưu tiên các biên trong hướng dẫn."
            )
            async for message in agent.receive_response():
                # In text agent trả về (bỏ qua chi tiết message object theo SDK version).
                text = getattr(message, "text", None) or getattr(message, "content", None)
                if isinstance(text, str):
                    print(text, end="")
        print()
    finally:
        client.delete_draft()
        client.close()

    print(f"\n=== đã chạy {_state['evaluated']} case, "
          f"{len(_state['findings'])} finding (xem {FINDINGS_PATH}) ===")


if __name__ == "__main__":
    asyncio.run(main())
