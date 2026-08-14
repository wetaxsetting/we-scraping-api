# Bàn giao deploy SIT — VAT Declaration, 2026-08-13

**Nhánh:** `sit` @ `55f2352` · **Artifact:** `build/libs/wecms-api-0.0.1-SNAPSHOT.jar`
**Phạm vi:** chỉ module VAT Declaration. Không có việc của người khác đi kèm.

---

## Bản đang chạy là bản nào

Không có endpoint nào phơi commit đang chạy (`/actuator/health` chỉ trả
`{"status":"UP"}`; `build.gradle` không có plugin git-properties). Suy ra từ
hành vi đo được:

| Quan sát | Kết luận |
|---|---|
| `[28]` **có** cộng `[33a]` | bản chạy **cũ hơn** `01d41e4` (10/08) |
| `KCT→[32a]`, `KKKNT→[26]` đúng | bản chạy **mới hơn** commit hoán đổi 07/08 |

Nên bản đang chạy nằm trong khoảng **07/08–09/08**. Hai commit `dispatch` của
Sokny Ran (03/08, 05/08) **đã ra rồi** — deploy này không mang chúng theo.

---

## Sáu thay đổi hành vi

| | Thay đổi | Mã lỗi mới |
|---|---|---|
| 1 | `[28]` không còn cộng `[33a]` | — |
| 2 | Không khoá được tờ khai đánh dấu `[21]` mà vẫn khai doanh thu | `WBE_VAT_DECL_NO_ACTIVITY_CONFLICT` |
| 3 | Không khoá được kỳ **chưa kết thúc** (tạo sớm vẫn cho) | `WBE_VAT_DECL_PERIOD_NOT_ENDED` |
| 4 | Không lẫn tháng/quý trong một năm dương lịch; `[43]`→`[22]` bắc cầu qua ranh giới năm | `WBE_VAT_DECL_PERIOD_TYPE_MISMATCH` |
| 5 | Đồng bộ **từ chối** thuế suất không xếp được; `[25]`/`[31]` chỉ tự điền khi ô nguồn cùng request; `[33]` không còn được suy | `WBE_VAT_DECL_UNKNOWN_TAX_RATE` |
| 6 | Đồng bộ làm tròn về đồng chẵn (HALF_UP) | — |
| 7 | **Chỉ tiêu âm được phép** trên 13 ô doanh thu / mua vào | — |

**Bốn mã lỗi mới** đã có trong `messages.properties`, `_en`, `_vi`.

### (7) chỉ tiêu âm — PO chốt 13/08/2026

Hoá đơn điều chỉnh giảm có thể lớn hơn doanh thu của chính kỳ đó khi nó huỷ một
phần hoá đơn kỳ trước. Kỳ đó thật sự ghi nhận doanh thu thuần âm, và việc từ chối
làm **không khai được** một tờ khai hợp lệ (QA S-07).

**13 ô nhận số âm:** `[23]` `[24]` `[23a]` `[24a]` `[25]` `[26]` `[29]` `[30]`
`[31]` `[32]` `[32a]` `[33]` `[33a]`.

**7 ô vẫn chặn** — ở đây dấu trừ đổi *ý nghĩa* chứ không phải đổi độ lớn:

| Ô | Vì sao vẫn chặn |
|---|---|
| `[21]` | cờ 0/1 |
| `[22]` | là `[43]` của kỳ trước, vốn `max(0,…) − max(0,…)` nên không âm tại nguồn |
| `[37]` `[38]` `[39a]` | là **độ lớn**; công thức mới là chỗ cấp dấu (`+[37] − [38]`). Cho âm thì một khoản giảm viết được thành khoản tăng — hai cách viết cho cùng một điều chỉnh, người đọc không phân biệt được |
| `[40b]` `[42]` `[42a]` `[42b]` | bị chặn trên bởi `[40a]`/`[41]`, cả hai đều `max(0,…)`; và không ai đề nghị hoàn một số âm |

Danh sách này ở `VatDeclarationService.MAY_BE_NEGATIVE`, và QA `S-34` kiểm đúng
hai chiều: 13 ô phải **nhận**, 7 ô phải **chặn**.

Hệ quả số học: doanh thu âm đẩy `[36]` âm → `hieuSo` âm → `[41]`/`[43]` dương.
Tức là kỳ đó sinh **số thuế còn được khấu trừ chuyển kỳ sau**, không phải số phải
nộp âm. Đúng như mong đợi.

### Thay đổi có thể chặn thao tác đang chạy được hôm nay

**(5) từ chối thuế suất lạ.** Kỳ nào có hoá đơn bán ra mang thuế suất ngoài
`{0%, 5%, 8%, 10%, KCT, KKKNT}` sẽ **không đồng bộ được** cho tới khi dữ liệu
hoá đơn được sửa. Trên tenant QA hiện không có kỳ nào như vậy, nhưng tenant
khác thì chưa kiểm.

**(4) không lẫn loại kỳ.** Nếu tenant nào đang có **cả** tờ khai tháng lẫn quý
trong cùng một năm, họ sẽ **không tạo được tờ khai mới nào của năm đó** cho tới
khi xoá kỳ thừa. Nên rà trước khi deploy:

```sql
select biz_key, period_year, count(distinct period_type) t
  from vat_decl where del_yn = 'N'
 group by 1, 2 having count(distinct period_type) > 1;
```

Ra dòng nào thì xử lý trước.

---

## Không cần migration

Không có Flyway nào mới. Bốn cột hai truy vấn mới dùng (`PERIOD_TYPE`,
`PERIOD_YEAR`, `SUPPL_NO`, `TO_DT`) đều đã tồn tại — `ddl-auto: validate` sẽ
không chặn boot. Đây thường là điểm hỏng lớn nhất của một lần deploy; lần này
nó trống.

---

## Kiểm thử

**2730 test · 2 đỏ · 21 bỏ qua.** Hai cái đỏ là **môi trường máy dựng**:
`SchedulerConfigurationContractTest` đòi `wecms.dispatch.efs.base-url` trong
profile `local`, mà `application-local.yml` (gitignored) trên máy đó có từ
02/06, trước việc dispatch tháng 8. Thuộc tính này **có** trong
`application-sit.yml` nên SIT không ảnh hưởng.

Riêng VAT: **46 test, 0 đỏ.**

---

## Xác nhận sau deploy

```bash
curl -s https://wecms-api.kosign.dev/actuator/health

cd /opt/wecms/qa && git pull && cd /opt/wecms
python3 qa/agent/run-all.py --yes
```

### Cơ sở đối chiếu — lần chạy 13/08/2026, trước deploy

**16 phép kiểm đỏ, và cả 16 đều là bản chưa deploy.** Không còn cái nào chưa
giải thích được. Sau deploy, cả 16 phải chuyển xanh trong **một** lần chạy; cái
nào còn đỏ là hồi quy thật, không phải nợ cũ.

| Bản sửa | Phép kiểm đang đỏ | Chờ thấy |
|---|---|---|
| 1 · `[28]` bỏ `[33a]` | `S-31` | `[28]` không đổi khi `[33a]` đổi |
| 2 · guard `[21]` | `S-19` | `KHOA BI CHAN: WBE_VAT_DECL_NO_ACTIVITY_CONFLICT` |
| 3 · kỳ chưa kết thúc | `S-23` | `tao duoc … nhung KHOA BI CHAN: WBE_VAT_DECL_PERIOD_NOT_ENDED` |
| 4 · loại kỳ theo năm | `S-05` `S-06` | `bi chan: WBE_VAT_DECL_PERIOD_TYPE_MISMATCH` |
| 5 · `[25]`/`[33]`/thuế suất lạ | `S-26` `S-29` `S-36` `S-42` | `[33]` giữ thuế thật; `[25]` nhập tay sống qua lần sửa ô khác; thuế suất lạ báo `WBE_VAT_DECL_UNKNOWN_TAX_RATE` thay vì im lặng nuốt 60.000.000 |
| 6 · làm tròn đồng | `S-35` + 4 phép L1 (`VAT-INV-frac`, `VAT-EXP-xml` ở 02/2026 và 06/2026) | mọi chỉ tiêu là số nguyên đồng; XML và API khớp `ct23` |
| 7 · chỉ tiêu âm | `S-07` `S-34` | `S-07` đồng bộ được, `[43]` ra số dương; `S-34` báo `13 o nhan so am, 7 o chan` |

Một dấu hiệu **chưa deploy** dễ nhận: `S-34` in `ct24 BI CHAN`, `ct32 BI CHAN`…
— nghĩa là build đang chạy còn chặn cả 20 ô.

### Không nằm trong deploy này

| | |
|---|---|
| `S-44` | bỏ qua thật — cần một tenant thứ hai có hoá đơn cùng kỳ |
| `S-06` | ngoài việc chặn lẫn loại kỳ, còn một câu **hỏi PO chưa trả lời**: khi đổi tháng→quý qua ranh giới năm, `[22]` lấy từ `[43]` của kỳ tháng cuối hay bằng 0? |

---

## Nếu phải quay lại

Không có migration nên rollback là triển khai lại artifact cũ, không cần đụng
DB. Dữ liệu do bản mới ghi ra vẫn đọc được bằng bản cũ: các cột không đổi, chỉ
giá trị `[25]`/`[33]` và phần thập phân là khác.
