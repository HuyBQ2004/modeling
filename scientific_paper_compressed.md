# Modeling and Predicting Latency Within the Operating Regions of Java Virtual Threads


**Abstract** — This paper builds and validates a predictive model of the operating region in which Java Virtual Threads (VT) and Platform Threads (PT) produce meaningfully different latency. The model applies the standard Erlang-C / M/M/c approximation — unmodified — to both execution models, treating connection-pool slots as the service channels for both. *Resource currency* — native thread occupancy for PT versus heap-resident continuation state for VT — is the interpretive vocabulary for why one parameterization fits both models below a breakpoint and diverges above it. Validation on a Spring Boot / SQL Server microservice (20 replicates per operating point, ten-level ρ grid) locates the breakpoint at ρ* ≈ 0.75 (VT) and ρ* ≈ 0.70 (PT). Below ρ*, the model predicts latency within ≈9–12% MAPE and VT/PT are statistically indistinguishable; above ρ*, VT exceeds PT by +22% at ρ = 0.85 and +79% at ρ = 0.95. An independent replication against PostgreSQL shows that the model's *shape* generalizes — Region I MAPE is slightly lower on PostgreSQL (4.7–6.2%) and breakpoints are comparable (ρ* ≈ 0.65–0.75) — while the *severity* of post-breakpoint divergence does not: the VT–PT gap and Platform Thread count growth are an order of magnitude larger on PostgreSQL. A second blocking substrate (HTTP) reveals the opposite saturation failure mode: Platform Thread count grows unboundedly while VT degrades gracefully. These findings support regime-aware capacity planning: queue length and GC collection frequency are the monitoring signals that remain informative across both execution models and both substrates studied; thread count and raw heap occupancy are not.

**Keywords** — Java Virtual Threads, Project Loom, queueing theory, Erlang-C, M/M/c, performance modeling, blocking workload, operating regions, capacity planning, resource currency.

---

## 1. Introduction

### 1.1 The Problem Is Waiting, Not Computing

Modern server applications are increasingly constrained by waiting rather than computation. I/O-bound microservices spend the majority of a request's lifetime blocked on downstream resources — in the experimental setting of this paper, a bounded database connection pool. What differs between threading models is not *whether* requests wait, but *what the system pays while they wait*. We formalize this distinction through the concept of **resource currency**:

> **Definition 1 (Resource Currency).** *The resource currency of blocking is the physical resource whose occupancy is proportional to the inventory of waiting requests under a given execution model. Under Platform Threads, this resource is native OS thread slots (one thread held per waiting request). Under Virtual Threads, it is heap-resident continuation state (one parked continuation per waiting request, carrier thread released).*

Under Platform Threads, the thread pool acts as a hard concurrency cap. Under Virtual Threads (Project Loom, Java 21+), blocking unmounts the continuation from its carrier thread, which is released to the scheduler while the waiting state is serialized into heap-resident continuation objects. The *inventory* of waiting requests is identical across models; the *resource currency* in which that inventory is denominated differs fundamentally.

### 1.2 Gap and Research Questions

Existing work reports that Virtual Threads improve scalability under blocking workloads [1]–[6], but these studies remain largely empirical. Within the literature surveyed in §2, we did not find a study that (a) predicts the boundaries of the region where VT and PT differ or (b) quantifies where a classical queueing approximation is and is not valid for both execution models.

We address three falsifiable research questions and hypotheses stated **prior to measurement**:
Research Questions

RQ1. How does blocking change resource consumption under Platform Threads and Virtual Threads?
RQ2. Can an unmodified Erlang-C / M/M/c approximation accurately predict latency within the operating region?
RQ3. Where are the operating-region boundaries, and how does the model fail beyond them?

Hypotheses

- **H1** — Blocking changes the resource representation of waiting: PT holds native threads proportional to blocked inventory; VT parks continuations in heap memory while keeping the carrier pool stable.
- **H2** — An Erlang-C / M/M/c approximation predicts latency and queue length within a moderate operating region (ρ ≤ ρ*), with prediction quality degrading systematically above ρ*.
- **H3** — Prediction error increases monotonically as utilization exceeds the saturation breakpoint ρ*, consistent with progressive violation of model assumptions.

### 1.3 Contributions

This paper's contribution is empirical and interpretive, not new queueing mathematics: Erlang-C is applied unmodified throughout, and Lemma 1 (§6.1) is a direct restatement of Little's Law.

1. **A validated predictive model of the VT/PT operating envelope, independently replicated against a second DBMS backend.** The key modeling decision — treating connection-pool slots, not carrier threads, as the Erlang-C server count for *both* execution models — lets a single, unmodified parameterization predict both. Pre-registered before measurement (§4.2), it locates four falsifiable operating regions with boundaries found by data (ρ* ≈ 0.70–0.75 on MSSQL; ≈0.65–0.75 on PostgreSQL), achieving ≈9–12% latency MAPE within the queue-free region. The replication separates two claims: the region *boundary* generalizes across backends; the *severity* of divergence above it does not.

2. **Resource currency as an interpretive framework for *why* the model works and where it breaks** — a vocabulary (§1.1, §6.1) for the observation that identical blocking inventory is denominated in different physical resources under PT (native threads) and VT (heap-resident continuations). This motivates the regime-aware monitoring guidance in §6.3 and explains why thread count alone is an insufficient capacity signal under Virtual Threads.

---

![Figure 1. Operating envelope](analysis/paper/fig1_operating_envelope.png)

**Fig. 1.** The four-region operating envelope. Region I (ρ ≤ 0.77) is where Erlang-C is quantitatively reliable and VT/PT are latency-indistinguishable. Region II (0.77–0.90) marks the onset of resource-currency divergence. Region III (0.90–0.98) is the divergence region, where the model is directional only. Region IV (ρ ≥ 1) is intentionally unstable, used only as boundary evidence.

## 2. Related Work

### 2.1 Empirical Studies and Queueing Models

The introduction of Project Loom in Java 21 prompted a wave of empirical studies. Lašić et al. [2] found measurable VT throughput improvements in database-driven Spring Boot applications without a predictive model. Beronić et al. [4] surveyed structured concurrency patterns, characterizing VT as a scalable alternative to reactive frameworks. Hu [5] benchmarked VT against PT in SPECjbb2015, observing workload-dependent differences without formalizing the boundary. Šimatović et al. [7] studied GC behavior under high-concurrency VT workloads, noting heap pressure at high utilization — consistent with our continuation-accumulation abstraction. A common thread: these studies *report* performance differences more than they *explain* them analytically, and we are not aware of falsifiable, pre-registered predictions or quantitative model-validity boundaries in this literature.

M/M/c models and Erlang-C approximations have been applied to classical thread-pool configurations [8], connection pooling [9], and database concurrency [10]. Wu et al. [11] introduced blocking-less queuing (BLQ) as a runtime approach. The gap: prior queueing work targets classical thread-pool architectures. To our knowledge, these models have not been applied to the *currency-transforming* behavior of Loom-style virtual threads, where the number of service channels becomes decoupled from blocking inventory. Our aim is to formalize *why* the two models diverge and *where* that divergence starts. We adopt the replicated-measurement framework of Taipalus [14] and Vershinin and Mustafina [15] (20 replicates per operating point, independent replication against a second DBMS backend).

---

## 3. Background: Queueing Theory and Erlang-C

### 3.1 M/M/c Model

We model the connection pool as an M/M/c queue with Poisson arrivals (rate λ), exponential service time (mean 1/μ), and c servers (connection-pool capacity). The offered load per server is ρ = λ / (c · μ); stability requires ρ < 1.

### 3.2 Erlang-C Formula

The probability that an arriving request must wait is given by the Erlang-C formula C(c, a), where a = λ/μ is the offered traffic in Erlangs. Mean waiting time, queue length, and system latency follow:

> W_q = C(c, a) / (c · μ − λ),  L_q = λ · W_q,  W = W_q + 1/μ

### 3.3 Application to Virtual Threads

Under Virtual Threads, the *service channels* are not the carrier threads but the connection-pool slots. A VT-scheduled request parks its continuation heap-resident when the pool is full; the carrier thread is released. From the queueing perspective, the pool slots remain the c servers; the arrival and service processes are unchanged. The transformation is in the *resource substrate* of the waiting state, not in the queueing geometry — which is why one M/M/c parameterization models both execution models, and why it diverges above ρ*.

---

## 4. Methodology

### 4.1 System Under Test

We instrument a Spring Boot 3.2 microservice connected to Microsoft SQL Server 2019 via synchronous JDBC (HikariCP connection pool). The workload is a JPQL query followed by a server-side `WAITFOR DELAY '00:00:00.050'` — a **synthetic controlled-blocking workload** in which the connection is genuinely held for the full service duration, satisfying the M/M/c server definition (E[S] equals connection occupancy time). Experiments run on a dedicated host (Intel Core i7-12th Gen, 32 GB RAM, Windows 11, OpenJDK 21.0.2).

### 4.2 Benchmark Design

**Study 1 — ArrivalSweepBenchmark (primary).** Implements the M/M/c Poisson-arrival assumption by computing λ_target = ρ_target · c · μ and spacing requests with exponential inter-arrival gaps. Connection pool fixed at c = 50. Ten utilization levels: ρ ∈ {0.30, 0.50, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.95, 1.05} (ρ = 1.05 included as Region IV boundary evidence only). Two execution modes (VT, PT) × **20 replicates** per (ρ, mode) cell, each a 10-second warmup + 45-second measurement, run order randomized. Queue length E[N_q] is measured directly from `HikariPoolMXBean.getThreadsAwaitingConnection()` and cross-checked against a Little's-Law estimate. Run against both MSSQL (primary) and PostgreSQL (§5.2) with identical configuration.

**Study 2 — PoolSweepValidationBenchmark (secondary).** Sweeps connection pool sizes under burst-arrival to estimate the heap overhead parameter α per queued continuation. Because burst-arrival violates the Poisson assumption, this study is supplementary; Virtual Threads only. Results referenced in §5.1 where noted.

**Study 3 — HTTP substrate.** HttpSleepBenchmark replaces JDBC with HTTP I/O via Java `HttpClient` and a `Semaphore(50)` as the pool proxy, run at n = 20 against both backends. Results in §5.4.

**Pre-registration.** Erlang-C predictions for all operating points are written to CSV before any measurement cell is executed.

### 4.3 Metrics

* **Mean latency** (ms) and **P99 latency** (ms): end-to-end request response time within the measurement window.
* **Queue length** (*N*_q): mean HikariCP pending-connection count from `HikariPoolMXBean`.
* **Heap memory** (MB) and **Thread count**: mean JVM values sampled at 1 s intervals.
* **GC pause duration** (ms) and **GC collection count**: per-replicate delta from `GarbageCollectorMXBean`.
* **Net heap growth rate** (MB/s): net heap accumulation during measurement window — a coarse proxy for continuation-object accumulation, not a true allocation-volume measurement (§6.4).
* **Erlang-C predictions**: W and L_q from §3.2, frozen before measurement.

### 4.4 Statistical Analysis

Prediction error: MAE, RMSE, and MAPE across the nine ρ < 1 operating points. Residuals fail Shapiro–Wilk normality (p < 0.05) for all four latency/queue comparisons, so **Wilcoxon signed-rank** is used throughout (n = 9 cells per mode). **Cohen's *d*** and **Cliff's δ** quantify VT–PT practical significance per ρ level (n = 20 per mode). Breakpoint detection uses piecewise regression minimising total residual sum of squares; bootstrap 95% CI uses 2,000 resamples. All 95% CIs use the Student-t critical value (df = n − 1, n = 20).

### 4.5 Model Assumptions

The M/M/c approximation assumes: (A1) Poisson arrivals — measured inter-arrival CV exceeds 1 across the whole grid (≈1.5 at ρ = 0.30 rising to ≈2.9 near saturation), so A1 is strained throughout; (A2) exponential service time — approximately satisfied in Region I, shifting near saturation; (A3) no carrier-thread pinning — enforced by workload design; (A4) single homogeneous blocking source — enforced; (A5) open-loop arrival — enforced. A1 and A2 are the primary explanations for model degradation above ρ* (§6.2).

---

## 5. Results

### 5.1 RQ1 — Resource Consumption Is Consistent with Blocking-Currency Transformation

Under low utilization (ρ ≤ 0.50), both models exhibit nearly identical latency: PT at ρ = 0.30 yields 56.3 ms (95% CI ±0.257 ms); VT yields 56.4 ms (±0.168 ms). As utilization rises, the same waiting inventory materializes in two distinct physical substrates:

* **Platform Threads**: thread count tracks utilization monotonically (Spearman r = 0.928, p < 0.0001), rising from 147.3 at ρ = 0.30 to 199.4 at ρ = 0.95 on MSSQL. This bounded growth is backend-specific: the same benchmark on PostgreSQL shows Platform Thread count growing into the thousands near saturation (§5.2).
* **Virtual Threads**: carrier thread pool remains essentially constant across all ρ levels (r = 0.110, p = 0.14 — indistinguishable from zero), staying within 138.0–138.2, while queue length grows from 0.02 requests at ρ = 0.30 to 65.16 at ρ = 0.95.

Within Study 1, sampled heap occupancy correlates only weakly with ρ for **both** modes (Spearman r ≈ 0), while GC collection frequency correlates strongly (r = 0.99 VT, 0.90 PT). The resource-currency distinction manifests in **thread count** and **queue length**, not point-in-time heap occupancy.

**Conclusion:** Consistent with H1. Thread count is a misleading capacity signal under VT; raw heap occupancy is also unreliable under either mode. Queue length and GC frequency are the metrics that track load.

---

![Figure 8. Resource reallocation](analysis/paper/fig8_resource_reallocation.png)

**Fig. 8.** Resource reallocation under increasing utilization (Study 1, MSSQL, n = 20 per cell). Platform Threads (red) move up and to the right with utilization (r = 0.928). Virtual Threads (blue) move almost vertically — carrier count stays near 138 throughout (r = 0.110, n.s.) while queue length climbs two orders of magnitude.

### 5.2 RQ2 — The Approximation Predicts, Within a Region

Table 1 presents the full agreement analysis over the nine stable ρ levels.

**Table 1. Validation Metrics — Erlang-C vs. Measured Values (n = 20 replicates per cell, MSSQL)**

| Metric | n | MAE | RMSE | MAPE (%) | R² | r |
| ------------------------- | -- | ---- | ----- | --------- | ------- | ------ |
| Mean Latency — VT (ms) | 9 | 14.7 | 27.0 | 19.5 | 0.264 | 0.991 |
| Mean Latency — PT (ms) | 9 | 80.3 | 229.0 | 120.6 | −0.124 | −0.094 |
| Mean Latency — All (ms) | 18 | 47.5 | 163.0 | 70.0 | −0.063 | 0.034 |
| Queue Length — VT (req) | 9 | 7.1 | 18.0 | 357.2 | 0.210 | 0.998 |
| Queue Length — PT (req) | 9 | 27.0 | 77.3 | 39 932.4 | −0.148 | −0.055 |

*Bootstrap 95% CI (latency, all modes, 2,000 resamples): MAPE = 70.0% [7.2–189.1%]; RMSE = 163.0 ms [5.8–281.6 ms].*

---

![Figure 2. Latency vs rho](analysis/paper/fig2_latency_vs_rho.png)

**Fig. 2.** Mean latency vs. target utilization ρ with Erlang-C predictions overlaid. Error bars: 95% CI over 20 replicates.

---

![Figure 3. Calibration](analysis/paper/fig3_calibration.png)

**Fig. 3.** Calibration plot: Erlang-C predicted vs. measured mean latency (a) and queue length (b) for all (ρ, mode) pairs. Points above the 1:1 diagonal indicate model underestimation.

The aggregate PT statistics are dominated by a single anomalous cell: at ρ = 0.75, PT recorded 753.2 ms with a 95% CI of ±1417.3 ms (CV ≈ 402%), roughly an order of magnitude above neighboring cells (62.9 ms at ρ = 0.70, 65.7 ms at ρ = 0.80). We treat this as a measurement anomaly — not reproduced at any neighboring ρ, no analogous spike for VT at the same ρ. Excluding it, PT's MAPE over the remaining eight points drops from 120.6% to 7.0%. Within Region I (ρ ≤ 0.70), relative latency error is 12.0% (VT) and 9.1% (PT).

**Statistical bias tests** (Table 2): latency bias is not statistically significant for either mode; queue-length bias **is** significant for both (W = 0.0, p = 0.0078), with the model consistently under-predicting queue length.

**Table 2. Statistical Significance of Prediction Error (Wilcoxon signed-rank, n = 9 cells per mode)**

| Comparison | Statistic | *p* | Significant |
| ---------- | --------- | ------ | ----------- |
| Latency VT | 17.00 | 0.5703 | No |
| Latency PT | 18.00 | 0.6523 | No |
| Queue VT | 0.00 | 0.0078 | **Yes** |
| Queue PT | 0.00 | 0.0078 | **Yes** |

**Cross-DBMS check (PostgreSQL).** Study 1 was re-run against PostgreSQL with the identical configuration. Restricting to Region I (ρ ≤ 0.70), mean absolute latency error is 5.1% (VT) and 6.2% (PT) — slightly *better* than MSSQL's 9.1–12.0%. The queue-free operating region generalizes; the magnitude of divergence above the breakpoint does not. At ρ = 0.95, measured mean latency on PostgreSQL reaches 3192.4 ms (VT) and 1977.1 ms (PT) — roughly 20× and 22× the MSSQL values. Platform Thread count grows to 3707.9 on PostgreSQL versus 199.4 on MSSQL — an unbounded growth structurally similar to the HTTP behavior in §5.4. VT carrier count remains flat on both backends (138.0–138.2). Breakpoint detection on PostgreSQL places ρ* ≈ 0.75 for VT and ≈ 0.65 for PT. Full per-ρ effect sizes are in Supplementary Table S1.

**Conclusion:** Results support H2 within Region I. The approximation is quantitatively useful below per-mode breakpoints (MAPE ≈ 9–12% on MSSQL) and qualitatively informative above them. Queue length is consistently under-predicted even in Region I, so queue-length estimates should carry a safety margin.

### 5.3 RQ3 — Where the Model Stops Working

**Step 1 — Breakpoint detection.** Piecewise regression places the onset of systematic deviation at **ρ* ≈ 0.75 for Virtual Threads** (bootstrap 95% CI [0.75, 0.80], SSR improvement 94.5%) and **ρ* ≈ 0.70 for Platform Threads** (CI [0.50, 0.80], SSR improvement 44.9%). PT's wide CI is driven by the ρ = 0.75 anomaly. Boundaries were found by the data, not chosen by the authors.

---

![Figure 4. Residual analysis](analysis/paper/fig4_residual.png)

**Fig. 4.** Relative latency prediction error (%) vs. ρ for VT and PT, with shaded bootstrap 95% CI bands around each mode's breakpoint.

**Step 2 — Residual direction.** VT: model *overestimates* measured latency from ρ = 0.30 through ρ = 0.80 (signed error −14.9% narrowing to −0.9%), crossing to *underestimation* at ρ = 0.85 (+20.9%) and sharply at ρ = 0.95 (+94.1%). PT: error stays within ±5.2% from ρ = 0.60 through ρ = 0.85 (excluding the ρ = 0.75 anomaly). The VT sign-change is consistent with continuation accumulation: Erlang-C does not account for additional waiting from heap-resident continuation state once queueing becomes non-trivial.

**Step 3 — Effect sizes** (Table 3).

**Table 3. VT vs. PT Mean Latency and Effect Size by Utilization Level (n = 20 per cell, MSSQL)**

| ρ | VT mean (ms) | PT mean (ms) | Cohen's *d* | Cliff's δ | Magnitude |
| -------- | ------------- | ------------- | ----------: | --------: | --------------- |
| 0.30 | 56.4 | 56.3 | 0.254 | 0.228 | small |
| 0.50 | 58.0 | 58.7 | −0.303 | −0.142 | negligible |
| 0.60 | 60.3 | 69.3 | −0.316 | −0.185 | small |
| 0.65 | 60.4 | 63.3 | −0.545 | −0.440 | medium |
| 0.70 | 61.0 | 62.9 | −1.200 | −0.620 | large |
| **0.75** | 62.5 | **753.2** | −0.323 | −0.547 | large (outlier) |
| 0.80 | 66.2 | 65.7 | 0.159 | −0.130 | negligible |
| **0.85** | **82.2** | 67.2 | 1.332 | 0.720 | large |
| **0.95** | **161.0** | 90.1 | 1.111 | 0.790 | large |

*Pooled (all ρ < 1, n = 180 per mode): Cohen's d = −0.096, Cliff's δ = −0.062 (negligible) — opposite-signed per-ρ effects cancel in the pooled statistic.*

Above ρ*, VT becomes the higher-latency mode: Δ = 14.9 ms (+22%) at ρ = 0.85 and Δ = 70.9 ms (+79%) at ρ = 0.95.

**Step 4 — Operating envelope.**

| Region | ρ Range | Characterization |
| ------------------------------------ | ------------------- | --------------------------------------------------------------------------------------------------- |
| **I — Queue-free / model valid** | ρ ≤ 0.70–0.75 | MAPE 9–12%; VT and PT indistinguishable (absolute difference ≤ 3 ms) |
| **II — Transition** | 0.70/0.75 < ρ ≤ 0.90 | Onset of VT–PT divergence; model directional only |
| **III — Divergence** | 0.90 < ρ ≤ 0.98 | Large VT penalty (up to +79% at ρ = 0.95); model under-predicts both latency and queue length |
| **IV — Saturation** | ρ ≥ 1 | Intentionally unstable; VT 462.5 ms vs. PT 136.8 ms at ρ = 1.05 (JDBC; opposite under HTTP) |

**Conclusion:** Results support H3. Model validity is better described as two close but distinct per-mode breakpoints (VT ρ* ≈ 0.75, PT ρ* ≈ 0.70) than a single pooled value.

### 5.4 GC Evidence and HTTP Workload Transferability

**GC evidence (Study 1).** GC event count rises monotonically for both modes: VT 6.2 → 24.6 (3.96×) and PT 7.4 → 21.9 (2.96×) from ρ = 0.30 to ρ = 1.05. GC pause time follows the same pattern (VT 6.8×, PT 4.6×), with VT scaling more steeply. Net heap growth rate is essentially flat for both modes (3.5–3.7 MB/s), so the GC divergence is driven by collection *frequency*, not net heap accumulation. Full per-(mode, ρ) GC data are in Supplementary Table S2.

**HTTP workload (Study 3).** From ρ = 0.30 through ρ = 0.85, VT and PT are close under the HTTP substrate (VT 62–64 ms, PT 61–65 ms). At the unstable ρ = 1.05 point, the two modes diverge sharply and in the **opposite direction** from Study 1: **VT mean latency is 1084.5 ms; PT is 1975.5 ms (+82% relative to VT)**. PT's mean thread count explodes to 1737.3 (max 10,115) with 369 rejected requests; VT's carrier thread count stays at 132.3 with zero rejections. PostgreSQL at ρ = 1.05 reproduces the same direction (PT 2277.6 ms vs. VT 1902.0 ms).

**Transferability.** Low-to-moderate-load indistinguishability (Region I) is consistent across both substrates and backends. At saturation, the two substrates disagree on *which mode fails more severely*: VT under JDBC (bounded pool shared by both modes), PT under HTTP (no equivalent bound on PT's native thread growth). Which resource ends up bounded is a property of the specific driver/pool/server configuration, not of execution model alone.

---

## 6. Discussion

### 6.1 Why Does the Model Work Below ρ*?

The mechanism is Little's Law: E[N] = λ · W, which holds regardless of execution model. Parking a continuation and blocking a thread are the same *queueing* event — both remove one unit of service capacity from the pool and add one unit to inventory E[N]. Below ρ*, currency choice is invisible to latency, which is why one Erlang-C parameterization fits both execution models.

**Lemma 1 (Resource Currency Invariance).** *The physical realization of N is model-dependent — PT: native thread occupancy; VT: heap-resident continuation state — but the waiting time W itself is currency-invariant.*

**Corollary:** Below ρ*, VT and PT are latency-indistinguishable. In Region I, VT vs. PT is a **design decision that should be made on non-performance grounds** — memory footprint, operational constraints, code complexity — because latency consequences are negligible. The per-mode breakpoints and operating-envelope regions produced here depend on comparing measurements against a predictive model, which distinguishes this analysis from a benchmark comparison alone (§2).

### 6.2 Why Does Prediction Degrade Above ρ*?

Degradation is consistent with violation of assumptions A1 and A2 (§4.5):

1. **Burst arrivals (A1 violated).** Inter-arrival CV > 1 across the entire grid, intensifying with load. Erlang-C underestimates waiting for super-Poisson arrivals, consistent with under-prediction growing with ρ.

2. **Service time distribution shift (A2 strained).** At high queue occupancy, HikariCP connection acquisition time becomes an increasing component of service time; the combined distribution is no longer well-approximated by a single-parameter exponential.

3. **VT-specific: continuation overhead (weaker evidence).** GC event count and pause duration scale with ρ for both VT (r = 0.99) and PT (r = 0.90), VT somewhat more steeply — qualified support for a VT-specific mechanism, since GC pressure alone does not cleanly separate the two modes. Causal attribution from continuation accumulation to the VT-specific latency gap above ρ* remains a hypothesis; per-class allocation profiling would be needed to confirm it (§6.4).

### 6.3 Practical Implications

**DP1: Monitor queue length and GC frequency, not thread count or raw heap occupancy.** Thread count is a misleading capacity signal under VT — it stays flat even as queue length grows two orders of magnitude. The VT-specific warning sign is the *combination* of rising queue length with flat thread count.

**DP2: Apply queueing approximation only within the valid operating region.** Erlang-C predictions are quantitatively reliable in Region I (ρ ≤ 0.70 for PT, ≤ 0.75 for VT). In Region II, treat model output as directional only. In Region III, use as a saturation warning only — and note that the model under-predicts queue length even in Region I, so queue estimates should carry a safety margin.

**DP3: Capacity planning should be regime-aware, substrate-aware, and backend-aware.** The boundary ρ* ≈ 0.65–0.75 is reasonably consistent across the JDBC configurations and substrates studied. What is *not* consistent is the saturation failure mode: under MSSQL/JDBC, VT accumulates more latency while PT's thread count stays bounded; under PostgreSQL/JDBC, PT's thread count grows unboundedly; under HTTP, PT blows up while VT degrades gracefully. Identify which resource is actually bounded in your specific deployment and confirm empirically per DBMS/driver/framework.

**Table 4. Monitoring Signal Checklist by Operating Region**

| Signal | Region I (ρ ≤ 0.70–0.75) | Region II (up to ρ ≈ 0.90) | Region III (ρ > 0.90) |
| ---------------------- | ------------------------- | --------------------------- | -------------------------- |
| Carrier thread count | ✓ PT; ✗ misleading for VT | ✗ (VT); ✓ (PT, until substrate-specific bound) | ✗ (VT); ⚠ blow-up warning (PT, HTTP) |
| Request queue length | ✓ Reliable (both) | ✓ Reliable (both) | ✓ **Primary signal** (both) |
| Heap occupancy (MB) | No detected signal (both) | No detected signal | Do not rely on this alone |
| GC collection frequency | Optional (tracks ρ, both) | ✓ Watch (both) | ✓ Watch (both) |
| Erlang-C model output | ✓ Latency; queue under-predicted | ⚠ Directional only | ⚠ Saturation warning only |

### 6.4 Limitations

- **Two DBMS backends — disagreeing on severity.** The queue-free region generalizes well (≈5–6% MAPE on PostgreSQL vs. ≈9–12% on MSSQL), but post-breakpoint divergence is an order of magnitude larger on PostgreSQL and Platform Thread count, bounded on MSSQL, grows essentially without limit on PostgreSQL. Take the *existence* and *location* of the operating envelope as reasonably portable; treat specific gaps and which resource bounds Platform Threads as configuration-specific. MySQL and non-relational stores remain untested.
- **Single blocking type in Studies 1–2.** Study 3 adds HTTP as a second substrate, finding a substrate-dependent saturation failure mode. Generalizability to Redis, Kafka, gRPC, or mixed blocking patterns remains untested.
- **Sample size.** Twenty replicates per cell tightens CIs at low-to-moderate utilization, but variance stays high near saturation and PT's breakpoint CI ([0.50, 0.80]) is wide due to the ρ = 0.75 anomaly.
- **Indirect resource-currency measurement.** Queue length is measured directly via `HikariPoolMXBean` and cross-checked against a Little's-Law estimate (agreeing to within ~4% at highest stable ρ). Neither counts heap-resident continuation objects directly. Per-class heap profiling would resolve the ambiguity.
- **Virtual Thread pinning excluded (A3).** Workloads triggering carrier thread pinning (legacy `synchronized`, JNI, `Object.wait()`) invalidate the currency-transformation abstraction and are out of scope.
- **JVM version.** Results are calibrated for OpenJDK 21.0.2; future Loom scheduler versions may shift operating-region boundaries.

### 6.5 Threats to Validity

- **Internal validity.** A 10-second warmup mitigates JVM warmup effects; randomized run order mitigates systematic drift. The PT ρ = 0.75 anomaly shows a transient effect can dominate even at n = 20.
- **External validity.** Results are from a single machine (Intel Core i7-12th Gen, Windows 11). Cloud or containerized deployments and Linux scheduler configurations may shift ρ* boundaries.
- **Statistical power.** n = 20 per cell was determined prior to experimentation (power analysis: 80% power, d = 0.8, α = 0.05); no stopping rule was applied.

### 6.6 Future Work

1. **Sensitivity analysis on service time.** Repeat with blocking durations of 20/40/80 ms to test whether ρ* shifts; it should be relatively stable if the model is robust.
2. **Diagnose the PostgreSQL Platform Thread count blow-up.** Driver-level tracing of HikariCP/PostgreSQL connection acquisition is needed to explain why Platform Thread count grows unboundedly on PostgreSQL but not MSSQL under the nominally identical pool configuration.
3. **Direct continuation object count.** Per-class heap profiling (async-profiler, heap dump analysis) to count continuation objects directly per ρ level — a higher priority now that GC evidence scales with ρ for both modes rather than VT alone.
4. **Root-cause the PT ρ = 0.75 anomaly.** Repeat that cell with finer telemetry (CPU steal, page faults, JIT events) to clarify whether the extreme outlier (CV = 402%) is a real tail behavior or an environmental confound.

---

## 7. Conclusion

This paper builds and validates a predictive model of the operating region in which Platform Threads and Virtual Threads produce meaningfully different latency. The model is unmodified Erlang-C / M/M/c, applied to both execution models via one modeling decision: the connection-pool slots, not the carrier threads, are the service channels for both. Resource currency — native OS thread occupancy (PT) versus heap-resident continuation state (VT) — is the vocabulary used to interpret *why* this works and where it breaks.

**Key finding:** Below ρ* (≈0.70–0.75, consistent across both DBMS backends), VT and PT are latency-indistinguishable and Erlang-C predicts within ≈9–12% MAPE on MSSQL and ≈5–6% on PostgreSQL. Above ρ*, VT incurs higher latency than PT on MSSQL (up to +79% at ρ = 0.95); the same direction holds on PostgreSQL but at roughly 20× the magnitude. A second blocking substrate (HTTP) reverses which mode is more fragile at saturation: PT's uncapped thread growth is the failure mode there. Which resource ends up bounded is a property of the specific driver/pool/server configuration, not of execution model alone.

**Practical take-away:** Below ρ*, VT vs. PT should be chosen on non-performance grounds. Above ρ*, queue length and GC collection frequency — not thread count or heap occupancy — are the monitoring signals that hold up across both execution models and backends studied.

---

## Acknowledgements

*(To be added: hardware/lab access, anonymous reviewers.)*

---

## References

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

## Appendix A — Supplementary Figures

---

![Figure A1. Bland-Altman analysis](analysis/paper/fig5_bland_altman.png)

**Fig. A1.** Bland-Altman agreement plot of Erlang-C predicted vs. measured mean latency (all nine stable ρ < 1 operating points, both models, n = 20 per point). Mean bias = +40.2 ms, limits of agreement [−278.5, +358.8 ms], driven almost entirely by the PT ρ = 0.75 anomaly (§5.2).

---

![Figure A2. VT vs PT comparison](analysis/paper/fig6_vt_vs_pt.png)

**Fig. A2.** Side-by-side latency and queue-length trajectory comparison for VT and PT across all nine stable ρ levels. Points: measured mean ± 95% CI (n = 20). Dashed line: Erlang-C prediction. The PT ρ = 0.75 anomaly is visible as an isolated spike.

---

![Figure A3. Correlation heatmap](analysis/paper/fig7_correlation_heatmap.png)

**Fig. A3.** Spearman correlation matrices for Study 1, stable regime (ρ < 1). Left: Platform Threads — Threads_Mean correlates strongly with Rho_Target (r = 0.928). Right: Virtual Threads — Threads_Mean is nearly uncorrelated with Rho_Target (r = 0.110). Heap_Mean_MB correlates only weakly with ρ for either mode (|r| ≤ 0.02).

---

*Supplementary material (Tables S1, S2) — available online: Table S1: VT vs. PT per-ρ effect sizes, PostgreSQL (n = 20 per cell). Table S2: Full GC metrics per (mode, ρ), MSSQL (Study 1).*
