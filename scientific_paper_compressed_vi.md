# Mô hình hóa và Dự đoán Độ trễ trong các Vùng Hoạt động của Luồng Ảo Java (Java Virtual Threads)


**Tóm tắt (Abstract)** — Bài báo này xây dựng và kiểm chứng một mô hình dự đoán về vùng hoạt động mà trong đó Luồng ảo Java (Virtual Threads - VT) và Luồng nền tảng (Platform Threads - PT) tạo ra sự khác biệt rõ rệt về độ trễ. Mô hình áp dụng xấp xỉ Erlang-C / M/M/c tiêu chuẩn — giữ nguyên không sửa đổi — cho cả hai mô hình thực thi, coi các slot của connection pool (hồ chứa kết nối) là các kênh phục vụ cho cả hai. *Resource currency* (tiền tệ tài nguyên) — mức độ chiếm giữ luồng native đối với PT so với trạng thái continuation trú đóng trên heap đối với VT — là từ vựng diễn giải cho lý do tại sao một bộ tham số hóa duy nhất lại phù hợp với cả hai mô hình bên dưới điểm gãy (breakpoint) và phân kỳ bên trên nó. Đánh giá thực nghiệm trên một microservice Spring Boot / SQL Server (20 lần lặp lại cho mỗi điểm hoạt động, lưới 10 mức ρ) xác định điểm gãy tại ρ* ≈ 0.75 (VT) và ρ* ≈ 0.70 (PT). Bên dưới ρ*, mô hình dự đoán độ trễ trong khoảng MAPE ≈ 9–12% và VT/PT không thể phân biệt về mặt thống kê; bên trên ρ*, độ trễ của VT vượt PT +22% tại ρ = 0.85 và +79% tại ρ = 0.95. Một thử nghiệm lặp lại độc lập trên PostgreSQL cho thấy *hình dạng* (shape) của mô hình có tính tổng quát — MAPE ở Vùng I (Region I) trên PostgreSQL thấp hơn một chút (4.7–6.2%) và các điểm gãy tương đương (ρ* ≈ 0.65–0.75) — trong khi *mức độ nghiêm trọng* của sự phân kỳ sau điểm gãy thì không: khoảng cách VT–PT và sự tăng trưởng số lượng Platform Thread lớn hơn một cấp độ quy mô (order of magnitude) trên PostgreSQL. Một môi trường blocking thứ hai (HTTP) bộc lộ chế độ thất bại bão hòa ngược lại: số lượng Platform Thread tăng không giới hạn trong khi VT suy giảm hiệu năng một cách êm đẹp (degrades gracefully). Những phát hiện này hỗ trợ lập kế hoạch năng lực dựa trên vùng hoạt động (regime-aware capacity planning): chiều dài hàng chờ (queue length) và tần suất thu gom rác (GC collection frequency) là các tín hiệu giám sát duy nhất giữ nguyên giá trị thông tin trên cả hai mô hình thực thi và cả hai môi trường (substrates) được nghiên cứu; số lượng luồng và dung lượng chiếm dụng heap thô thì không.

**Từ khóa (Keywords)** — Luồng ảo Java (Java Virtual Threads), Project Loom, lý thuyết hàng chờ (queueing theory), Erlang-C, M/M/c, mô hình hóa hiệu năng (performance modeling), tải làm việc nghẽn (blocking workload), vùng hoạt động (operating regions), lập kế hoạch năng lực (capacity planning), tiền tệ tài nguyên (resource currency).

---

## 1. Giới thiệu (Introduction)

### 1.1 Vấn đề là Chờ đợi, Không phải Tính toán

Các ứng dụng máy chủ hiện đại ngày càng bị hạn chế bởi việc chờ đợi hơn là tính toán. Các microservice bị giới hạn I/O (I/O-bound) dành phần lớn vòng đời của một yêu cầu (request) ở trạng thái nghẽn (blocked) trên các tài nguyên hạ nguồn — trong ngữ cảnh thực nghiệm của bài báo này là một connection pool cơ sở dữ liệu có giới hạn. Sự khác biệt giữa các mô hình luồng không nằm ở việc yêu cầu *có phải* chờ đợi hay không, mà ở việc *hệ thống phải trả giá bằng gì trong khi chúng chờ đợi*. Chúng tôi chính thức hóa sự khác biệt này thông qua khái niệm **Resource Currency (Tiền tệ tài nguyên)**:

> **Định nghĩa 1 (Resource Currency).** *Tiền tệ tài nguyên của việc nghẽn (blocking) là tài nguyên vật lý mà mức độ chiếm dụng của nó tỉ lệ thuận với lượng yêu cầu đang chờ xử lý (waiting inventory) dưới một mô hình thực thi cho trước. Dưới mô hình Platform Threads, tài nguyên này là các slot luồng OS native (mỗi yêu cầu chờ giữ một luồng). Dưới mô hình Virtual Threads, đó là trạng thái continuation trú đóng trên heap (mỗi yêu cầu chờ giữ một continuation đã parked, luồng carrier được giải phóng).*

Dưới mô hình Platform Threads, thread pool đóng vai trò như một giới hạn cứng về độ đồng thời (concurrency cap). Dưới mô hình Virtual Threads (Project Loom, Java 21+), thao tác nghẽn sẽ unmount continuation khỏi luồng carrier của nó, luồng carrier được giải phóng về cho bộ lập lịch (scheduler) trong khi trạng thái chờ được tuần tự hóa thành các đối tượng continuation trú đóng trên heap. *Lượng yêu cầu chờ* (inventory) là hoàn toàn giống nhau giữa các mô hình; nhưng *tiền tệ tài nguyên* để định giá lượng yêu cầu chờ đó thì khác nhau một cách căn bản.

### 1.2 Khoảng trống nghiên cứu và Câu hỏi nghiên cứu

Các nghiên cứu hiện tại báo cáo rằng Luồng ảo giúp cải thiện khả năng mở rộng dưới các tải làm việc nghẽn [1]–[6], nhưng các nghiên cứu này phần lớn vẫn mang tính thực nghiệm. Trong số các tài liệu được tổng quan ở §2, chúng tôi không tìm thấy nghiên cứu nào (a) dự đoán ranh giới của vùng mà VT và PT khác biệt hoặc (b) định lượng nơi mà một phép xấp xỉ hàng chờ cổ điển có hiệu lực và không có hiệu lực cho cả hai mô hình thực thi.

Chúng tôi giải quyết ba giả thuyết có thể bác bỏ (falsifiable hypotheses) được đề xuất **trước khi đo lường**:

- **H1** — Việc nghẽn làm thay đổi hình thái biểu diễn tài nguyên của trạng thái chờ: PT giữ các luồng native tỉ lệ thuận với lượng yêu cầu bị nghẽn; VT park các continuation trong bộ nhớ heap trong khi giữ cho pool luồng carrier ổn định.
- **H2** — Phép xấp xỉ Erlang-C / M/M/c dự đoán độ trễ và chiều dài hàng chờ trong một vùng hoạt động vừa phải (ρ ≤ ρ*), với chất lượng dự đoán suy giảm có hệ thống ở phía trên ρ*.
- **H3** — Sai số dự đoán tăng đơn điệu khi mức độ sử dụng (utilization) vượt qua điểm gãy bão hòa ρ*, phù hợp với sự vi phạm tăng dần các giả định của mô hình.

### 1.3 Các đóng góp

Đóng góp của bài báo này mang tính thực nghiệm và diễn giải, không phải tạo ra toán học hàng chờ mới: Erlang-C được áp dụng giữ nguyên xuyên suốt, và Bổ đề 1 (§6.1) là phát biểu lại trực tiếp của Định luật Little.

1. **Một mô hình dự đoán được kiểm chứng về vùng hoạt động VT/PT, được lặp lại thử nghiệm độc lập trên một cơ sở dữ liệu backend thứ hai.** Quyết định mô hình hóa then chốt — coi các slot connection pool, chứ không phải các luồng carrier, là số lượng server Erlang-C cho *cả hai* mô hình thực thi — cho phép một bộ tham số hóa duy nhất, không sửa đổi dự đoán cho cả hai. Đăng ký trước (pre-registered) trước khi đo lường (§4.2), mô hình xác định 4 vùng hoạt động có thể bác bỏ với ranh giới được tìm ra từ dữ liệu (ρ* ≈ 0.70–0.75 trên MSSQL; ≈0.65–0.75 trên PostgreSQL), đạt MAPE độ trễ ≈9–12% trong vùng không hàng chờ (queue-free region). Thử nghiệm lặp lại tách biệt hai khẳng định: *ranh giới* vùng có tính tổng quát qua các backend; *mức độ nghiêm trọng* của sự phân kỳ phía trên ranh giới thì không.

2. **Khái niệm Resource Currency như một khuôn khổ diễn giải cho lý do *tại sao* mô hình hoạt động và nơi nó bị phá vỡ** — một bộ từ vựng (§1.1, §6.1) cho quan sát rằng lượng chờ nghẽn giống hệt nhau được quy đổi ra các tài nguyên vật lý khác nhau dưới PT (luồng native) và VT (continuation trên heap). Điều này thúc đẩy hướng dẫn giám sát theo vùng hoạt động ở §6.3 và giải thích tại sao chỉ riêng số lượng luồng không phải là tín hiệu năng lực đầy đủ dưới Luồng ảo.

---

![Hình 1. Vùng hoạt động](analysis/paper/fig1_operating_envelope.png)

**Hình 1.** Vùng hoạt động 4 phần. Vùng I (ρ ≤ 0.77) là nơi Erlang-C đáng tin cậy về mặt định lượng và VT/PT không thể phân biệt về độ trễ. Vùng II (0.77–0.90) đánh dấu sự bắt đầu phân kỳ tiền tệ tài nguyên. Vùng III (0.90–0.98) là vùng phân kỳ, nơi mô hình chỉ mang tính định hướng. Vùng IV (ρ ≥ 1) cố ý không ổn định, chỉ dùng làm bằng chứng ranh giới.

## 2. Các nghiên cứu liên quan (Related Work)

### 2.1 Các nghiên cứu thực nghiệm và Mô hình hàng chờ

Sự ra đời của Project Loom trong Java 21 đã kích hoạt một làn sóng nghiên cứu thực nghiệm. Lašić và cộng sự [2] tìm thấy sự cải thiện thông lượng đo đạc được của VT trong các ứng dụng Spring Boot hướng cơ sở dữ liệu mà không có mô hình dự đoán. Beronić và cộng sự [4] khảo sát các mô hình lập trình đồng thời có cấu trúc (structured concurrency), mô tả VT như một giải pháp thay thế có khả năng mở rộng cho các framework reactive. Hu [5] benchmark VT so với PT trong SPECjbb2015, quan sát thấy sự khác biệt phụ thuộc vào tải công việc nhưng không chính thức hóa ranh giới. Šimatović và cộng sự [7] nghiên cứu hành vi GC dưới các tải VT đồng thời cao, ghi nhận áp lực heap ở mức sử dụng cao — phù hợp với trừu tượng hóa tích tụ continuation của chúng tôi. Một điểm chung: các nghiên cứu này *báo cáo* sự khác biệt hiệu năng nhiều hơn là *giải thích* chúng một cách phân tích, và chúng tôi chưa thấy các dự đoán đăng ký trước, có thể bác bỏ hay ranh giới hiệu lực mô hình định lượng trong các tài liệu này.

Mô hình M/M/c và phép xấp xỉ Erlang-C đã được áp dụng cho các cấu hình thread-pool cổ điển [8], connection pooling [9], và tính đồng thời trong cơ sở dữ liệu [10]. Wu và cộng sự [11] giới thiệu hàng chờ không nghẽn (blocking-less queuing - BLQ) như một cách tiếp cận runtime. Khoảng trống: các nghiên cứu hàng chờ trước đây nhắm vào kiến trúc thread-pool cổ điển. Theo hiểu biết của chúng tôi, các mô hình này chưa từng được áp dụng cho hành vi *biến đổi tiền tệ* kiểu Loom của luồng ảo, nơi số lượng kênh phục vụ bị tách rời khỏi lượng chờ nghẽn. Mục tiêu của chúng tôi là chính thức hóa *tại sao* hai mô hình phân kỳ và *nơi* sự phân kỳ đó bắt đầu. Chúng tôi áp dụng khuôn khổ đo lường lặp lại của Taipalus [14] cũng như Vershinin và Mustafina [15] (20 lần lặp cho mỗi điểm hoạt động, thử nghiệm lặp lại độc lập trên cơ sở dữ liệu backend thứ hai).

---

## 3. Cơ sở lý thuyết: Lý thuyết hàng chờ và Erlang-C

### 3.1 Mô hình M/M/c

Chúng tôi mô hình hóa connection pool như một hàng chờ M/M/c với luồng đến Poisson (tốc độ λ), thời gian phục vụ mũ (trung bình 1/μ), và c server (dung lượng connection pool). Tải cung cấp trên mỗi server là ρ = λ / (c · μ); điều kiện ổn định yêu cầu ρ < 1.

### 3.2 Công thức Erlang-C

Xác suất một yêu cầu đến phải chờ đợi được cho bởi công thức Erlang-C C(c, a), trong đó a = λ/μ là lưu lượng cung cấp tính bằng Erlangs. Thời gian chờ trung bình, chiều dài hàng chờ và độ trễ hệ thống như sau:

> W_q = C(c, a) / (c · μ − λ),  L_q = λ · W_q,  W = W_q + 1/μ

### 3.3 Áp dụng cho Luồng ảo

Dưới Luồng ảo, các *kênh phục vụ* (service channels) không phải là luồng carrier mà là các slot của connection pool. Một yêu cầu được lập lịch bởi VT sẽ park continuation của nó trên heap khi pool bị đầy; luồng carrier được giải phóng. Từ góc độ hàng chờ, các slot của pool vẫn là c server; tiến trình đến và phục vụ không thay đổi. Sự biến đổi nằm ở *nền tảng tài nguyên* (resource substrate) của trạng thái chờ, chứ không nằm ở hình học hàng chờ — đó là lý do tại sao một bộ tham số hóa M/M/c lại mô hình hóa được cả hai mô hình thực thi, và tại sao nó phân kỳ phía trên ρ*.

---

## 4. Phương pháp luận (Methodology)

### 4.1 Hệ thống được thử nghiệm

Chúng tôi kiểm thử một microservice Spring Boot 3.2 kết nối với Microsoft SQL Server 2019 thông qua JDBC đồng bộ (HikariCP connection pool). Tải công việc là một truy vấn JPQL theo sau bởi câu lệnh `WAITFOR DELAY '00:00:00.050'` phía server — một **tải nghẽn kiểm soát nhân tạo (synthetic controlled-blocking workload)** trong đó kết nối thực sự bị giữ trong toàn bộ thời gian phục vụ, thỏa mãn định nghĩa server M/M/c (E[S] bằng thời gian chiếm giữ kết nối). Các thử nghiệm chạy trên một máy chủ riêng (Intel Core i7-12th Gen, 32 GB RAM, Windows 11, OpenJDK 21.0.2).

### 4.2 Thiết kế Benchmark

**Nghiên cứu 1 — ArrivalSweepBenchmark (chính).** Thực thi giả định luồng đến Poisson của M/M/c bằng cách tính λ_target = ρ_target · c · μ và giãn cách các yêu cầu bằng khoảng thời gian đến ngẫu nhiên theo phân phối mũ. Connection pool cố định tại c = 50. Mười mức sử dụng: ρ ∈ {0.30, 0.50, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.95, 1.05} (ρ = 1.05 chỉ bao gồm làm bằng chứng ranh giới Vùng IV). Hai chế độ thực thi (VT, PT) × **20 lần lặp lại** cho mỗi ô (ρ, mode), mỗi ô gồm 10 giây khởi động (warmup) + 45 giây đo lường, thứ tự chạy ngẫu nhiên. Chiều dài hàng chờ E[N_q] được đo trực tiếp từ `HikariPoolMXBean.getThreadsAwaitingConnection()` và được đối chiếu với ước tính theo Định luật Little. Chạy trên cả MSSQL (chính) và PostgreSQL (§5.2) với cấu hình giống hệt.

**Nghiên cứu 2 — PoolSweepValidationBenchmark (phụ).** Quét kích thước connection pool dưới dạng luồng đến theo bùng nổ (burst-arrival) để ước tính tham số chi phí heap α trên mỗi continuation xếp hàng. Vì luồng đến bùng nổ vi phạm giả định Poisson, nghiên cứu này mang tính bổ trợ; chỉ áp dụng cho Luồng ảo. Kết quả được trích dẫn ở §5.1 khi lưu ý.

**Nghiên cứu 3 — Môi trường HTTP.** HttpSleepBenchmark thay thế JDBC bằng HTTP I/O thông qua Java `HttpClient` và một `Semaphore(50)` đóng vai trò proxy cho pool, chạy tại n = 20 trên cả hai backend. Kết quả ở §5.4.

**Đăng ký trước (Pre-registration).** Các dự đoán Erlang-C cho tất cả các điểm hoạt động được ghi ra tệp CSV trước khi bất kỳ ô đo lường nào được thực hiện.

### 4.3 Các chỉ số (Metrics)

* **Độ trễ trung bình** (ms) và **Độ trễ P99** (ms): thời gian phản hồi yêu cầu đầu-cuối (end-to-end) trong cửa sổ đo lường.
* **Chiều dài hàng chờ** (*N*_q): số lượng kết nối đang chờ trung bình của HikariCP từ `HikariPoolMXBean`.
* **Bộ nhớ Heap** (MB) và **Số lượng luồng**: giá trị JVM trung bình lấy mẫu ở khoảng thời gian 1 giây.
* **Thời gian dừng GC** (ms) và **Số lần thu gom GC**: chênh lệch (delta) mỗi lần lặp từ `GarbageCollectorMXBean`.
* **Tốc độ tăng trưởng heap ròng** (MB/s): sự tích tụ heap ròng trong cửa sổ đo lường — một chỉ số đại diện thô cho sự tích tụ đối tượng continuation, không phải đo lường thể tích cấp phát thực sự (§6.4).
* **Dự đoán Erlang-C**: W và L_q từ §3.2, được đóng băng trước khi đo lường.

### 4.4 Phân tích thống kê

Sai số dự đoán: MAE, RMSE, và MAPE trên chín điểm hoạt động ρ < 1. Phần dư vi phạm kiểm định chuẩn Shapiro–Wilk (p < 0.05) đối với cả 4 so sánh độ trễ/hàng chờ, do đó kiểm định **Wilcoxon signed-rank** được sử dụng xuyên suốt (n = 9 ô cho mỗi chế độ). **Cohen's *d*** và **Cliff's δ** định lượng ý nghĩa thực tiễn giữa VT–PT cho mỗi mức ρ (n = 20 mỗi chế độ). Việc phát hiện điểm gãy sử dụng hồi quy từng đoạn (piecewise regression) để tối thiểu hóa tổng bình phương phần dư; khoảng tin cậy (CI) 95% bootstrap sử dụng 2.000 mẫu lại. Tất cả CI 95% sử dụng giá trị tới hạn Student-t (df = n − 1, n = 20).

### 4.5 Các giả định của mô hình

Phép xấp xỉ M/M/c giả định: (A1) Luồng đến Poisson — hệ số biến thiên (CV) khoảng thời gian đến đo được vượt quá 1 trên toàn bộ lưới (≈1.5 tại ρ = 0.30 tăng lên ≈2.9 gần bão hòa), vì vậy A1 bị áp lực xuyên suốt; (A2) thời gian phục vụ mũ — xấp xỉ thỏa mãn ở Vùng I, dịch chuyển gần điểm bão hòa; (A3) không ghim luồng carrier (no carrier-thread pinning) — được đảm bảo bởi thiết kế tải công việc; (A4) nguồn nghẽn đồng nhất đơn lẻ — được đảm bảo; (A5) luồng đến mạch mở (open-loop arrival) — được đảm bảo. A1 và A2 là các nguyên nhân chính cho sự suy giảm của mô hình phía trên ρ* (§6.2).

---

## 5. Kết quả (Results)

### 5.1 RQ1 — Mức tiêu thụ tài nguyên phù hợp với Sự biến đổi Tiền tệ Tài nguyên Nghẽn

Dưới mức sử dụng thấp (ρ ≤ 0.50), cả hai mô hình cho độ trễ gần như giống hệt nhau: PT tại ρ = 0.30 đạt 56.3 ms (95% CI ±0.257 ms); VT đạt 56.4 ms (±0.168 ms). Khi mức sử dụng tăng lên, cùng một lượng hàng chờ sẽ hiện hữu dưới hai nền tảng vật lý riêng biệt:

* **Platform Threads**: số lượng luồng tăng đơn điệu theo mức sử dụng (Spearman r = 0.928, p < 0.0001), tăng từ 147.3 tại ρ = 0.30 lên 199.4 tại ρ = 0.95 trên MSSQL. Sự tăng trưởng có giới hạn này phụ thuộc vào backend: cùng benchmark trên PostgreSQL cho thấy số lượng Platform Thread tăng lên hàng nghìn gần điểm bão hòa (§5.2).
* **Virtual Threads**: pool luồng carrier về cơ bản giữ nguyên không đổi trên tất cả các mức ρ (r = 0.110, p = 0.14 — không thể phân biệt với 0), duy trì trong khoảng 138.0–138.2, trong khi chiều dài hàng chờ tăng từ 0.02 yêu cầu tại ρ = 0.30 lên 65.16 tại ρ = 0.95.

Trong Nghiên cứu 1, dung lượng heap lấy mẫu chỉ tương quan yếu với ρ đối với **cả hai** chế độ (Spearman r ≈ 0), trong khi tần suất thu gom GC tương quan mạnh (r = 0.99 với VT, 0.90 với PT). Sự biệt về tiền tệ tài nguyên thể hiện ở **số lượng luồng** và **chiều dài hàng chờ**, chứ không nằm ở dung lượng chiếm dụng heap tại một điểm thời điểm.

**Kết luận:** Phù hợp với H1. Số lượng luồng là một tín hiệu năng lực gây hiểu lầm dưới VT; dung lượng heap thô cũng không đáng tin cậy dưới bất kỳ chế độ nào. Chiều dài hàng chờ và tần suất GC là các chỉ số theo dõi được tải hệ thống.

---

![Hình 8. Tái phân bổ tài nguyên](analysis/paper/fig8_resource_reallocation.png)

**Hình 8.** Tái phân bổ tài nguyên dưới mức sử dụng tăng dần (Nghiên cứu 1, MSSQL, n = 20 mỗi ô). Platform Threads (đỏ) di chuyển lên trên và sang phải theo mức sử dụng (r = 0.928). Virtual Threads (xanh) di chuyển gần như theo chiều dọc — số luồng carrier giữ gần mức 138 xuyên suốt (r = 0.110, không có ý nghĩa thống kê) trong khi chiều dài hàng chờ leo dốc hai cấp độ quy mô.

### 5.2 RQ2 — Phép xấp xỉ dự đoán tốt trong một Vùng

Bảng 1 trình bày phân tích mức độ phù hợp đầy đủ trên chín mức ρ ổn định.

**Bảng 1. Chỉ số kiểm chứng — Erlang-C so với Giá trị đo thực tế (n = 20 lần lặp mỗi ô, MSSQL)**

| Chỉ số | n | MAE | RMSE | MAPE (%) | R² | r |
| ------------------------- | -- | ---- | ----- | --------- | ------- | ------ |
| Độ trễ trung bình — VT (ms) | 9 | 14.7 | 27.0 | 19.5 | 0.264 | 0.991 |
| Độ trễ trung bình — PT (ms) | 9 | 80.3 | 229.0 | 120.6 | −0.124 | −0.094 |
| Độ trễ trung bình — Tất cả (ms) | 18 | 47.5 | 163.0 | 70.0 | −0.063 | 0.034 |
| Chiều dài hàng chờ — VT (req) | 9 | 7.1 | 18.0 | 357.2 | 0.210 | 0.998 |
| Chiều dài hàng chờ — PT (req) | 9 | 27.0 | 77.3 | 39 932.4 | −0.148 | −0.055 |

*Bootstrap 95% CI (độ trễ, tất cả chế độ, 2.000 mẫu lại): MAPE = 70.0% [7.2–189.1%]; RMSE = 163.0 ms [5.8–281.6 ms].*

---

![Hình 2. Độ trễ so với rho](analysis/paper/fig2_latency_vs_rho.png)

**Hình 2.** Độ trễ trung bình so với mức sử dụng mục tiêu ρ đè lên dự đoán Erlang-C. Thanh sai số: 95% CI trên 20 lần lặp.

---

![Hình 3. Hiệu chuẩn](analysis/paper/fig3_calibration.png)

**Hình 3.** Biểu đồ hiệu chuẩn: Erlang-C dự đoán so với độ trễ trung bình đo thực tế (a) và chiều dài hàng chờ (b) cho tất cả cặp (ρ, mode). Các điểm nằm trên đường chéo 1:1 thể hiện mô hình dự đoán thấp hơn thực tế.

Thống kê tổng hợp của PT bị chi phối bởi một ô bất thường đơn lẻ: tại ρ = 0.75, PT ghi nhận 753.2 ms với 95% CI ±1417.3 ms (CV ≈ 402%), cao hơn khoảng một cấp độ quy mô so với các ô lân cận (62.9 ms tại ρ = 0.70, 65.7 ms tại ρ = 0.80). Chúng tôi coi đây là bất thường đo lường — không tái hiện ở bất kỳ ô ρ lân cận nào, không có đỉnh nhọn tương tự cho VT ở cùng ρ. Loại bỏ ô này, MAPE của PT trên 8 điểm còn lại giảm từ 120.6% xuống 7.0%. Trong Vùng I (ρ ≤ 0.70), sai số độ trễ tương đối là 12.0% (VT) và 9.1% (PT).

**Kiểm định độ chệch thống kê** (Bảng 2): độ chệch độ trễ không có ý nghĩa thống kê cho cả hai chế độ; độ chệch chiều dài hàng chờ **có** ý nghĩa thống kê cho cả hai (W = 0.0, p = 0.0078), với mô hình nhất quán dự đoán chiều dài hàng chờ thấp hơn thực tế.

**Bảng 2. Ý nghĩa thống kê của Sai số Dự đoán (Wilcoxon signed-rank, n = 9 ô mỗi chế độ)**

| So sánh | Thống kê (Statistic) | *p* | Có ý nghĩa thống kê |
| ---------- | --------- | ------ | ----------- |
| Độ trễ VT | 17.00 | 0.5703 | Không |
| Độ trễ PT | 18.00 | 0.6523 | Không |
| Hàng chờ VT | 0.00 | 0.0078 | **Có** |
| Hàng chờ PT | 0.00 | 0.0078 | **Có** |

**Kiểm tra chéo DBMS (PostgreSQL).** Nghiên cứu 1 được chạy lại trên PostgreSQL với cấu hình giống hệt. Thu hẹp trong Vùng I (ρ ≤ 0.70), sai số độ trễ tuyệt đối trung bình là 5.1% (VT) và 6.2% (PT) — tốt hơn một chút so với 9.1–12.0% của MSSQL. Vùng hoạt động không hàng chờ có tính tổng quát; mức độ phân kỳ phía trên điểm gãy thì không. Tại ρ = 0.95, độ trễ trung bình đo được trên PostgreSQL đạt 3192.4 ms (VT) và 1977.1 ms (PT) — gấp khoảng 20× và 22× giá trị trên MSSQL. Số lượng Platform Thread tăng lên 3707.9 trên PostgreSQL so với 199.4 trên MSSQL — sự tăng trưởng không giới hạn mang tính cấu trúc tương tự hành vi HTTP ở §5.4. Số lượng carrier VT giữ phẳng trên cả hai backend (138.0–138.2). Việc phát hiện điểm gãy trên PostgreSQL xác định ρ* ≈ 0.75 cho VT và ≈ 0.65 cho PT. Kích thước tác động đầy đủ cho mỗi ρ nằm ở Bảng bổ trợ S1.

**Kết luận:** Kết quả hỗ trợ H2 trong Vùng I. Phép xấp xỉ có ích về mặt định lượng phía dưới các điểm gãy theo chế độ (MAPE ≈ 9–12% trên MSSQL) và có ích về mặt định hướng phía trên chúng. Chiều dài hàng chờ bị dự đoán thấp hơn một cách nhất quán ngay cả ở Vùng I, do đó ước tính chiều dài hàng chờ nên kèm theo một biên độ an toàn.

### 5.3 RQ3 — Nơi mô hình dừng hoạt động

**Bước 1 — Phát hiện điểm gãy.** Hồi quy từng đoạn xác định điểm bắt đầu của sự lệch có hệ thống tại **ρ* ≈ 0.75 đối với Luồng ảo** (bootstrap 95% CI [0.75, 0.80], SSR cải thiện 94.5%) và **ρ* ≈ 0.70 đối với Luồng nền tảng** (CI [0.50, 0.80], SSR cải thiện 44.9%). CI rộng của PT bị chi phối bởi bất thường tại ρ = 0.75. Các ranh giới được tìm ra từ dữ liệu, không phải do tác giả tự chọn.

---

![Hình 4. Phân tích phần dư](analysis/paper/fig4_residual.png)

**Hình 4.** Sai số dự đoán độ trễ tương đối (%) so với ρ cho VT và PT, với dải khoảng tin cậy 95% bootstrap xung quanh điểm gãy của mỗi chế độ.

**Bước 2 — Hướng của phần dư.** VT: mô hình *ước tính cao hơn* độ trễ đo được từ ρ = 0.30 đến ρ = 0.80 (sai số có dấu −14.9% thu hẹp về −0.9%), chuyển sang *ước tính thấp hơn* tại ρ = 0.85 (+20.9%) và tăng mạnh tại ρ = 0.95 (+94.1%). PT: sai số duy trì trong khoảng ±5.2% từ ρ = 0.60 đến ρ = 0.85 (loại trừ bất thường ρ = 0.75). Sự đổi dấu của VT phù hợp với sự tích tụ continuation: Erlang-C không tính đến thời gian chờ bổ sung từ trạng thái continuation trú đóng trên heap một khi việc xếp hàng trở nên đáng kể.

**Bước 3 — Kích thước tác động (Effect sizes)** (Bảng 3).

**Bảng 3. Độ trễ trung bình VT vs. PT và Kích thước Tác động theo Mức sử dụng (n = 20 mỗi ô, MSSQL)**

| ρ | VT trung bình (ms) | PT trung bình (ms) | Cohen's *d* | Cliff's δ | Mức độ |
| -------- | ------------- | ------------- | ----------: | --------: | --------------- |
| 0.30 | 56.4 | 56.3 | 0.254 | 0.228 | nhỏ (small) |
| 0.50 | 58.0 | 58.7 | −0.303 | −0.142 | không đáng kể (negligible) |
| 0.60 | 60.3 | 69.3 | −0.316 | −0.185 | nhỏ (small) |
| 0.65 | 60.4 | 63.3 | −0.545 | −0.440 | trung bình (medium) |
| 0.70 | 61.0 | 62.9 | −1.200 | −0.620 | lớn (large) |
| **0.75** | 62.5 | **753.2** | −0.323 | −0.547 | lớn (ngoại lệ) |
| 0.80 | 66.2 | 65.7 | 0.159 | −0.130 | không đáng kể (negligible) |
| **0.85** | **82.2** | 67.2 | 1.332 | 0.720 | lớn (large) |
| **0.95** | **161.0** | 90.1 | 1.111 | 0.790 | lớn (large) |

*Gộp (tất cả ρ < 1, n = 180 mỗi chế độ): Cohen's d = −0.096, Cliff's δ = −0.062 (không đáng kể) — các tác động trái dấu ở mỗi mức ρ triệt tiêu lẫn nhau trong thống kê gộp.*

Phía trên ρ*, VT trở thành chế độ có độ trễ cao hơn: Δ = 14.9 ms (+22%) tại ρ = 0.85 và Δ = 70.9 ms (+79%) tại ρ = 0.95.

**Bước 4 — Vùng hoạt động (Operating envelope).**

| Vùng | Khoảng ρ | Đặc điểm |
| ------------------------------------ | ------------------- | --------------------------------------------------------------------------------------------------- |
| **I — Không hàng chờ / mô hình có hiệu lực** | ρ ≤ 0.70–0.75 | MAPE 9–12%; VT và PT không thể phân biệt (chênh lệch tuyệt đối ≤ 3 ms) |
| **II — Chuyển tiếp** | 0.70/0.75 < ρ ≤ 0.90 | Bắt đầu phân kỳ VT–PT; mô hình chỉ mang tính định hướng |
| **III — Phân kỳ** | 0.90 < ρ ≤ 0.98 | Mức phạt độ trễ VT lớn (lên tới +79% tại ρ = 0.95); mô hình dự đoán thấp hơn cả độ trễ lẫn chiều dài hàng chờ |
| **IV — Bão hòa** | ρ ≥ 1 | Cố ý không ổn định; VT 462.5 ms vs. PT 136.8 ms tại ρ = 1.05 (JDBC; ngược lại dưới HTTP) |

**Kết luận:** Kết quả hỗ trợ H3. Hiệu lực mô hình được mô tả tốt hơn như hai điểm gãy riêng biệt theo chế độ nhưng gần nhau (VT ρ* ≈ 0.75, PT ρ* ≈ 0.70) thay vì một giá trị gộp đơn lẻ.

### 5.4 Bằng chứng GC và Khả năng chuyển giao sang tải HTTP

**Bằng chứng GC (Nghiên cứu 1).** Số lượng sự kiện GC tăng đơn điệu cho cả hai chế độ: VT 6.2 → 24.6 (3.96×) và PT 7.4 → 21.9 (2.96×) từ ρ = 0.30 đến ρ = 1.05. Thời gian dừng GC cũng theo mẫu tương tự (VT 6.8×, PT 4.6×), với VT tăng dốc hơn. Tốc độ tăng trưởng heap ròng về cơ bản là phẳng cho cả hai chế độ (3.5–3.7 MB/s), do đó sự phân kỳ GC bị chi phối bởi *tần suất* thu gom chứ không phải tích tụ heap ròng. Dữ liệu GC đầy đủ theo (mode, ρ) nằm ở Bảng bổ trợ S2.

**Tải HTTP (Nghiên cứu 3).** Từ ρ = 0.30 đến ρ = 0.85, VT và PT rất gần nhau dưới môi trường HTTP (VT 62–64 ms, PT 61–65 ms). Tại điểm không ổn định ρ = 1.05, hai chế độ phân kỳ mạnh và theo **hướng ngược lại** với Nghiên cứu 1: **độ trễ trung bình của VT là 1084.5 ms; PT là 1975.5 ms (+82% so với VT)**. Số lượng luồng trung bình của PT bùng nổ lên 1737.3 (tối đa 10.115) với 369 yêu cầu bị từ chối; số luồng carrier của VT duy trì ở mức 132.3 và không có yêu cầu nào bị từ chối. PostgreSQL tại ρ = 1.05 tái hiện cùng hướng này (PT 2277.6 ms vs. VT 1902.0 ms).

**Khả năng chuyển giao.** Sự không thể phân biệt ở tải thấp-đến-vừa (Vùng I) là nhất quán trên cả hai môi trường và backend. Tại điểm bão hòa, hai môi trường bất đồng về *chế độ nào thất bại nghiêm trọng hơn*: VT dưới JDBC (pool có giới hạn dùng chung cho cả hai chế độ), PT dưới HTTP (không có giới hạn tương đương đối với sự tăng trưởng luồng native của PT). Tài nguyên nào bị giới hạn là đặc tính của cấu hình driver/pool/server cụ thể, chứ không chỉ riêng mô hình thực thi.

---

## 6. Thảo luận (Discussion)

### 6.1 Tại sao mô hình hoạt động phía dưới ρ*?

Cơ chế ở đây là Định luật Little: E[N] = λ · W, đúng bất kể mô hình thực thi nào. Việc park một continuation và block một luồng là cùng một sự kiện *hàng chờ* — cả hai đều loại bỏ một đơn vị năng lực phục vụ khỏi pool và thêm một đơn vị vào lượng hàng chờ E[N]. Phía dưới ρ*, việc lựa chọn loại tiền tệ tài nguyên là vô hình đối với độ trễ, đó là lý do tại sao một bộ tham số Erlang-C lại phù hợp với cả hai mô hình thực thi.

> **Bổ đề 1 (Resource Currency Invariance).** *Sự thể hiện vật lý của N phụ thuộc vào mô hình — PT: sự chiếm giữ luồng native; VT: trạng thái continuation trú đóng trên heap — nhưng bản thân thời gian chờ W không phụ thuộc vào tiền tệ tài nguyên.*

> **Hệ quả:** Bên dưới ρ*, VT và PT không thể phân biệt về độ trễ. Trong Vùng I, việc chọn VT hay PT là **một quyết định thiết kế nên dựa trên các lý do phi hiệu năng** — dung lượng bộ nhớ, hạn chế vận hành, độ phức tạp của mã nguồn — vì ảnh hưởng lên độ trễ là không đáng kể. Các điểm gãy theo chế độ và các vùng hoạt động được tạo ra ở đây dựa trên việc so sánh các phép đo với một mô hình dự đoán, giúp phân biệt phân tích này với một so sánh benchmark đơn thuần (§2).

### 6.2 Tại sao dự đoán suy giảm phía trên ρ*?

Sự suy giảm phù hợp với việc vi phạm các giả định A1 và A2 (§4.5):

1. **Luồng đến bùng nổ (Vi phạm A1).** Hệ số biến thiên CV của thời gian giữa các lần đến > 1 trên toàn bộ lưới, tăng mạnh theo tải. Erlang-C ước tính thấp hơn thời gian chờ đối với luồng đến super-Poisson, phù hợp với sai số ước tính thấp tăng dần theo ρ.

2. **Dịch chuyển phân phối thời gian phục vụ (A2 bị áp lực).** Ở mức xếp hàng cao, thời gian lấy kết nối từ HikariCP trở thành một thành phần tăng dần trong thời gian phục vụ; phân phối kết hợp không còn được xấp xỉ tốt bởi phân phối mũ đơn tham số.

3. **Đặc thụ VT: chi phí continuation (bằng chứng yếu hơn).** Số lượng sự kiện GC và thời gian dừng GC tăng theo ρ cho cả VT (r = 0.99) và PT (r = 0.90), VT tăng dốc hơn một chút — hỗ trợ có điều kiện cho cơ chế đặc thụ VT, vì riêng áp lực GC không tách biệt hoàn toàn hai chế độ. Việc quy kết nguyên nhân từ sự tích tụ continuation dẫn đến khoảng cách độ trễ đặc thụ VT phía trên ρ* vẫn là một giả thuyết; cần phân tích cấp phát theo lớp (per-class allocation profiling) để xác nhận (§6.4).

### 6.3 Ý nghĩa thực tiễn

**DP1: Giám sát chiều dài hàng chờ và tần suất GC, không giám sát số lượng luồng hay dung lượng heap thô.** Số lượng luồng là một tín hiệu năng lực gây hiểu lầm dưới VT — nó giữ phẳng ngay cả khi chiều dài hàng chờ tăng hai cấp độ quy mô. Dấu hiệu cảnh báo đặc thụ của VT là *sự kết hợp* giữa chiều dài hàng chờ tăng với số lượng luồng giữ phẳng.

**DP2: Chỉ áp dụng phép xấp xỉ hàng chờ trong vùng hoạt động có hiệu lực.** Dự đoán Erlang-C đáng tin cậy về mặt định lượng ở Vùng I (ρ ≤ 0.70 cho PT, ≤ 0.75 cho VT). Ở Vùng II, coi đầu ra mô hình chỉ mang tính định hướng. Ở Vùng III, chỉ sử dụng như một cảnh báo bão hòa — và lưu ý rằng mô hình dự đoán thấp hơn chiều dài hàng chờ ngay cả ở Vùng I, do đó ước tính hàng chờ nên có biên độ an toàn.

**DP3: Lập kế hoạch năng lực cần nhận thức được vùng hoạt động, môi trường và backend.** Ranh giới ρ* ≈ 0.65–0.75 tương đối nhất quán trên các cấu hình JDBC và môi trường được nghiên cứu. Điều *không* nhất quán là chế độ thất bại bão hòa: dưới MSSQL/JDBC, VT tích tụ nhiều độ trễ hơn trong khi số luồng của PT bị giới hạn; dưới PostgreSQL/JDBC, số luồng của PT tăng không giới hạn; dưới HTTP, PT bùng nổ trong khi VT suy giảm êm đẹp. Hãy xác định tài nguyên nào thực sự bị giới hạn trong triển khai cụ thể của bạn và xác nhận bằng thực nghiệm trên từng DBMS/driver/framework.

**Bảng 4. Danh mục Tín hiệu Giám sát theo Vùng Hoạt động**

| Tín hiệu | Vùng I (ρ ≤ 0.70–0.75) | Vùng II (lên tới ρ ≈ 0.90) | Vùng III (ρ > 0.90) |
| ---------------------- | ------------------------- | --------------------------- | -------------------------- |
| Số lượng luồng carrier | ✓ PT; ✗ gây hiểu lầm cho VT | ✗ (VT); ✓ (PT, cho đến giới hạn môi trường) | ✗ (VT); ⚠ cảnh báo bùng nổ (PT, HTTP) |
| Chiều dài hàng chờ request | ✓ Đáng tin cậy (cả hai) | ✓ Đáng tin cậy (cả hai) | ✓ **Tín hiệu chính** (cả hai) |
| Chiếm dụng Heap (MB) | Không phát hiện tín hiệu (cả hai) | Không phát hiện tín hiệu | Không dựa vào riêng chỉ số này |
| Tần suất thu gom GC | Tùy chọn (theo dõi ρ, cả hai) | ✓ Theo dõi (cả hai) | ✓ Theo dõi (cả hai) |
| Đầu ra mô hình Erlang-C | ✓ Độ trễ; hàng chờ bị đoán thấp | ⚠ Định hướng | ⚠ Cảnh báo bão hòa |

### 6.4 Hạn chế của nghiên cứu

- **Hai DBMS backend — bất đồng về mức độ nghiêm trọng.** Vùng không hàng chờ có tính tổng quát tốt (MAPE ≈5–6% trên PostgreSQL vs. ≈9–12% trên MSSQL), nhưng sự phân kỳ sau điểm gãy lớn hơn một cấp độ quy mô trên PostgreSQL và số lượng Platform Thread (bị giới hạn trên MSSQL) lại tăng gần như không giới hạn trên PostgreSQL. Hãy coi *sự tồn tại* và *vị trí* của vùng hoạt động là tương đối linh hoạt; coi các khoảng cách cụ thể và tài nguyên nào giới hạn Platform Threads là phụ thuộc cấu hình. MySQL và các kho lưu trữ phi quan hệ chưa được kiểm thử.
- **Dạng nghẽn đơn lẻ trong Nghiên cứu 1–2.** Nghiên cứu 3 bổ sung HTTP làm môi trường thứ hai, tìm thấy chế độ thất bại bão hòa phụ thuộc vào môi trường. Khả năng tổng quát hóa cho Redis, Kafka, gRPC hoặc các mô hình nghẽn hỗn hợp chưa được kiểm thử.
- **Kích thước mẫu.** 20 lần lặp cho mỗi ô giúp thu hẹp CI ở mức sử dụng thấp-đến-vừa, nhưng biến động vẫn cao gần điểm bão hòa và CI điểm gãy của PT ([0.50, 0.80]) bị rộng do bất thường tại ρ = 0.75.
- **Đo lường tiền tệ tài nguyên gián tiếp.** Chiều dài hàng chờ được đo trực tiếp qua `HikariPoolMXBean` và kiểm tra chéo với ước tính Định luật Little (khớp trong khoảng ~4% ở ρ ổn định cao nhất). Cả hai đều không đếm trực tiếp các đối tượng continuation trú đóng trên heap. Việc profiling heap theo lớp sẽ giải quyết được sự mơ hồ này.
- **Loại trừ hiện tượng ghim Luồng ảo (A3).** Các tải công việc kích hoạt ghim luồng carrier (`synchronized` cũ, JNI, `Object.wait()`) làm vô hiệu trừu tượng hóa biến đổi tiền tệ và nằm ngoài phạm vi nghiên cứu.
- **Phiên bản JVM.** Kết quả được hiệu chuẩn cho OpenJDK 21.0.2; các phiên bản bộ lập lịch Loom trong tương lai có thể làm dịch chuyển ranh giới vùng hoạt động.

### 6.5 Đe dọa đối với tính hiệu lực (Threats to Validity)

- **Hiệu lực nội tại.** 10 giây khởi động giúp giảm thiểu hiệu ứng làm nóng JVM; thứ tự chạy ngẫu nhiên giảm thiểu sự trôi lệch hệ thống. Bất thường PT ρ = 0.75 cho thấy một hiệu ứng nhất thời có thể chi phối ngay cả ở n = 20.
- **Hiệu lực bên ngoài.** Kết quả từ một máy đơn lẻ (Intel Core i7-12th Gen, Windows 11). Triển khai trên Cloud hoặc container và cấu hình bộ lập lịch Linux có thể dịch chuyển ranh giới ρ*.
- **Sức mạnh thống kê.** n = 20 mỗi ô được xác định trước khi thử nghiệm (phân tích power: 80% power, d = 0.8, α = 0.05); không áp dụng quy tắc dừng sớm.

### 6.6 Hướng phát triển tương lai

1. **Phân tích độ nhạy đối với thời gian phục vụ.** Lặp lại với thời gian nghẽn 20/40/80 ms để kiểm tra xem ρ* có dịch chuyển hay không; nó nên tương đối ổn định nếu mô hình mạnh mẽ.
2. **Chẩn đoán sự bùng nổ số lượng Platform Thread trên PostgreSQL.** Cần tracing mức driver của việc lấy kết nối HikariCP/PostgreSQL để giải thích tại sao số lượng Platform Thread tăng không giới hạn trên PostgreSQL nhưng không bị trên MSSQL dưới cấu hình pool giống hệt về mặt danh nghĩa.
3. **Đếm trực tiếp đối tượng continuation.** Heap profiling theo lớp (async-profiler, phân tích heap dump) để đếm trực tiếp đối tượng continuation trên mỗi mức ρ — một ưu tiên cao hơn hiện nay khi bằng chứng GC tăng theo ρ cho cả hai chế độ thay vì chỉ riêng VT.
4. **Tìm nguyên nhân gốc rễ bất thường PT ρ = 0.75.** Lặp lại ô đó với định lượng từ xa mịn hơn (CPU steal, page faults, JIT events) để làm rõ liệu điểm ngoại lệ cực đoan (CV = 402%) là hành vi đuôi thực sự hay một nhiễu môi trường.

---

## 7. Kết luận (Conclusion)

Bài báo này xây dựng và kiểm chứng một mô hình dự đoán về vùng hoạt động mà trong đó Platform Threads và Virtual Threads tạo ra sự khác biệt rõ rệt về độ trễ. Mô hình là Erlang-C / M/M/c không sửa đổi, được áp dụng cho cả hai mô hình thực thi thông qua một quyết định mô hình hóa: các slot connection pool, chứ không phải luồng carrier, là các kênh phục vụ cho cả hai. Tiền tệ tài nguyên (Resource currency) — sự chiếm giữ luồng OS native (PT) so với trạng thái continuation trú đóng trên heap (VT) — là từ vựng được sử dụng để diễn giải *tại sao* mô hình hoạt động và nơi nó bị phá vỡ.

**Phát hiện chính:** Phía dưới ρ* (≈0.70–0.75, nhất quán trên cả hai backend DBMS), VT và PT không thể phân biệt về độ trễ và Erlang-C dự đoán trong khoảng MAPE ≈9–12% trên MSSQL và ≈5–6% trên PostgreSQL. Phía trên ρ*, VT chịu độ trễ cao hơn PT trên MSSQL (lên tới +79% tại ρ = 0.95); hướng tương tự cũng diễn ra trên PostgreSQL nhưng với quy mô gấp khoảng 20×. Môi trường nghẽn thứ hai (HTTP) đảo ngược chế độ nào dễ tổn thương hơn khi bão hòa: sự tăng trưởng luồng không giới hạn của PT là chế độ thất bại ở đó. Tài nguyên nào bị giới hạn là đặc tính của cấu hình driver/pool/server cụ thể, chứ không chỉ riêng mô hình thực thi.

**Bài học thực tiễn:** Phía dưới ρ*, nên chọn VT hay PT dựa trên các lý do phi hiệu năng. Phía trên ρ*, chiều dài hàng chờ và tần suất thu gom GC — chứ không phải số lượng luồng hay chiếm dụng heap — là các tín hiệu giám sát có giá trị trên cả hai mô hình thực thi và các backend được nghiên cứu.

---

## Lời cảm ơn (Acknowledgements)

*(Sẽ bổ sung: truy cập phần cứng/phòng lab, người phản biện ẩn danh.)*

---

## Tài liệu tham khảo (References)

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

## Phụ lục A — Các Hình ảnh Bổ trợ (Supplementary Figures)

---

![Hình A1. Phân tích Bland-Altman](analysis/paper/fig5_bland_altman.png)

**Hình A1.** Biểu đồ Bland-Altman về mức độ phù hợp giữa Erlang-C dự đoán so với độ trễ trung bình đo được (tất cả 9 điểm hoạt động ρ < 1 ổn định, cả hai mô hình, n = 20 mỗi điểm). Độ chệch trung bình = +40.2 ms, giới hạn phù hợp [−278.5, +358.8 ms], bị chi phối gần như hoàn toàn bởi bất thường PT tại ρ = 0.75 (§5.2).

---

![Hình A2. So sánh VT và PT](analysis/paper/fig6_vt_vs_pt.png)

**Hình A2.** So sánh quỹ đạo độ trễ và chiều dài hàng chờ song song cho VT và PT trên cả 9 mức ρ ổn định. Các điểm: giá trị trung bình đo được ± 95% CI (n = 20). Đường đứt nét: dự đoán Erlang-C. Bất thường PT tại ρ = 0.75 hiển thị như một đỉnh nhọn cô lập.

---

![Hình A3. Heatmap tương quan](analysis/paper/fig7_correlation_heatmap.png)

**Hình A3.** Ma trận tương quan Spearman cho Nghiên cứu 1, chế độ ổn định (ρ < 1). Trái: Platform Threads — Threads_Mean tương quan mạnh với Rho_Target (r = 0.928). Phải: Virtual Threads — Threads_Mean gần như không tương quan với Rho_Target (r = 0.110). Heap_Mean_MB chỉ tương quan yếu với ρ ở cả hai chế độ (|r| ≤ 0.02).

---

*Tài liệu bổ trợ (Bảng S1, S2) — có sẵn trực tuyến: Bảng S1: Kích thước tác động VT vs. PT theo ρ, PostgreSQL (n = 20 mỗi ô). Bảng S2: Các chỉ số GC đầy đủ theo (mode, ρ), MSSQL (Nghiên cứu 1).*
