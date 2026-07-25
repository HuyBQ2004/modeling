# Mô hình hóa và Dự đoán Độ trễ Trong các Vùng Hoạt động của Java Virtual Threads: Một Mô hình Dự đoán Đã được Xác thực về Độ Đồng thời Nghẽn (Blocking Concurrency)

**[Đang được phản biện — JSS / SPE]**

*Phiên bản bản thảo với các bổ sung do người phản biện yêu cầu: lưới ρ mịn hơn, đo lường trực tiếp GC/heap, xác thực nền tảng nghẽn HTTP, lặp lại n = 20 lần cho mỗi điểm hoạt động, và một nghiên cứu lặp lại độc lập trên PostgreSQL cho nghiên cứu chính (dữ liệu: 15-07-2026 đến 17-07-2026; phân tích chéo DBMS trên PostgreSQL được thêm vào ngày 22-07-2026).*

---

**Tóm tắt (Abstract)** — Bài báo này xây dựng và xác thực một mô hình dự đoán thực nghiệm về vùng hoạt động mà trong đó Java Virtual Threads (VT) và Platform Threads (PT) tạo ra hoặc không tạo ra sự khác biệt có ý nghĩa về độ trễ. Chúng tôi áp dụng phép xấp xỉ Erlang-C / M/M/c chuẩn bằng cách coi các slot trong bể kết nối cơ sở dữ liệu làm các kênh phục vụ cho cả hai mô hình thực thi. Lăng kính *tiền tệ tài nguyên* (resource currency) giải thích cho sự liệm biệt về tài nguyên vật lý mà hệ thống tiêu tốn trong khi các yêu cầu chờ đợi (luồng gốc OS đối với PT, continuation trú đóng trên heap đối với VT). Xác thực thực nghiệm trên một microservice Spring Boot / SQL Server (lặp lại n = 20 cho 10 mức sử dụng ρ) xác định ranh giới phân kỳ của hệ thống tại ρ* ≈ 0.75 (VT) và ρ* ≈ 0.70 (PT). Bên dưới ρ*, mô hình dự đoán độ trễ với sai số MAPE ≈ 9–12%, và độ trễ của VT và PT không thể phân biệt về mặt thống kê. Bên trên ρ*, độ trễ VT phân kỳ so với PT (+22% tại ρ = 0.85 và +79% tại ρ = 0.95). Đánh giá độc lập trên PostgreSQL tái hiện ranh giới ρ* ≈ 0.65–0.75 với sai số Vùng I thấp hơn (4.7–6.2%), trong khi thử nghiệm trên nền tảng HTTP cho thấy chế độ thất bại bão hòa phụ thuộc vào môi trường hạ nguồn. Kết quả hỗ trợ việc quy hoạch năng lực hệ thống có nhận thức về chế độ, chỉ ra rằng độ dài hàng chờ và tần suất GC mới là các chỉ số giám sát đáng tin cậy.

**Từ khóa (Keywords)** — Java Virtual Threads, Project Loom, lý thuyết hàng chờ, Erlang-C, M/M/c, mô hình hóa hiệu năng, tải công việc nghẽn (blocking workload), các vùng hoạt động, quy hoạch năng lực (capacity planning), tiền tệ tài nguyên (resource currency).

---

## 1. Giới thiệu (Introduction)

### 1.1 Vấn đề là Sự chờ đợi, Không phải Tính toán (The Problem Is Waiting, Not Computing)

Các microservice bị giới hạn bởi I/O dành phần lớn thời gian sống của một yêu cầu để bị nghẽn (blocked) trên các tài nguyên hạ nguồn — như bể kết nối cơ sở dữ liệu có giới hạn. Điều khác biệt giữa các mô hình luồng không phải là *liệu* các yêu cầu có chờ đợi hay không, mà là *tài nguyên nào bị chiếm giữ trong khi chờ đợi*.

Chúng tôi chính thức hóa sự liệm biệt này thông qua khái niệm **tiền tệ tài nguyên** (resource currency):

> **Định nghĩa 1 (Tiền tệ tài nguyên - Resource Currency).** *Tiền tệ tài nguyên của sự nghẽn là tài nguyên vật lý R mà mức độ chiếm dụng của nó tỷ lệ thuận với lượng tồn kho (inventory) các yêu cầu đang chờ N dưới một mô hình thực thi M: occupancy(R) ∝ E[N]. Dưới Platform Threads, R là các luồng OS gốc (mỗi yêu cầu giữ một luồng). Dưới Virtual Threads, R là trạng thái continuation trú đóng trên heap (mỗi yêu cầu park một continuation, luồng carrier được giải phóng).*

Dưới Platform Threads, thread pool đóng vai trò như một mức trần đồng thời cứng. Dưới Virtual Threads (Java 21+), thao tác nghẽn unmount continuation khỏi luồng carrier, giải phóng luồng carrier cho bộ lập lịch trong khi trạng thái chờ được lưu trữ dưới dạng các đối tượng continuation trên heap.

### 1.2 Khoảng trống trong các Nghiên cứu Hiện tại (The Gap in Existing Work)

Các nghiên cứu hiện tại báo cáo rằng Virtual Threads cải thiện khả năng mở rộng dưới các tải công việc nghẽn [1]–[6]. Tuy nhiên, các đánh giá này chủ yếu mang tính thực nghiệm và thiếu sự phân tích về *khi nào* và *tại sao* hai mô hình phân kỳ. Trong phạm vi các nghiên cứu được khảo sát ở §2, **theo hiểu biết của chúng tôi**, chưa có nghiên cứu nào dự đoán được ranh giới của vùng mà sự khác biệt trở nên có ý nghĩa, hoặc chính thức hóa sự chuyển đổi tiền tệ tài nguyên được thảo luận ở đây; chúng tôi không thể loại trừ khả năng có các nghiên cứu như vậy nằm ngoài phạm vi khảo sát của chúng tôi. Bài báo này giải quyết các câu hỏi định lượng còn thiếu: Ở mức độ sử dụng nào VT bắt đầu phân kỳ khỏi PT? Mô hình hàng chờ cổ điển dự đoán chính xác đến đâu và suy giảm ở đâu?

### 1.3 Câu hỏi Nghiên cứu và các Giả thuyết (Research Questions and Hypotheses)

Chúng tôi đưa ra ba câu hỏi nghiên cứu và các giả thuyết tương ứng:

* **RQ1** — Sự nghẽn làm thay đổi tiêu thụ tài nguyên như thế nào dưới mỗi mô hình thực thi?
* **RQ2** — Phép xấp xỉ hàng chờ (M/M/c, Erlang-C) có thể dự đoán độ trễ và độ dài hàng chờ trong các vùng hoạt động vừa phải không?
* **RQ3** — Mô hình ngừng hoạt động ở đâu, và điều gì xảy ra ở đó?

**H1** — Sự nghẽn thay đổi tiền tệ tài nguyên của sự chờ đợi: PT giữ các luồng gốc tỷ lệ thuận với lượng tồn kho bị nghẽn; VT park các continuation trong bộ nhớ heap trong khi giữ bể luồng carrier ổn định.

**H2** — Phép xấp xỉ Erlang-C / M/M/c dự đoán độ trễ và độ dài hàng chờ trong vùng hoạt động vừa phải (ρ ≤ ρ*), với chất lượng dự đoán suy giảm ở phía trên ρ*.

**H3** — Sai số dự đoán tăng khi mức sử dụng vượt quá điểm gãy bão hòa ρ*, do vi phạm tăng dần các giả định mô hình.

### 1.4 Các Đóng góp (Contributions)

Bài báo đóng góp một *mô hình vùng hoạt động dự đoán* đã xác thực cho hai mô hình thực thi JVM, kết hợp với từ vựng giải thích tiền tệ tài nguyên:

1. **Khung giới hạn hoạt động VT/PT đã đư�## 2. Các Nghiên cứu Liên quan (Related Work)

### 2.1 Các Nghiên cứu Thực nghiệm về Java Virtual Threads
Project Loom trong Java 21 đã mở ra nhiều nghiên cứu thực nghiệm. Lašić và cộng sự [2] ghi nhận sự cải thiện thông lượng của VT trong ứng dụng Spring Boot, nhưng chưa có mô hình dự đoán. Beronić và cộng sự [4] khảo sát mô hình đồng thời có cấu trúc (structured concurrency) như giải pháp thay thế cho reactive. Hu [5] và Ponnampalam [6] đánh giá hiệu năng VT so với PT trên SPECjbb2015 và các hệ thống thời gian thực. Šimatović và cộng sự [7] và Charlak và cộng sự [1] khảo sát hành vi GC và so sánh VT với reactive programming. Hầu hết các nghiên cứu này báo cáo đo lường thực nghiệm mà chưa chính thức hóa ranh giới định lượng nơi các mô hình thực thi phân kỳ.

### 2.2 Mô hình Hàng chờ Áp dụng cho Thread Pool
Lý thuyết hàng chờ M/M/c và Erlang-C được áp dụng rộng rãi cho thread pool cổ điển [8], pooling kết nối [9], và độ đồng thời cơ sở dữ liệu [10]. Wu và cộng sự [11] đề xuất hàng chờ không nghẽn (BLQ), trong khi Poutanen và cộng sự [12] mô hình hóa các đường ống API gateway bất đồng bộ. **Theo hiểu biết của chúng tôi**, các công trình hàng chờ hiện có chưa được áp dụng để giải thích hành vi *chuyển đổi tiền tệ* (currency-transforming) của virtual threads theo phong cách Loom, nơi số kênh phục vụ (carrier threads) bị tách rời khỏi tồn kho nghẽn; chúng tôi không thể khẳng định không có công trình nào như vậy tồn tại bên ngoài phạm vi khảo sát này.

---

## 3. Kiến thức Nền tảng: Lý thuyết Hàng chờ và Erlang-C (Background: Queueing Theory and Erlang-C)

### 3.1 Mô hình M/M/c
Chúng tôi mô hình hóa bể kết nối như một hàng chờ M/M/c với tiến trình đến Poisson tốc độ λ (yêu cầu/giây), thời gian phục vụ hàm mũ trung bình 1/μ (giây), c máy chủ (dung lượng bể kết nối), và phòng chờ vô hạn (FCFS). Cường độ giao thông trên mỗi máy chủ là ρ = λ / (c · μ), điều kiện ổn định là ρ < 1.

### 3.2 Công thức Erlang-C
Xác suất một yêu cầu phải chờ được tính bằng công thức Erlang-C:

$$C(c, a) = \frac{\frac{a^c}{c!} \frac{c}{c - a}}{\sum_{k=0}^{c-1} \frac{a^k}{k!} + \frac{a^c}{c!} \frac{c}{c - a}}$$

trong đó a = λ / μ là lượng giao thông đề nghị (Erlangs). Thời gian chờ trung bình W_q, số lượng trung bình trong hàng chờ L_q, và độ trễ trung bình W được xác định theo:

$$W_q = \frac{C(c, a)}{c\mu - \lambda}, \quad L_q = \lambda W_q, \quad W = W_q + \frac{1}{\mu}$$

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
* **Tốc độ tăng trưởng heap ròng** (MB/s): Tỷ lệ tích tụ heap ròng trong cửa sổ đo lường, tính bằng (heap_max − heap_đầu_cửa_sổ) / window_seconds. Đây **không** phải đo lường tốc độ phân bổ (allocation rate) thực sự — các JVM MXBean chuẩn (`MemoryMXBean`) chỉ quan sát được mức chiếm dụng heap tại thời điểm lấy mẫu, không thấy được các byte đã được GC thu hồi giữa các lần lấy mẫu. Chúng tôi dùng thuật ngữ "tăng trưởng heap ròng" (net heap growth) thay vì "tốc độ phân bổ" (allocation rate) xuyên suốt bài báo để tránh phóng đại những gì được đo; đây là proxy thô cho sự tích tụ đối tượng continuation dưới VT, không phải đo lường trực tiếp khối lượng phân bổ. Tốc độ phân bổ thực sự sẽ cần `ThreadMXBean.getThreadAllocatedBytes()` hoặc JFR allocation profiling.

### 4.4 Phân tích Thống kê (Statistical Analysis)
Sai số dự đoán được định lượng qua MAE, RMSE, và MAPE trên 9 mức ρ < 1. Do phần dư không tuân theo phân phối chuẩn (Shapiro-Wilk p < 0.05), kiểm định phi tham số **Wilcoxon signed-rank** được sử dụng cho so sánh ghép đôi. Hiệu ứng được đánh giá qua **Cohen's d** và **Cliff's δ**. Điểm gãy ρ* được xác định bằng hồi quy từng đoạn tự động (piecewise regression) kèm 95% CI bootstrap (2,000 resamples).

**Ghi chú về phương pháp CI.** Tất cả khoảng tin cậy 95% trong bài báo này (CI per-cell của độ trễ và hàng chờ trong văn bản và Hình 2) được tính từ các đo lường thô per-replicate sử dụng giá trị tới hạn Student-t (df = n − 1, n = 20) và **độ lệch chuẩn mẫu** với hiệu chỉnh Bessel (mẫu số n − 1). Điều này khác với cột `CI95_*` được bộ khung benchmark ghi, vốn dùng hệ số z = 1.96 cố định trên SD quần thể (mẫu số n) — đánh giá thấp khoảng tin cậy khoảng 9–13% tại n = 20. Các cột harness được giữ trong dữ liệu thô nhưng không được trích dẫn trực tiếp trong bài báo này.

### 4.5 Các Giả định của Mô hình (Model Assumptions)

| Nhãn | Giả định | Trạng thái trong Nghiên cứu này |
| ------ | --------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| **A1** | Tiến trình đến Poisson (CV = 1) | CV giữa các lần đến đo được tăng từ ≈1.5 (ρ=0.30) lên ≈2.6–2.9 gần bão hòa |
| **A2** | Thời gian phục vụ hàm mũ | Phù hợp ở Vùng I; phân phối bị lệch khi tiến gần bão hòa |
| **A3** | Không pinning luồng carrier | Đảm bảo bằng thiết kế tải công việc (không có `synchronized`/JNI nghẽn) |
| **A4** | Nguồn nghẽn đơn lẻ đồng nhất | Đảm bảo qua tải công việc JDBC/HTTP thuần túy |
| **A5** | Tải công việc mở vòng (open-loop) | Thực thi qua bộ phát tải có thời hạn tuyệt đối |

---
## 5. Kết quả (Results)

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
**Đo lường GC (Bảng 5):** Tần suất GC và thời gian tạm dừng tăng đơn điệu theo ρ ở cả hai chế độ (Spearman r = 0.989 VT, 0.900 PT). VT có mức tăng thời gian tạm dừng dốc hơn (10.9 ms → 74.2 ms, tăng 6.8×) so với PT (12.3 ms → 56.0 ms, tăng 4.6×) — nhất quán với, nhưng không thiết lập được cơ chế của, giả thuyết về áp lực thu gom continuation trên heap khi tải tăng. Cả hai chế độ đều cho thấy GC tăng theo ρ; VT tăng dốc hơn đôi chút, nhưng đây là bằng chứng tương quan (correlational), không phải nhân quả (causal): cần profiling phân bổ per-class để xác nhận trực tiếp.

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

### 6.0 Tại sao Điều này Quan trọng? (Why Does This Matter?)

Đóng góp của bài báo này không phải là thêm một con số VT-so-PT khác vào bảng hiệu năng. Nó là việc chính thức hóa *tại sao* hai mô hình thực thi đôi khi phân kỳ và *ở đâu* sự phân kỳ đó bắt đầu — và làm điều đó thông qua một mô hình dự đoán có thể bác bỏ được (falsifiable), đã được đăng ký trước và xác thực độc lập.

Lăng kính *tiền tệ tài nguyên* (resource currency) là điều cho phép điều này: bằng cách nhận ra rằng tồn kho nghẽn đồng nhất (E[N]) được định danh bằng các tài nguyên vật lý khác nhau dưới mỗi mô hình thực thi, chúng tôi có thể áp dụng một tham số hóa M/M/c duy nhất cho cả hai mô hình, dự đoán cả khi nào chúng không thể phân biệt được và khi nào chúng phân kỳ, và giải thích tại sao các tín hiệu giám sát khác nhau (số luồng, độ dài hàng chờ, GC) theo dõi tải khác nhau tùy thuộc vào chế độ thực thi.

Kết quả là: các nhà lập kế hoạch năng lực có thể lý luận về VT-so-PT không phải bằng trực giác mà bằng ranh giới vùng hoạt động — biết *trong vùng nào* sự lựa chọn không tạo ra sự khác biệt, *trong vùng nào* nó quan trọng theo hướng nào, và *tín hiệu nào* đáng tin cậy trong mỗi chế độ. Đây là kết quả bổ sung cho chứ không thay thế các nghiên cứu thực nghiệm hiện có trong §2.1.

### 6.1 Tại sao Mô hình Hoạt động Dưới ρ*?
Cơ chế cốt lõi nằm ở Định luật Little ($E[N] = \lambda W$).

**Bổ đề 1 (Tính Bất biến của Tiền tệ Tài nguyên).** *Sự thể hiện vật lý của N phụ thuộc mô hình (PT: luồng gốc OS; VT: continuation trên heap), nhưng thời gian chờ W là bất biến theo tiền tệ tài nguyên.*

Bên dưới ρ*, sự lựa chọn tiền tệ tài nguyên không ảnh hưởng đến độ trễ. Do đó trong Vùng I, việc chọn VT hay PT nên dựa trên các yếu tố kiến trúc và vận hành (dấu chân bộ nhớ, độ phức tạp mã nguồn) thay vì kỳ vọng khác biệt độ trễ.

### 6.2 Tại sao Dự đoán Suy giảm Phía trên ρ*?
Sự suy giảm mô hình phía trên ρ* nhất quán với vi phạm của các giả định A1 và A2 (§4.5). Chúng tôi đề xuất các giả thuyết sau — chúng tôi **không** khẳng định cơ chế nhân quả, vì kiểm tra chúng là công việc tương lai:

1. **Lượt đến bùng nổ hơn Poisson (A1 bị vi phạm).** CV giữa các lần đến đo được vượt quá 1 trên toàn bộ lưới (≈1.5–1.6 tại ρ = 0.30, tăng lên ≈2.6–2.9 gần bão hòa) — arrivals bùng nổ hơn Poisson xuyên suốt.
2. **Phân phối thời gian phục vụ bị lệch (A2 bị căng).** Tại mức chiếm dụng hàng chờ cao, thời gian chờ bể kết nối HikariCP trở thành thành phần ngày càng lớn của thời gian phục vụ; phân phối kết hợp không còn được xấp xỉ tốt bằng hàm mũ đơn tham số.
3. **VT-cụ thể: chi phí continuation — bằng chứng yếu hơn dự kiến.** Bằng chứng GC trực tiếp (Bảng 5, §5.4) cho thấy số sự kiện GC và thời gian tạm dừng tăng theo ρ cho **cả hai** chế độ VT (r = 0.99) và PT (r = 0.90), VT tăng dốc hơn đôi chút — đây là hỗ trợ tương quan nhất quán với giả thuyết tích tụ continuation, nhưng không phải bằng chứng nhân quả. Áp lực GC không tách biệt rõ ràng hai chế độ (cả hai đều tăng). **Cơ chế không thể được thiết lập từ dữ liệu instrumentation thu thập ở đây**; profiling phân bổ per-class sẽ cần thiết để xác nhận trực tiếp (§6.7, mục 4).

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
3. **Mẫu thực nghiệm:** Đạt 20 lần lặp giúp thu hẹp CI ở tải thấp/vừa, nhưng biến động vẫn cao gần bão hòa (PT CV = 58% tại ρ = 0.60, VT CV = 44% tại ρ = 0.95).
4. **Loại trừ Pinning (A3):** Tải công việc không chứa `synchronized` hay JNI nghẽn. Pinning sẽ làm sụp đổ trừu tượng hóa tiền tệ tài nguyên.
5. **`System.gc()` trước mỗi replicate.** Benchmark gọi `System.gc()` một lần mỗi replicate cell, ít nhất 10.8 giây trước khi cửa sổ đo lường bắt đầu, để giảm carry-over giữa các lần chạy. Điều này không nên trực tiếp loại bỏ các đối tượng tích lũy trong cửa sổ đo lường, nhưng chúng tôi ghi lại nó vì §5.1/§5.4 báo cáo tương quan gần bằng không giữa mức chiếm dụng heap được lấy mẫu và ρ cho cả hai chế độ — một kết quả DP1 (§6.3) dựa trên. Chúng tôi coi lý giải về giới hạn độ nhạy đo lường (trạng thái continuation ở quy mô quan sát — tối đa 65 waiters đồng thời — có thể chỉ là vài MB so với baseline ≈110–118 MB, được lấy mẫu mỗi giây) là có khả năng hơn là artifact của `System.gc()`, nhưng không thể loại trừ hoàn toàn tương tác từ dữ liệu thu thập ở đây.
6. **Độ trễ mô phỏng.** Tải công việc sử dụng `WAITFOR DELAY '00:00:00.050'` để tạo nghẽn được kiểm soát; đây là tải công việc phòng thí nghiệm, không phải trace sản xuất. Các pattern I/O không đồng nhất hoặc hỗn hợp là một hạn chế rõ ràng.
7. **HikariCP và JVM version.** Ranh giới ρ* được hiệu chỉnh cho HikariCP với cấu hình cụ thể (§4.1) và OpenJDK 21.0.2; bộ lập lịch Loom phát triển qua các phiên bản JDK và có thể dịch chuyển ranh giới hoạt động.

### 6.5 Các Đe dọa đối với Tính Hiệu lực

**Tính hiệu lực bên trong.** Khởi động 10 giây giảm bớt nhiễu JIT và ổn định bể kết nối, nhưng các tạm dừng GC không được cô lập rõ ràng — các phép đo VT ở ρ cao có thể chứa các đột biến do GC không bị bóc tách. Lặp lại n = 20 (tăng từ n = 5 ở phiên bản thử nghiệm) thu hẹp CI ở mức tải thấp/vừa; gần bão hòa, phương sai vẫn cao cho cả hai chế độ và cell PT ρ = 0.75 cho thấy một hiệu ứng host- hoặc JVM-level nhất thời vẫn có thể chi phối một cell riêng lẻ ngay cả ở n = 20. Thứ tự chạy ngẫu nhiên (seed = 42) giảm thiểu drift hệ thống. Khoảng khởi động 10 giây ngắn hơn một số convention benchmark JVM; các hiệu ứng JIT và ổn định pool không thể loại trừ hoàn toàn.

**Tính hiệu lực bên ngoài.** Kết quả từ một cấu hình máy đơn (Intel i7-12th Gen, 32 GB, Windows 11). Các môi trường container hoặc cloud với CPU/memory chia sẻ có thể cho thấy ranh giới ρ* khác nhau do tương tác với bộ lập lịch OS. Cấu hình bộ lập lịch Linux có thể tạo ra các tradeoff khác nhau do sự tương tác giữa bộ lập lịch JVM Loom và bộ lập lịch nhân — đây là một mối đe dọa rõ ràng với khả năng chuyển giao của ρ* sang các môi trường triển khai khác nhau.

**Tính hiệu lực khái niệm.** Erlang-C giả định trạng thái ổn định; benchmark thực thi trong cửa sổ đo lường 45 giây mỗi cell. Các hiệu ứng nhất thời tại các bước chuyển tải được loại trừ bởi cửa sổ khởi động, nhưng cửa sổ 45 giây có thể không đạt trạng thái ổn định đầy đủ tại ρ ≈ 0.95 nơi thời gian drain dài nhất.

**Kích thước mẫu và phân tích công suất.** Cỡ mẫu n = 20 per cell được xác định trước khi thực nghiệm, dựa trên phân tích công suất nhắm đến 80% power để phát hiện d = 0.8 tại α = 0.05; không áp dụng stopping rule. Không có n nào được thêm vào sau khi xem xét kết quả.

### 6.6 Mối quan hệ với các Nghiên cứu Trước
Khác với các nghiên cứu so sánh thông lượng đơn thuần [1]–[7], **theo hiểu biết của chúng tôi**, bài báo này chính thức hóa ranh giới tính hiệu lực định lượng của mô hình dự đoán và cung cấp lăng kính tiền tệ tài nguyên để giải thích cơ chế phân kỳ — tương tự ở §2.1 chưa kết hợp cả (a) chính thức hóa sự chuyển đổi tiền tệ tài nguyên và (b) xác định các ranh giới định lượng nơi mô hình có và không có giá trị. Bài báo này định vị mình là bổ sung cho chứ không thay thế tài liệu thực nghiệm hiện có.

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

## Lời cảm ơn (Acknowledgements)

*(Sẽ bổ sung: truy cập phần cứng/phòng thí nghiệm, những người phản biện ẩn danh.)*

---

## Tài liệu Tham khảo (References)

[1] D. Charlak, J. Brzeziński, and G. Kozieł, "Comparative analysis of reactive programming and Java virtual threads," *Journal of Computer Sciences Institute*, vol. 39, pp. 154–160, 2026.

[2] L. Lašić, D. Beronić, B. Mihaljević, and A. Radovan, "Assessing the efficiency of Java virtual threads in database-driven server applications," in *Proc. 2024 47th MIPRO ICT and Electronics Convention*, 2024.

[3] P. Pufek, D. Beronić, B. Mihaljević, and A. Radovan, "Achieving efficient structured concurrency through lightweight fibers in Java Virtual Machine," in *Proc. 2020 43rd Int. Convention on Information, Communication and Electronic Technology (MIPRO)*, 2020, pp. 1629–1634. doi: 10.23919/MIPRO48935.2020.9245253.

[4] D. Beronić, N. Novosel, B. Mihaljević, and A. Radovan, "On analyzing virtual threads — a structured concurrency model for scalable applications on the JVM," in *MIPRO Conference*, 2024.

[5] Z. Hu, "Platform threads versus virtual threads in SPECjbb2015: An empirical study on OpenJDK 25," Uppsala University: Disciplinary Domain of Science and Technology, 2026.

[6] S. Ponnampalam, "Evaluation of Virtual Threads and RxJava: A performance and code complexity analysis in a real-time clearing system," Degree Project in Computing Science and Engineering, Uppsala University, 2025.

[7] M. Šimatović, H. Markulin, D. Beronić, and B. Mihaljević, "Evaluating memory management and garbage collection algorithms with virtual threads in high-concurrency Java applications," in *48th ICT and Electronics Convention MIPRO 2025*, 2025.

[8] B. Pereira and F. M. Carvalho, "Enabling progressive server-side rendering for traditional web template engines with Java virtual threads," *Software*, vol. 4, no. 3, p. 20, 2025. doi: 10.3390/software4030020.

[9] T. Cherny-Shahar and A. Yehudai, "MetaFFI-Multilingual indirect interoperability system," *Software*, vol. 4, no. 3, p. 21, 2025. doi: 10.3390/software4030021.

[10] L. Phan, M. Wang, G. Wu, W. Dawei, C. Liqun, and L. Jin, "CrossTrace: Efficient cross-thread and cross-service span correlation in distributed tracing for microservices," arXiv, 2025.

[11] Q. Wu, R. Li, J. Beard, and L. John, "BLQ: Light-weight locality-aware runtime for blocking-less queuing," in *Proc. 33rd ACM SIGPLAN Int. Conf. on Compiler Construction (CC '24)*, 2024. doi: 10.1145/3640537.3641568.

[12] Poutanen et al., "A formal model of an asynchronous, non-blocking edge API gateway pipeline," *Preprints.org*, 2026.

[13] S. Yang et al., "Thread architecture models (TAMs)," in *13th USENIX Symposium on Operating Systems Design and Implementation (OSDI 18)*, 2018.

[14] T. Taipalus, "Database management system performance comparisons: A systematic literature review," *ResearchGate*, 2022.

[15] I. Vershinin and A. R. Mustafina, "Performance analysis of PostgreSQL, MySQL, Microsoft SQL Server," in *Proc. 2021 Int. Russian Automation Conference (RusAutoCon)*, 2021. doi: 10.1109/RusAutoCon52004.2021.9537400.

[16] M. Sagan and M. Plechawska-Wójcik, "Comparative analysis of non-relational databases on the example of Amazon DynamoDB and MongoDB," *Journal of Computer Sciences Institute*, vol. 39, pp. 108–114, 2026.

[17] Z. Wu, J. Liang, J. Fu, M. Wang, and Y. Jiang, "PUPPY: Finding performance degradation bugs in DBMSs via limited-optimization plan construction," in *Proc. 2025 IEEE/ACM 47th Int. Conference*, 2025.

[18] S. Tavakolisomeh, R. Bruno, and P. Ferreira, "BestGC: An automatic GC selector software," *ResearchGate*, 2023.

[19] W. Wnuk and M. Plechawska-Wójcik, "Comparative performance analysis of Express.js and Spring Boot in CRUD-oriented web applications," *Journal of Computer Sciences Institute*, vol. 39, pp. 176–182, 2026. doi: 10.35784/jcsi.9574.

[20] M. Serej, K. Kopciński, and J. Smołka, "Comparative performance analysis of Spring Boot and Ktor for a ticket reservation REST API on the JVM," *Journal of Computer Sciences Institute*, vol. 39, pp. 183–187, 2026.

[21] V. Solohub and V. Pashkevych, "Comparative analysis of query optimization techniques in modern relational database systems," *Journal of Computer Sciences Institute*, vol. 39, pp. 132–137, 2026.

[22] D. C. Ma'soem, "Implementation of event-driven architecture using NATS JetStream for a large-scale online exam answer collection system," *Journal of Business, Social and Technology*, vol. 7, no. 2, pp. 482–495, 2024.

---

## Phụ lục A — Các Hình vẽ Bổ sung (Appendix A — Supplementary Figures)

> *Ba hình vẽ sau đây mang tính bổ sung — chúng cung cấp bằng chứng hỗ trợ nhưng không phải là phương tiện chính cho các khẳng định của bài báo. Chúng có thể được đặt trong một phụ lục trực tuyến nếu giới hạn số trang yêu cầu.*

---

![Hình A1. Phân tích Bland-Altman](analysis/paper/fig5_bland_altman.png)

**Hình A1.** Biểu đồ sự tương đồng Bland–Altman giữa độ trễ trung bình dự đoán Erlang-C so với đo được (tất cả chín điểm hoạt động ρ < 1 ổn định, cả hai mô hình, n = 20 mỗi điểm). Độ lệch trung bình = +40.2 ms, giới hạn tương đồng [−278.5, +358.8 ms] (độ lệch ± 1.96 SD), được thúc đẩy gần như hoàn toàn bởi bất thường PT ρ = 0.75 (§5.2).

---

![Hình A2. So sánh VT so với PT](analysis/paper/fig6_vt_vs_pt.png)

**Hình A2.** So sánh cạnh nhau về độ trễ và quỹ đạo độ dài hàng chờ cho Virtual Threads và Platform Threads qua tất cả chín mức ρ ổn định {0.30, 0.50, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.95}. Các điểm: giá trị trung bình đo được ± 95% CI (n = 20 lần lặp). Đường đứt nét: dự đoán Erlang-C (giống hệt cho cả hai chế độ). Bất thường PT ρ = 0.75 có thể thấy dưới dạng một đỉnh cô lập ở cả hai bảng.

---

![Hình A3. Bản đồ nhiệt tương quan](analysis/paper/fig7_correlation_heatmap.png)

**Hình A3.** Ma trận tương quan Spearman cho các đo lường Nghiên cứu 1 (ArrivalSweepBenchmark), chế độ ổn định (ρ < 1), n = 20 mỗi ô. Bên trái: Platform Threads — Threads_Mean tương quan mạnh với Rho_Target (r = 0.928), xác nhận sự chiếm dụng luồng gốc tỉ lệ với tải. Bên phải: Virtual Threads — Threads_Mean gần như không tương quan với Rho_Target (r = 0.110), trong khi các chỉ số độ trễ và độ dài hàng chờ vẫn tương quan mạnh với mức sử dụng (r ≈ 0.90–0.92), phù hợp với dự đoán tính ổn định bể carrier của trừu tượng hóa tiền tệ tài nguyên. Lưu ý Heap_Mean_MB chỉ tương quan yếu với ρ cho cả hai chế độ (|r| ≤ 0.02), cơ sở cho hướng dẫn giám sát heap đã sửa đổi ở §6.3.

---

*Số lượng từ (phần thân, không bao gồm tài liệu tham khảo và phụ lục): ≈ 11,300 từ (22-07-2026, sau khi thêm bản lặp lại độc lập PostgreSQL chéo DBMS ở §5.2, một đợt rút gọn cắt bớt bản thảo kết hợp khoảng 20%, một đợt tái định vị định hình lại bài báo thành mô hình dự đoán vùng hoạt động đã được xác thực chứ không phải lý thuyết hàng chờ mới, và một đợt hiệu chỉnh biên tập lại phụ đề để làm nổi bật mô hình vùng hoạt động, đổi tên Vùng III từ "Tích tụ continuation" thành "Phân kỳ" để tránh đặt tên vùng theo một giả thuyết chưa chứng minh, hợp nhất bốn tuyên bố miễn trừ trách nhiệm "không phải lý thuyết mới" riêng biệt xuống còn hai tuyên bố tải trọng chính (Tóm tắt, §1.4), và cắt ≈2–3% độ dài phần thân bằng cách xóa nội dung lặp lại trong Kết luận, §6.2, và §6.3). Số trang bài báo mục tiêu: 14–16 trang (định dạng hai cột JSS/SPE); việc cắt giảm thêm 10–15%, nếu muốn, sẽ yêu cầu chuyển Phụ lục A và các Bảng 1B/3B sang tài liệu bổ sung thay vì cắt gọt thêm văn xuôi — phần văn bản thân còn lại không còn trùng lặp rõ ràng.*
