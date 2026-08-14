"""
Cầu nối API <-> oracle: chạy 1 case qua API thật, tính kỳ vọng bằng oracle,
đối chiếu tới từng đồng. Dùng chung cho cả deterministic fuzzer và agent SDK.
"""
from oracle import compute, INVALID


def judge_case(client, inputs: dict, override_source: bool) -> dict:
    """
    Trả về verdict:
      {
        "pass": bool,
        "inputs": {...}, "override_source": bool,
        "expected": {"error": ...} | {"indicators": {...}},
        "actual":   {"error": ...} | {"indicators": {...}},
        "mismatches": [ {cell, expected, actual}, ... ]  # rỗng nếu pass
      }
    """
    provided = client.provided_set(override_source)
    exp_derived, exp_err = compute(inputs, provided)
    actual = client.evaluate(inputs, override_source)

    mismatches = []

    if exp_err is not None:
        # Kỳ vọng lỗi.
        if "error" not in actual:
            mismatches.append({"cell": "<result>",
                               "expected": exp_err,
                               "actual": "OK (không lỗi)"})
        elif actual["error"] != exp_err:
            mismatches.append({"cell": "<result>",
                               "expected": exp_err,
                               "actual": actual["error"]})
        expected_view = {"error": exp_err}
    else:
        # Kỳ vọng thành công — đối chiếu từng ô dẫn xuất.
        if "error" in actual:
            mismatches.append({"cell": "<result>",
                               "expected": "OK",
                               "actual": actual["error"]})
        else:
            act = actual["indicators"]
            for cell, exp_val in exp_derived.items():
                got = act.get(cell)
                if got != exp_val:
                    mismatches.append({"cell": cell,
                                       "expected": exp_val,
                                       "actual": got})
        expected_view = {"indicators": exp_derived}

    return {
        "pass": len(mismatches) == 0,
        "inputs": inputs,
        "override_source": override_source,
        "expected": expected_view,
        "actual": actual,
        "mismatches": mismatches,
    }
