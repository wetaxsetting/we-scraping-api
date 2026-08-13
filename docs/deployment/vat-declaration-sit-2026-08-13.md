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

**Bốn mã lỗi mới** đã có trong `messages.properties`, `_en`, `_vi`.

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
python3 qa/l3/run-vat-business.py --group cong-thuc --type M --yes
python3 qa/l3/run-vat-cases.py --all --type M --yes
```

Chờ chuyển sang PASS:

| | Chờ thấy |
|---|---|
| `S-26` | `[33]` giữ thuế thật, không bị suy theo 10% |
| `S-29` | `[25]` nhập tay giữ được qua lần sửa ô khác |
| `S-31` | `[28]` không đổi khi `[33a]` đổi |
| `S-05` `S-06` | `bi chan: WBE_VAT_DECL_PERIOD_TYPE_MISMATCH` |
| `S-19` | `KHOA BI CHAN: WBE_VAT_DECL_NO_ACTIVITY_CONFLICT` |
| `S-23` | `tao duoc … nhung KHOA BI CHAN: WBE_VAT_DECL_PERIOD_NOT_ENDED` |

Và `run-vat-indicators.py --no-sql` phải hết `VAT-INV-frac` / `VAT-EXP-xml` ở
02/2026 và 06/2026 — đó là bản làm tròn.

---

## Nếu phải quay lại

Không có migration nên rollback là triển khai lại artifact cũ, không cần đụng
DB. Dữ liệu do bản mới ghi ra vẫn đọc được bằng bản cũ: các cột không đổi, chỉ
giá trị `[25]`/`[33]` và phần thập phân là khác.
