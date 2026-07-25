import sys
import os

paper_path = r'D:\research_2\scientific_paper_vi.md'

with open(paper_path, 'r', encoding='utf-8', errors='replace') as f:
    text = f.read()

# Header and Section 1
sec1_tag = "## 1. Giới thiệu (Introduction)"
sec2_tag = "## 2. Các Nghiên cứu Liên quan (Related Work)"
sec5_tag = "## 5. Kết quả (Results)"
sec6_tag = "## 6. Thảo luận (Discussion)"
sec7_tag = "## 7. Kết luận (Conclusion)"
ack_tag  = "## Lời cảm ơn (Acknowledgements)"

i1 = text.find(sec1_tag)
i2 = text.find(sec2_tag)
i5 = text.find(sec5_tag)
i6 = text.find(sec6_tag)
i7 = text.find(sec7_tag)
i_ack = text.find(ack_tag)

header_and_sec1 = text[:i2]

# Concise Sections 2 - 4
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

# Now let's process Section 5, 6, 7 from text
sec5_text = text[i5:i6]
sec6_text = text[i6:i7]
sec7_text = text[i7:i_ack]
rest = text[i_ack:]

print("Original Sec 5 words:", len(sec5_text.split()))
print("Original Sec 6 words:", len(sec6_text.split()))
print("Original Sec 7 words:", len(sec7_text.split()))
