"""
Oracle độc lập cho ma trận công thức Mẫu 01/GTGT (TT80).

ĐÂY LÀ GROUND TRUTH — được viết lại từ luật TT80, KHÔNG copy code Java.
Con agent sinh đầu vào; oracle này (không phải model) phán kết quả kỳ vọng,
nên model không thể "bịa" giá trị mong đợi.

Đối chiếu công thức (khớp VatIndicatorCalculator.java, verify 2026-08-04):

    Ô nguồn: [25]=[24]; [31]=floor([30]/20); [33]=floor([32]/10)
             (nếu client gửi ct25/ct31/ct33 -> giữ giá trị client)
    [27] = [29]+[30]+[32]+[32a]
    [28] = [31]+[33]+[33a]
    [34] = [26]+[27]
    [35] = [28]
    [36] = [35] - [25]
    hieu_so = [36] - [22] + [37] - [38] - [39a]
    [40a] = max(hieu_so, 0)
    [41]  = max(-hieu_so, 0)
    [40]  = [40a] - [40b]     (yêu cầu [40b] <= [40a])
    [43]  = [41] - [42]       (yêu cầu [42] <= [41])
    Bất kỳ input âm -> lỗi WBE_VAT_DECL_INVALID_INDICATOR
"""

INVALID = "WBE_VAT_DECL_INVALID_INDICATOR"

# Các ô model được phép đặt giá trị (white cells). ct21 là CỜ 0/1, không phải tiền.
EDITABLE = [
    "ct21", "ct22", "ct23", "ct23a", "ct24", "ct24a", "ct25", "ct26",
    "ct29", "ct30", "ct31", "ct32", "ct32a", "ct33", "ct33a",
    "ct37", "ct38", "ct39a", "ct40b", "ct42", "ct42a", "ct42b",
]

# Ô nguồn — có thể override; nếu không gửi thì suy ra từ ô dẫn.
SOURCE_CELLS = ["ct25", "ct31", "ct33"]

# Ô dẫn xuất mà oracle tính và sẽ đối chiếu với response detail.
DERIVED = [
    "ct25", "ct31", "ct33", "ct27", "ct28", "ct34", "ct35",
    "ct36", "ct40a", "ct41", "ct40", "ct43",
]


def _floordiv(value: int, divisor: int) -> int:
    # Java BigDecimal.divideToIntegralValue = truncate về 0. Với value>=0 (đã assert
    # non-negative) thì trunc == floor; Python // với số dương cũng là floor.
    return value // divisor


def compute(inputs: dict, provided: set):
    """
    inputs:   dict {cell -> int} các ô editable model gửi (ô không gửi coi như 0).
    provided: set tên ô model thực sự gửi (quyết định derive-vs-override cho ct25/31/33).

    Trả về (result: dict|None, error: str|None).
      - Nếu vi phạm (âm hoặc cap) -> (None, INVALID).
      - Ngược lại -> (dict các ô DERIVED, None).
    """
    def nz(k):
        return int(inputs.get(k, 0) or 0)

    ct22, ct24, ct26 = nz("ct22"), nz("ct24"), nz("ct26")
    ct29, ct30, ct32 = nz("ct29"), nz("ct30"), nz("ct32")
    ct32a, ct33a = nz("ct32a"), nz("ct33a")
    ct37, ct38, ct39a = nz("ct37"), nz("ct38"), nz("ct39a")
    ct40b, ct42 = nz("ct40b"), nz("ct42")

    # Assert non-negative — khớp assertNonNegative(...) trong Java (cùng tập ô).
    nonneg = [
        ct22, ct24, ct26, ct29, ct30, ct32, ct32a, ct33a,
        ct37, ct38, ct39a, ct40b, ct42,
        nz("ct21"), nz("ct23"), nz("ct23a"), nz("ct24a"),
        nz("ct25"), nz("ct31"), nz("ct33"),
    ]
    if any(v < 0 for v in nonneg):
        return None, INVALID

    ct25 = nz("ct25") if "ct25" in provided else ct24
    ct31 = nz("ct31") if "ct31" in provided else _floordiv(ct30, 20)
    ct33 = nz("ct33") if "ct33" in provided else _floordiv(ct32, 10)

    ct27 = ct29 + ct30 + ct32 + ct32a
    ct28 = ct31 + ct33 + ct33a
    ct34 = ct26 + ct27
    ct35 = ct28
    ct36 = ct35 - ct25

    hieu_so = ct36 - ct22 + ct37 - ct38 - ct39a
    ct40a = max(hieu_so, 0)
    ct41 = max(-hieu_so, 0)

    if ct40b > ct40a or ct42 > ct41:
        return None, INVALID

    ct40 = ct40a - ct40b
    ct43 = ct41 - ct42

    return {
        "ct25": ct25, "ct31": ct31, "ct33": ct33,
        "ct27": ct27, "ct28": ct28, "ct34": ct34, "ct35": ct35,
        "ct36": ct36, "ct40a": ct40a, "ct41": ct41, "ct40": ct40, "ct43": ct43,
    }, None
