import sys
import os

paper_path = r'D:\research_2\scientific_paper_vi.md'

with open(paper_path, 'r', encoding='utf-8', errors='replace') as f:
    text = f.read()

# Headers
sec2_tag = "## 2. Các Nghiên cứu Liên quan (Related Work)"
sec5_tag = "## 5. Kết quả (Results)"
ack_tag  = "## Lời cảm ơn (Acknowledgements)"

i2 = text.find(sec2_tag)
i5 = text.find(sec5_tag)
i_ack = text.find(ack_tag)

header_and_sec1 = text[:i2]
rest = text[i_ack:]

sec2_4_clean = """## 2. Các Nghiên cứu Liên quan (Related Work)

### 2.1 Các Nghiên cứu Thực nghiệm về Java Virtual Threads
Project Loom trong Java 21 đã mở ra nhiều nghiên cứu thực nghiệm. Lašić và cộng sự [2] ghi nhận sự cải thiện thông lượng của VT trong ứng dụng Spring Boot, nhưng chưa có mô hình dự đoán. Beronić và cộng sự [4] khảo sát mô hình đồng thời có cấu trúc (structured concurrency) như giải pháp thay thế cho reactive. Hu [5] và Ponnampalam [6] đánh giá hiệu năng VT so với PT trên SPECjbb2015 và các hệ thống thời gian thực. Šimatović và cộng sự [7] và Charlak và cộng sự [1] khảo sát hành vi GC và so sánh VT với reactive programming. Hầu hết các nghiên cứu này báo cáo đo lường thực nghiệm mà chưa chính thức hóa ranh giới định lượng nơi các mô hình thực thi phân kỳ.

### 2.2 Mô hình Hàng chờ Áp dụng cho Thread Pool
Lý thuyết hàng chờ M/M/c và Erlang-C được áp dụng rộng rãi cho thread pool cổ điển [8], pooling kết nối [9], và độ đồng thời cơ sở dữ liệu [10]. Wu và cộng sự [11] đề xuất hàng chờ không nghẽn (BLQ), trong khi Poutanen và cộng sự [12] mô hình hóa các đường ống API gateway bất đồng bộ. Tuy nhiên, các mô hình hàng chờ truyền thống chưa từng được áp dụng để giải thích sự chuyển đổi tiền tệ tài nguyên của virtual threads, nơi kênh phục vụ (carrier thread) bị tách rời khỏi tồn kho nghẽn.

---

## 3. Kiến thức Nền tảng: Lý thuyết Hàng chờ và Erlang-C (Background: Queueing Theory and Erlang-C)

### 3.1 Mô hình M/M/c
Chúng tôi mô hình hóa bể kết nối như một hàng chờ M/M/c với tiến trình đến Poisson tốc độ λ (yêu cầu/giây), thời gian phục vụ hàm mũ trung bình 1/μ (giây), c máy chủ (dung lượng bể kết nối), và phòng chờ vô hạn (FCFS). Cường độ giao thông trên mỗi máy chủ là ρ = λ / (c · μ), điều kiện ổn định là ρ < 1.

### 3.2 Công thức Erlang-C
Xác suất một yêu cầu phải chờ được tính bằng công thức Erlang-C:

$$C(c, a) = \\frac{\\frac{a^c}{c!} \\frac{c}{c - a}}{\\sum_{k=0}^{c-1} \\frac{a^k}{k!} + \\frac{a^c}{c!} \\frac{c}{c - a}}$$

trong đó a = λ / μ là lượng giao thông đề nghị (Erlangs). Thời gian chờ trung bình W_q, số lượng trung bình trong hàng chờ L_q, và độ trễ trung bình W được xác định theo:

$$W_q = \\frac{C(c, a)}{c\\mu - \\lambda}, \\quad L_q = \\lambda W_q, \\quad W = W_q + \\frac{1}{\\mu}$$

### 3.3 Ứng dụng cho Virtual Threads
Dưới Virtual Threads, các *kênh phục vụ* (service channels) trong mô hình hàng chờ là các slot trong bể kết nối chứ không phải luồng carrier. Khi bể đầy, một yêu cầu VT sẽ park continuation của nó trên heap và giải phóng luồng carrier. Do đó, các slot trong bể đóng vai trò là c máy chủ Erlang-C cho cả hai mô hình thực thi, cho phép sử dụng cùng một tham số hóa M/M/c để mô hình hóa hệ thống.

---

## 4. Phương pháp Nghiên cứu (Methodology)

### 4.1 Hệ thống được Kiểm thử (System Under Test)
Chúng tôi đánh giá trên microservice Spring Boot 3.2 kết nối với Microsoft SQL Server 2019 qua JDBC (HikariCP pool). Tải công việc sử dụng truy vấn JPQL kết hợp `WAITFOR DELAY '00:00:00.050'` để tạo độ trễ nghẽn được kiểm soát, đảm bảo thời gian giữ kết nối phù hợp trực tiếp với thời gian phục vụ 1/μ trong Erlang-C. Máy chủ thử nghiệm độc lập trang bị CPU Intel Core i7 thế hệ 12, RAM 32 GB, OpenJDK 21.0.2.

### 4.2 Thiết kế Đánh giá Hiệu năng
Bài báo thực hiện ba chương trình benchmark bổ sung:
* **Nghiên cứu 1 — ArrivalSweepBenchmark (chính, open-loop):** Thực thi giả định đến Poisson M/M/c với bể kết nối c = 50. Lưới mức sử dụng gồm 10 mức: ρ ∈ {0.30, 0.50, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.95, 1.05}. Mỗi ô (ρ, mode) được chạy 20 lần lặp (10s warmup + 45s đo lường). Độ dài hàng chờ E[N_q] được lấy mẫu trực tiếp từ `HikariPoolMXBean`. Benchmark chạy trên MSSQL và được lặp lại độc lập trên PostgreSQL (§5.2).
* **Nghiên cứu 2 — PoolSweepValidationBenchmark (phụ, burst-arrival):** Quét kích thước bể kết nối {10, 25, 50, 100, 200} dưới tải đến dạng bùng nổ (4,000 VT) để xác thực tương quan bộ nhớ heap.
* **Nghiên cứu 3 — HttpSleepBenchmark (nền tảng HTTP):** Thay thế JDBC bằng HTTP I/O (`HttpClient` + `Semaphore(50)`) để kiểm tra tính chuyển giao trên cả MSSQL và PostgreSQL (§5.4).

Các dự đoán Erlang-C cho tất cả các điểm hoạt động đã được đăng ký trước (pre-registered).

### 4.3 Các Chỉ số (Metrics)
* **Độ trễ trung bình (W) và P99** (ms): Thời gian phản hồi yêu cầu đầu-cuối.
* **Độ dài hàng chờ (N_q)**: Số lượng kết nối đang chờ HikariCP trung bình.
* **Số lượng luồng & Bộ nhớ Heap** (MB): Số luồng JVM và mức chiếm dụng heap trung bình.
* **Tạm dừng GC & Số lần GC**: Tổng thời gian tạm dừng và số sự kiện thu gom từ `GarbageCollectorMXBean`.
* **Tốc độ tăng trưởng heap ròng** (MB/s): Tỷ lệ tích tụ heap trung bình trong cửa sổ đo lường.

### 4.4 Phân tích Thống kê (Statistical Analysis)
Sai số dự đoán được định lượng qua MAE, RMSE, và MAPE trên 9 mức ρ < 1. Do phần dư không tuân theo phân phối chuẩn (Shapiro-Wilk p < 0.05), kiểm định phi tham số **Wilcoxon signed-rank** được sử dụng cho so sánh ghép đôi. Hiệu ứng được đánh giá qua **Cohen's d** và **Cliff's δ**. Điểm gãy ρ* được xác định bằng hồi quy từng đoạn tự động (piecewise regression) kèm 95% CI bootstrap (2,000 resamples).

### 4.5 Các Giả định của Mô hình (Model Assumptions)

| Nhãn | Giả định | Trạng thái trong Nghiên cứu này |
| ------ | --------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| **A1** | Tiến trình đến Poisson (CV = 1) | CV giữa các lần đến đo được tăng từ ≈1.5 (ρ=0.30) lên ≈2.6–2.9 gần bão hòa |
| **A2** | Thời gian phục vụ hàm mũ | Phù hợp ở Vùng I; phân phối bị lệch khi tiến gần bão hòa |
| **A3** | Không pinning luồng carrier | Đảm bảo bằng thiết kế tải công việc (không có `synchronized`/JNI nghẽn) |
| **A4** | Nguồn nghẽn đơn lẻ đồng nhất | Đảm bảo qua tải công việc JDBC/HTTP thuần túy |
| **A5** | Tải công việc mở vòng (open-loop) | Thực thi qua bộ phát tải có thời hạn tuyệt đối |

---
"""

sec5_6_7_clean = """## 5. Kết quả (Results)

### 5.1 RQ1 — Tiêu thụ Tài nguyên Phù hợp với Sự chuyển đổi Tiền tệ Tài nguyên
Ở mức sử dụng thấp (ρ ≤ 0.50), cả hai mô hình thực thi thể hiện độ trễ tương đương (PT tại ρ = 0.30: 56.3 ms, 95% CI ±0.257 ms; VT: 56.4 ms, ±0.168 ms, n = 20). Khi mức sử dụng tăng, tiêu thụ tài nguyên phản ánh đúng sự chuyển đổi tiền tệ tài nguyên (H1):

* **Platform Threads:** Số lượng luồng tăng đơn điệu theo mức sử dụng (Spearman r = 0.928, p < 0.0001), từ 147.3 luồng tại ρ = 0.30 lên 199.4 tại ρ = 0.95 (243.6 tại ρ = 1.05) trên MSSQL.
* **Virtual Threads:** Số luồng carrier giữ nguyên không đổi (r = 0.110, p = 0.14) ở mức ≈138 luồng xuyên suốt, trong khi độ dài hàng chờ HikariCP tăng từ 0.02 yêu cầu (ρ = 0.30) lên 65.16 (ρ = 0.95). Định luật Little (λ̂·W̄_q = 67.82) xác nhận độ dài hàng chờ đo được với sai lệch 4.1%.

Mức chiếm dụng heap lấy mẫu tương quan yếu với ρ cho cả hai chế độ (r = -0.01 VT, -0.04 PT), nhưng tần suất thu gom GC tương quan rất mạnh (r = 0.99 VT, 0.90 PT). Kết quả này phù hợp với H1: tồn kho chờ dưới VT được biểu diễn bằng các continuation trên heap (phản ánh qua hàng chờ và GC) thay vì luồng gốc như PT (Hình 8).

---

![Hình 8. Tái phân bổ tài nguyên](analysis/paper/fig8_resource_reallocation.png)

**Hình 8.** Tái phân bổ tài nguyên dưới mức sử dụng tăng dần (Nghiên cứu 1, MSSQL, n = 20). PT (ô vuông đỏ) tăng số luồng OS theo tải (r = 0.928). VT (dấu tròn xanh) giữ phẳng số luồng carrier ở 138 luồng (r = 0.110) trong khi độ dài hàng chờ leo dốc.

---

### 5.2 RQ2 — Phép Xấp xỉ Dự đoán Trong một Vùng
Bảng 1 trình bày độ chính xác dự đoán của Erlang-C trên chín mức ρ ổn định (0.30–0.95) đối với MSSQL.

**Bảng 1. Các Chỉ số Xác thực — Erlang-C so với Giá trị Đo được (n = 20 lần lặp mỗi ô, MSSQL)**

| Chỉ số | n | MAE | RMSE | MAPE (%) | R² | r |
| ------------------------- | -- | ---- | ----- | --------- | ------- | ------ |
| Độ trễ trung bình — VT (ms) | 9 | 14.7 | 27.0 | 19.5 | 0.264 | 0.991 |
| Độ trễ trung bình — PT (ms) | 9 | 80.3 | 229.0 | 120.6 | −0.124 | −0.094 |
| Độ trễ trung bình — Tất cả (ms) | 18 | 47.5 | 163.0 | 70.0 | −0.063 | 0.034 |
| Độ dài hàng chờ — VT (yêu cầu) | 9 | 7.1 | 18.0 | 357.2 | 0.210 | 0.998 |
| Độ dài hàng chờ — PT (yêu cầu) | 9 | 27.0 | 77.3 | 39 932.4 | −0.148 | −0.055 |
| Bộ nhớ Heap (quét bể, MB) | 2 | 75.3 | 77.7 | 6.6 | −15.603 | n/a |

*Bootstrap 95% CI (độ trễ, tất cả chế độ): MAPE = 70.0% [7.2–189.1%]; RMSE = 163.0 ms [5.8–281.6 ms].*

---

![Hình 2. Độ trễ so với rho](analysis/paper/fig2_latency_vs_rho.png)

**Hình 2.** Độ trễ trung bình so với mức sử dụng mục tiêu ρ cho VT và PT kèm dự đoán Erlang-C (đường đứt nét). Thanh sai số: 95% CI (n = 20).

---

![Hình 3. Hiệu chuẩn](analysis/paper/fig3_calibration.png)

**Hình 3.** Biểu đồ hiệu chuẩn: Độ trễ trung bình dự đoán so với đo được (a) và độ dài hàng chờ (b).

---

Các thống kê PT gộp tại Bảng 1 bị tác động bởi một điểm ngoại lệ duy nhất tại ρ = 0.75 (độ trễ 753.2 ms, 95% CI ±1417.3 ms, CV ≈ 402%). Nếu loại trừ điểm bất thường này, MAPE của PT giảm từ 120.6% xuống 7.0%, và Pearson r phục hồi về 0.991 (tương đương VT). Trong Vùng I (ρ ≤ 0.70), sai số MAPE độ trễ đạt 12.0% (VT) và 9.1% (PT).

Kiểm định Wilcoxon signed-rank (Bảng 2) cho thấy sai số độ trễ không có lệch thống kê (VT: W = 17.0, p = 0.57; PT: W = 18.0, p = 0.65), nhưng độ dài hàng chờ bị mô hình dự đoán thấp có ý nghĩa thống kê cho cả hai chế độ (VT & PT: W = 0.0, p = 0.0078).

**Bảng 2. Ý nghĩa Thống kê của Sai số Dự đoán (Wilcoxon signed-rank, n = 9 ô mỗi chế độ)**

| So sánh | Kiểm định | Thống kê | *p* | Có ý nghĩa |
| ---------- | -------------------- | --------- | ------ | ----------- |
| Độ trễ VT | Wilcoxon signed-rank | 17.00 | 0.5703 | Không |
| Độ trễ PT | Wilcoxon signed-rank | 18.00 | 0.6523 | Không |
| Hàng chờ VT | Wilcoxon signed-rank | 0.00 | 0.0078 | **Có** |
| Hàng chờ PT | Wilcoxon signed-rank | 0.00 | 0.0078 | **Có** |

**Xác thực độc lập trên PostgreSQL (Bảng 1B & 3B):** Nghiên cứu 1 trên PostgreSQL tái hiện lại hình dạng mô hình: sai số Vùng I (ρ ≤ 0.70) đạt 5.1% (VT) và 6.2% (PT). Điểm gãy được xác định tại ρ* ≈ 0.75 (VT, SSR 96.8%) và ≈ 0.65 (PT, SSR 94.8%). Phía trên ρ*, PostgreSQL phân kỳ dốc hơn MSSQL: tại ρ = 0.95, độ trễ VT đạt 3192.4 ms so với PT 1977.1 ms (Cohen's d = 2.721), và số luồng PT tăng bùng nổ lên 3707.9 luồng.

**Bảng 1B. Các Chỉ số Xác thực — Erlang-C so với Giá trị Đo được, PostgreSQL (n = 20 lần lặp mỗi ô)**

| Chỉ số | n | MAE | RMSE | MAPE (%) | R² | r |
| ------------------------- | -- | ---- | ----- | --------- | ------- | ------ |
| Độ trễ trung bình — VT (ms) | 9 | 441.0 | 1068.2 | 677.4 | −0.195 | 0.992 |
| Độ trễ trung bình — PT (ms) | 9 | 238.2 | 639.1 | 357.9 | −0.145 | 1.000 |
| Độ trễ trung bình — Tất cả (ms) | 18 | 339.6 | 880.2 | 517.7 | −0.163 | 0.960 |
| Độ dài hàng chờ — VT (yêu cầu) | 9 | 326.3 | 798.7 | 16 458.3 | −0.188 | 0.991 |
| Độ dài hàng chờ — PT (yêu cầu) | 9 | 187.8 | 517.3 | 6 300.1 | −0.134 | 1.000 |

**Bảng 3B. Độ trễ Trung bình VT so với PT và Kích thước Hiệu ứng theo Mức sử dụng, PostgreSQL (n = 20 mỗi ô)**

| ρ | VT trung bình (ms) | PT trung bình (ms) | Cohen's *d* | Cliff's δ | Mức độ |
| -------- | ------------- | ------------- | ----------: | --------: | --------------- |
| 0.30 | 55.0 | 54.8 | 0.488 | 0.297 | nhỏ |
| 0.50 | 56.4 | 56.6 | −0.253 | −0.145 | không đáng kể |
| 0.60 | 57.3 | 57.4 | −0.057 | −0.085 | không đáng kể |
| 0.65 | 57.6 | 58.6 | −0.829 | −0.470 | trung bình |
| 0.70 | 60.1 | 61.9 | −0.417 | −0.305 | nhỏ |
| 0.75 | 69.4 | 74.6 | −0.148 | 0.125 | không đáng kể |
| 0.80 | 166.9 | 76.2 | 1.177 | 0.975 | lớn |
| 0.85 | 760.0 | 233.0 | 1.699 | 0.855 | lớn |
| 0.95 | 3192.4 | 1977.1 | 2.721 | 0.990 | lớn |

---

### 5.3 RQ3 — Nơi Mô hình Ngừng Hoạt động
Phân tích ranh giới hiệu lực gồm 4 bước:

1. **Phát hiện điểm gãy:** Hồi quy từng đoạn xác định điểm bắt đầu phân kỳ tại **ρ* ≈ 0.75 (VT, 95% CI [0.75, 0.80])** và **ρ* ≈ 0.70 (PT, 95% CI [0.50, 0.80])** trên MSSQL.
2. **Hướng phần dư:** Ở mức ρ thấp (ρ ≤ 0.80), mô hình đánh giá cao nhẹ độ trễ VT (-14.9% đến -0.9%). Khi ρ > 0.80, mô hình chuyển sang đánh giá thấp độ trễ VT (+20.9% tại ρ = 0.85 và +94.1% tại ρ = 0.95), phù hợp với chi phí tích tụ continuation không được mô hình hóa trong Erlang-C.
3. **Kích thước hiệu ứng (Bảng 3):** Bên dưới ρ*, khác biệt độ trễ giữa VT và PT là không đáng kể (≤ 3 ms). Phía trên ρ*, VT có độ trễ cao hơn PT rõ rệt: tại ρ = 0.85 (VT 82.2 ms vs PT 67.2 ms, Cohen's d = 1.332) và ρ = 0.95 (VT 161.0 ms vs PT 90.1 ms, Cohen's d = 1.111, Δ = 70.9 ms / +79%).

---

![Hình 4. Phân tích phần dư](analysis/paper/fig4_residual.png)

**Hình 4.** Phân tích phần dư sai số dự đoán độ trễ tương đối (%) so với ρ cho VT và PT.

---

**Bảng 3. Độ trễ Trung bình VT so với PT và Kích thước Hiệu ứng theo Mức sử dụng (MSSQL, n = 20 mỗi ô)**

| ρ | VT trung bình (ms) | PT trung bình (ms) | Cohen's *d* | Cliff's δ | Mức độ |
| -------- | ------------- | ------------- | ----------: | --------: | --------------- |
| 0.30 | 56.4 | 56.3 | 0.254 | 0.228 | nhỏ |
| 0.50 | 58.0 | 58.7 | −0.303 | −0.142 | không đáng kể |
| 0.60 | 60.3 | 69.3 | −0.316 | −0.185 | nhỏ |
| 0.65 | 60.4 | 63.3 | −0.545 | −0.440 | trung bình |
| 0.70 | 61.0 | 62.9 | −1.200 | −0.620 | lớn |
| **0.75** | 62.5 | **753.2** | −0.323 | −0.547 | lớn (ngoại lệ) |
| 0.80 | 66.2 | 65.7 | 0.159 | −0.130 | không đáng kể |
| **0.85** | **82.2** | 67.2 | 1.332 | 0.720 | lớn |
| **0.95** | **161.0** | 90.1 | 1.111 | 0.790 | lớn |

4. **Khung giới hạn hoạt động (Hình 1):**

| Vùng | Dải ρ | Đặc tả |
| ------------------------------------ | ------------------- | --------------------------------------------------------------------------------------------------- |
| **I — Không hàng chờ / mô hình hợp lệ** | ρ ≤ **0.70–0.75** | Mô hình đáng tin cậy (MAPE 9–12%); độ trễ VT và PT không khác biệt thực tiễn |
| **II — Chuyển tiếp** | 0.70/0.75 < ρ ≤ 0.90 | Bắt đầu phân kỳ; VT bắt đầu cao hơn PT; mô hình mang tính định hướng |
| **III — Phân kỳ** | 0.90 < ρ ≤ 0.98 | Độ trễ VT cao hơn PT (Δ lên tới 70.9 ms tại ρ = 0.95); mô hình dự đoán thiếu độ trễ và hàng chờ |
| **IV — Bão hòa** | ρ ≥ 1 (ρ = 1.05) | Không ổn định; làm bằng chứng ranh giới |

---

### 5.4 RQ1 (mở rộng) — Bằng chứng GC và Khả năng Chuyển giao Tải HTTP
**Đo lường GC (Bảng 5):** Tần suất GC và thời gian tạm dừng tăng đơn điệu theo ρ ở cả hai chế độ (Spearman r = 0.989 VT, 0.900 PT). VT có mức tăng thời gian tạm dừng dốc hơn (10.9 ms → 74.2 ms, tăng 6.8×) so với PT (12.3 ms → 56.0 ms, tăng 4.6×), phản ánh áp lực thu gom continuation trên heap khi tải tăng.

**Bảng 5. Các Chỉ số GC theo (Chế độ, ρ) — Trung bình n = 20 lần lặp (MSSQL)**

| ρ | Sự kiện GC VT | Tạm dừng GC VT (ms) | Tăng trưởng Heap VT (MB/s) | Sự kiện GC PT | Tạm dừng GC PT (ms) | Tăng trưởng Heap PT (MB/s) |
| ---- | ------------ | ---------------- | --------------------- | ------------ | ---------------- | --------------------- |
| 0.30 | 6.20 | 10.90 | 3.62 | 7.40 | 12.25 | 3.68 |
| 0.50 | 10.45 | 22.90 | 3.65 | 10.65 | 18.60 | 3.67 |
| 0.60 | 12.90 | 31.60 | 3.60 | 15.05 | 32.10 | 3.61 |
| 0.65 | 13.95 | 37.30 | 3.69 | 18.65 | 45.30 | 3.68 |
| 0.70 | 14.90 | 41.45 | 3.63 | 19.10 | 44.75 | 3.61 |
| 0.75 | 16.65 | 44.80 | 3.66 | 19.70 | 58.60 | 3.66 |
| 0.80 | 17.00 | 52.25 | 3.70 | 19.60 | 50.30 | 3.68 |
| 0.85 | 18.65 | 56.40 | 3.58 | 20.75 | 49.35 | 3.63 |
| 0.95 | 21.40 | 66.70 | 3.63 | 21.80 | 54.20 | 3.64 |
| 1.05 | **24.55** | **74.20** | 3.54 | **21.90** | **55.95** | 3.55 |

**Tải công việc HTTP (Nghiên cứu 3):** Thay JDBC bằng HTTP I/O (`HttpClient` + `Semaphore(50)`), kết quả ở Vùng I vẫn thể hiện sự tương đương giữa VT và PT. Tuy nhiên tại điểm bão hòa ρ = 1.05, chế độ thất bại bị đảo ngược: **PT suy thoái nghiêm trọng hơn VT** (độ trễ trung bình PT 1975.5 ms vs VT 1084.5 ms, số luồng PT bùng nổ lên 1737.3 luồng kèm 369 yêu cầu bị từ chối, trong khi VT giữ phẳng ở 132.3 carrier threads và 0 từ chối). Điều này chứng minh rằng hệ quả thực tiễn của tiền tệ tài nguyên phụ thuộc vào tài nguyên hạ nguồn nào bị giới hạn (bể kết nối JDBC vs luồng Tomcat HTTP).

---

## 6. Thảo luận (Discussion)

### 6.1 Tại sao Mô hình Hoạt động Dưới ρ*?
Cơ chế cốt lõi nằm ở Định luật Little ($E[N] = \\lambda W$).

**Bổ đề 1 (Tính Bất biến của Tiền tệ Tài nguyên).** *Sự thể hiện vật lý của N phụ thuộc mô hình (PT: luồng gốc OS; VT: continuation trên heap), nhưng thời gian chờ W là bất biến theo tiền tệ tài nguyên.*

Bên dưới ρ*, sự lựa chọn tiền tệ tài nguyên không ảnh hưởng đến độ trễ. Do đó trong Vùng I, việc chọn VT hay PT nên dựa trên các yếu tố kiến trúc và vận hành (dấu chân bộ nhớ, độ phức tạp mã nguồn) thay vì kỳ vọng khác biệt độ trễ.

### 6.2 Tại sao Dự đoán Suy giảm Phía trên ρ*?
Sự suy giảm mô hình phía trên ρ* xuất phát từ ba giả thuyết chính: (1) Lượt đến bùng nổ hơn Poisson (vi phạm A1, CV tăng từ ≈1.5 lên ≈2.9); (2) Phân phối thời gian phục vụ bị lệch do thời gian chờ bể kết nối (vi phạm A2); và (3) Chi phí tích tụ continuation trên heap ở VT gây ra áp lực GC bổ sung không được Erlang-C tính đến.

### 6.3 Hệ quả Thực tiễn và Nguyên tắc Thiết kế
Khung giới hạn hoạt động mang lại hướng dẫn cho quy hoạch năng lực:

* **DP1: Giám sát độ dài hàng chờ và sự tách rời luồng, không phụ thuộc dung lượng heap thô.** Số luồng carrier của VT không tăng theo tải. Dấu hiệu cảnh báo đặc trưng của VT là độ dài hàng chờ và tần suất GC tăng cao trong khi số luồng giữ phẳng.
* **DP2: Chỉ áp dụng Erlang-C trong vùng hợp lệ (ρ ≤ ρ*).** Trong Vùng II (ρ ≤ 0.90), kết quả chỉ mang tính định hướng. Trong Vùng III (ρ > 0.90), mô hình chỉ đóng vai trò cảnh báo bão hòa.
* **DP3: Quy hoạch năng lực nhận thức chế độ, nền tảng và backend.** Ranh giới ρ* ≈ 0.65–0.75 tương đối nhất quán, nhưng chế độ thất bại bão hòa phụ thuộc vào tài nguyên giới hạn của hệ thống cụ thể (JDBC vs HTTP, MSSQL vs PostgreSQL).

**Bảng 4. Danh mục Tín hiệu Giám sát theo Vùng Hoạt động**

| Tín hiệu | Vùng I (ρ ≤ 0.70–0.75) | Vùng II (lên đến ρ ≈ 0.90) | Vùng III (ρ > 0.90) |
| ---------------------- | ------------------------- | --------------------------- | -------------------------- |
| Số lượng luồng carrier | Mang thông tin cho PT; Gây hiểu lầm cho VT | Gây hiểu lầm (VT); Hữu ích (PT) | Gây hiểu lầm (VT); ⚠ Cảnh báo bùng nổ (PT HTTP) |
| Độ dài hàng chờ yêu cầu | Đáng tin cậy (cả hai) | Đáng tin cậy (cả hai) | **Tín hiệu chính** (cả hai) |
| Mức chiếm dụng Heap (MB) | Không phát hiện tín hiệu rõ | Không phát hiện tín hiệu | Không phát hiện tín hiệu |
| Tần suất thu gom GC | Theo dõi phụ | Theo dõi (cả hai) | Theo dõi (cả hai) |
| Đầu ra mô hình Erlang-C | Đáng tin cậy cho độ trễ | Chỉ mang tính định hướng | Chỉ làm cảnh báo bão hòa |

### 6.4 Các Hạn chế (Limitations)
1. **Tổng quát hóa backend:** Ranh giới ρ* xuất hiện trên cả MSSQL và PostgreSQL, nhưng mức độ phân kỳ và bùng nổ luồng PT đặc thù cho từng cấu hình DBMS/driver.
2. **Loại nghẽn:** Đánh giá tập trung vào JDBC đồng bộ và HTTP sleep; chưa mở rộng sang Redis, Kafka, hay I/O hỗn hợp.
3. **Mẫu thực nghiệm:** Đạt 20 lần lặp giúp thu hẹp CI ở tải thấp/vừa, nhưng biến động vẫn cao gần bão hòa.
4. **Loại trừ Pinning (A3):** Tải công việc không chứa `synchronized` hay JNI nghẽn. Pinning sẽ làm sụp đổ trừu tượng hóa tiền tệ tài nguyên.

### 6.5 Các Đe dọa đối với Tính Hiệu lực
* **Tính hiệu lực bên trong:** Khởi động 10s giảm bớt nhiễu JIT, nhưng phương sai cao ở ρ bão hòa có thể chứa tác động quá độ.
* **Tính hiệu lực bên ngoài:** Kết quả từ môi trường máy đơn; môi trường container/cloud chia sẻ CPU có thể dịch chuyển ρ*.
* **Tính hiệu lực khái niệm:** Erlang-C giả định trạng thái ổn định, trong khi đo lường thực thi trong cửa sổ 45s mỗi ô.

### 6.6 Mối quan hệ với các Nghiên cứu Trước
Khác với các nghiên cứu so sánh thông lượng đơn thuần [1]–[7], bài báo này chính thức hóa ranh giới tính hiệu lực định lượng của mô hình dự đoán và cung cấp lăng kính tiền tệ tài nguyên để giải thích cơ chế phân kỳ.

### 6.7 Các Hướng nghiên cứu trong Tương lai
1. Phân tích độ nhạy của ρ* theo thời gian phục vụ (20/40/80 ms).
2. Chẩn đoán chi tiết sự bùng nổ luồng PT trên PostgreSQL.
3. Profiling trực tiếp số lượng đối tượng continuation trên heap dưới VT.
4. Tổng quát hóa khái niệm tiền tệ tài nguyên sang Goroutines, Async/Await, và Reactive frameworks.

---

## 7. Kết luận (Conclusion)

Bài báo này xây dựng và xác thực thực nghiệm một mô hình dự đoán vùng hoạt động cho Java Virtual Threads và Platform Threads dưới tải công việc nghẽn. Bằng cách mô hình hóa các slot bể kết nối làm số máy chủ Erlang-C, phép xấp xỉ M/M/c chuẩn dự đoán độ trễ cho cả hai mô hình thực thi trong vùng chưa bão hòa, trong khi khái niệm *tiền tệ tài nguyên* giải thích cho sự phân kỳ khi bão hòa.

Kết quả thực nghiệm trên hai backend DBMS (MSSQL và PostgreSQL) và hai nền tảng I/O (JDBC và HTTP) xác nhận rằng bên dưới ρ* ≈ 0.70–0.75, VT và PT không thể phân biệt về độ trễ và mô hình đạt độ chính xác MAPE ≈ 5–12%. Phía trên ρ*, hai mô hình phân kỳ tùy thuộc vào tài nguyên bị giới hạn của môi trường hạ nguồn.

Những phát hiện này khẳng định rằng quy hoạch năng lực cần có nhận thức về chế độ và nền tảng. Độ dài hàng chờ và tần suất GC — chứ không phải riêng số lượng luồng hay mức chiếm dụng heap — là các tín hiệu giám sát cốt lõi cho các ứng dụng dựa trên Virtual Threads.

---

"""

full_updated_text = header_and_sec1 + sec2_4_clean + sec5_6_7_clean + rest

with open(paper_path, 'w', encoding='utf-8') as f:
    f.write(full_updated_text)

print("Manuscript written successfully!")
print("New total length (chars):", len(full_updated_text))
print("New total word count:", len(full_updated_text.split()))
