"""
Fuzzer KHÔNG dùng AI — chạy ngay, không tốn token, không cần ANTHROPIC_API_KEY.
Dùng để (a) khói-test toàn pipeline (login/create/PUT/detail/oracle) trước khi
bật agent, (b) hồi quy nhanh trong CI.

Chạy:  python run_deterministic.py [số_case_ngẫu_nhiên]
Cần:   backend đang chạy (bootRun) + VPN tới DB. Xem README.
"""
import random
import sys

from api_client import VatApiClient
from harness import judge_case

# Case biên tĩnh — luôn chạy trước, độc lập seed.
STATIC_CASES = [
    # (mô tả, inputs, override_source)
    ("zero", {}, False),
    ("floor 5% lẻ:  ct30=99 -> ct31=4", {"ct30": 99}, False),
    ("floor 10% lẻ: ct32=99 -> ct33=9", {"ct32": 99}, False),
    ("floor chia hết: ct30=100 -> ct31=5", {"ct30": 100}, False),
    ("override ct31 giữ nguyên", {"ct30": 100, "ct31": 999}, True),
    ("override ct33 giữ nguyên", {"ct32": 100, "ct33": 777}, True),
    ("override ct25 giữ nguyên", {"ct24": 500, "ct25": 123}, True),
    ("phải nộp: [40a]>0", {"ct32": 1000, "ct24": 0}, False),
    ("còn khấu trừ: [41]>0", {"ct22": 1000, "ct32": 0}, False),
    ("cap vi phạm [40b]>[40a]", {"ct32": 100, "ct40b": 999_999}, False),
    ("cap vi phạm [42]>[41]", {"ct22": 100, "ct42": 999_999}, False),
    ("input âm -> lỗi", {"ct24": -1}, False),
    ("số lớn (nghìn tỷ)", {"ct32": 1_234_567_890_123, "ct24": 5_000_000_000}, False),
]

# Biên số cho fuzz ngẫu nhiên.
_CELLS = ["ct22", "ct23", "ct23a", "ct24", "ct24a", "ct26",
          "ct29", "ct30", "ct32", "ct32a", "ct33a", "ct37", "ct38", "ct39a"]


def random_case(rng):
    inputs = {}
    for c in _CELLS:
        # thiên về giá trị nhỏ + thỉnh thoảng số lớn / lẻ để chạm floor.
        r = rng.random()
        if r < 0.2:
            inputs[c] = 0
        elif r < 0.7:
            inputs[c] = rng.randint(1, 999)
        else:
            inputs[c] = rng.randint(1_000, 10_000_000_000)
    # đôi khi thử cap / âm
    if rng.random() < 0.15:
        inputs["ct40b"] = rng.randint(1, 10_000_000_000)
    if rng.random() < 0.15:
        inputs["ct42"] = rng.randint(1, 10_000_000_000)
    if rng.random() < 0.05:
        inputs[rng.choice(_CELLS)] = -rng.randint(1, 1000)
    override = rng.random() < 0.3
    if override:
        inputs["ct25"] = rng.randint(0, 10_000)
        inputs["ct31"] = rng.randint(0, 10_000)
        inputs["ct33"] = rng.randint(0, 10_000)
    return inputs, override


def main():
    n_random = int(sys.argv[1]) if len(sys.argv) > 1 else 50
    rng = random.Random(1234)  # seed cố định -> tái lập được

    client = VatApiClient()
    client.login()
    client.create_draft()
    print(f"draft sid = {client.sid}\n")

    failures = []
    total = 0

    def run(label, inputs, override):
        nonlocal total
        total += 1
        v = judge_case(client, inputs, override)
        mark = "PASS" if v["pass"] else "FAIL"
        print(f"[{mark}] {label}")
        if not v["pass"]:
            for m in v["mismatches"]:
                print(f"        {m['cell']}: expected={m['expected']} actual={m['actual']}")
            failures.append((label, v))

    try:
        for label, inputs, override in STATIC_CASES:
            run(label, inputs, override)
        for i in range(n_random):
            inputs, override = random_case(rng)
            run(f"random #{i}", inputs, override)
    finally:
        client.delete_draft()
        client.close()

    print(f"\n=== {total - len(failures)}/{total} PASS, {len(failures)} FAIL ===")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
