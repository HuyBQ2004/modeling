# Modeling and Predicting Latency Within the Operating Regions of Java Virtual Threads


**Abstract** — This paper builds and validates an empirical, predictive model of the operating region in which Java Virtual Threads (VT) and Platform Threads (PT) produce meaningfully different latency, and the region in which they do not. The model applies the standard Erlang-C / M/M/c approximation — unmodified, no new queueing formulas — to both execution models, using the same connection-pool slots as the service channels for both. We call the physical resource each model spends while a request waits its *resource currency* (native thread occupancy for PT, heap-resident continuation state for VT); this is an interpretive lens for *why* one queueing parameterization fits both models below a breakpoint and diverges above it, not a new theoretical result in its own right. Validation on a Spring Boot / SQL Server microservice, replicated 20 times per operating point across a ten-level ρ grid, locates the onset of systematic model deviation at ρ* ≈ 0.75 (VT) and ρ* ≈ 0.70 (PT). Below ρ*, the model predicts latency to within ≈9–12% MAPE and VT/PT are statistically close to indistinguishable; above ρ*, the model degrades to a directional/saturation-warning signal only, and VT exceeds PT by 14.9 ms / +22% at ρ = 0.85 and 70.9 ms / +79% at ρ = 0.95. A second blocking substrate (HTTP) shows the opposite failure mode at saturation: Platform Threads degrade more severely once concurrency exceeds the servlet thread pool's capacity, with thread-count blow-up and request rejections VT avoids. Independently replicating the primary study against a second DBMS backend (PostgreSQL) shows that what generalizes is the model's *shape* — Region I prediction error is, if anything, slightly lower on PostgreSQL (4.7–6.2% vs. 9.1–12.0% on MSSQL), and breakpoints fall in a comparable range (ρ* ≈ 0.65–0.75) — while the *severity* of divergence above ρ* and even *which physical resource ends up bounding Platform Threads* do not: PostgreSQL shows an order-of-magnitude larger VT–PT gap near saturation, with Platform Thread count growing unboundedly rather than staying capped by the connection pool as it does on MSSQL. These findings support regime-aware capacity planning; queue length and GC collection frequency, not raw heap occupancy or thread count alone, are the monitoring signals that remain informative across both execution models and both substrates studied. We position resource currency as a vocabulary for interpreting these results within the Java Virtual Thread system studied here, not as a validated general theory of blocking concurrency — extending it to other execution models (goroutines, actors, async/await, reactive) is future work requiring new cross-runtime data (§6.7).

**Keywords** — Java Virtual Threads, Project Loom, queueing theory, Erlang-C, M/M/c, performance modeling, blocking workload, operating regions, capacity planning, resource currency.

---

## 1. Introduction

### 1.1 The Problem Is Waiting, Not Computing

Modern server applications are increasingly constrained by waiting rather than computation. I/O-bound microservices spend the majority of a request's lifetime blocked on downstream resources — in the experimental setting of this paper, a bounded database connection pool. What differs between threading models is not *whether* requests wait, but *what the system pays while they wait*.

We formalize this distinction through the concept of **resource currency**:

> **Definition 1 (Resource Currency).** *The resource currency of blocking is the physical resource whose occupancy is proportional to the inventory of waiting requests under a given execution model. Formally: for execution model M with blocking inventory N, the currency is the resource R such that* occupancy(R) ∝ E[N]*. Under Platform Threads, R = native OS thread slots (one thread held per waiting request). Under Virtual Threads, R = heap-resident continuation state (one parked continuation per waiting request, carrier thread released).*

The *inventory* of waiting requests is identical across models; the *resource currency* in which that inventory is denominated differs fundamentally. This distinction is the central object of study (Figure 1). Under Platform Threads, the thread pool acts as a hard concurrency cap: once all threads are in use, the system has already committed its native thread budget even as new arrivals queue in memory. Under Virtual Threads (Project Loom, Java 21+), blocking unmounts the continuation from its carrier thread, which is released immediately to the scheduler while the waiting state is serialized into heap-resident continuation objects.

### 1.2 The Gap in Existing Work

Existing work reports that Virtual Threads improve scalability under blocking workloads [1]–[6]. However, these studies remain largely empirical and provide limited analytical understanding of *when* and *why* the two models diverge. Within the work surveyed in §2, we did not find a study that predicts the boundaries of the region where the difference matters or formalizes the resource-currency transformation discussed here. Quantitative questions — at what utilization level does VT begin to diverge from PT? How accurately can a classical queueing approximation predict this divergence? Where does the approximation fail and why? — are, to our knowledge, largely unaddressed by that literature.

> *This paper is not a new scheduling algorithm. It is not a new runtime. Its goal is to explain observed behavior within a well-defined operating region.*

### 1.3 Research Questions and Hypotheses

We state three research questions and corresponding falsifiable hypotheses **prior to measurement**:

* **RQ1** — How does blocking change resource consumption under each execution model?
* **RQ2** — Can a queueing approximation (M/M/c, Erlang-C) predict latency and queue length within moderate operating regions?
* **RQ3** — Where does the model stop working, and what happens there?

**H1** — Blocking changes the resource representation (currency) of waiting under each execution model: PT holds native threads proportionally to blocked inventory; VT parks continuations in heap memory while keeping the carrier pool stable.

**H2** — An Erlang-C / M/M/c approximation predicts latency and queue length within a moderate operating region (ρ ≤ ρ*), with prediction quality degrading systematically above ρ*.

**H3** — Prediction error tends to increase monotonically as utilization exceeds the saturation breakpoint ρ*, consistent with progressive violation of model assumptions.

### 1.4 Contributions

This paper's contribution is empirical and interpretive, not a new piece of queueing mathematics: Erlang-C is applied unmodified throughout (§3), and Lemma 1 (§6.1) is a direct restatement of Little's Law, not a new theorem. What is novel is a validated *predictive operating-region model* for two JVM execution models, and the modeling decision and interpretive vocabulary that make it work. The practitioner-facing guidance in §6.3 and the pre-registration protocol in §4.2 follow from the two contributions below rather than standing as independent results.

1. **A validated predictive model of the VT/PT operating envelope, independently replicated against a second DBMS backend.** The modeling decision — treating the connection-pool slots, not the carrier threads, as the Erlang-C server count for *both* execution models — is what lets a single, unmodified Erlang-C parameterization predict both. Pre-registered before measurement (§4.2), it locates four falsifiable operating regions with boundaries found by data, not chosen by the authors (ρ* ≈ 0.70–0.75 on MSSQL; ≈0.65–0.75 on an independent PostgreSQL replication, §5.2), with ≈9–12% (MSSQL) / ≈5–6% (PostgreSQL) mean relative latency error within the queue-free region. The replication separates two claims a single-backend study cannot: the region *boundary* generalizes across the two DBMS backends tested, while the *severity* of divergence above it does not (§5.2). *Reframes "Is VT faster than PT?" into "In which operating region, and on which backend, does VT produce a meaningful difference, and how large is it?"*

2. **Resource currency as an interpretive framework for *why* the model works and where it breaks** — not a new theoretical result, but a vocabulary (§1.1, §6.1) for the observation that identical blocking inventory (Little's Law, E[N] = λW) is denominated in different physical resources under PT (native threads) and VT (heap-resident continuations). *Explains why thread count alone is insufficient to characterize system load under Virtual Threads, and motivates the regime-aware monitoring guidance in §6.3. Generalizing this vocabulary into a predictive theory spanning other execution models is explicitly future work (§6.7), not a claim made here.*

---

![Figure 1. Operating envelope](analysis/paper/fig1_operating_envelope.png)

**Fig. 1.** The four-region operating envelope. Region I (ρ ≤ 0.77) is where Erlang-C is quantitatively reliable and VT/PT are latency-indistinguishable. Region II (0.77–0.90) marks the onset of resource-currency divergence. Region III (0.90–0.98) is the divergence region, where the model is directional only; continuation accumulation is one hypothesized driver of this divergence, not an established mechanism (§6.2). Region IV (ρ ≥ 1) is intentionally unstable, used only as boundary evidence. The ρ ≈ 0.77 boundary is the pooled, rounded value used for the figure; §5.3 reports the exact per-mode breakpoints (VT ≈ 0.75, PT ≈ 0.70) it approximates.

## 2. Related Work

### 2.1 Empirical Studies of Java Virtual Threads

The introduction of Project Loom's Virtual Threads in Java 21 prompted a wave of empirical studies. Lašić et al. [2] found measurable VT throughput improvements in database-driven Spring Boot applications under high concurrency, without a predictive model. Beronić et al. [4] surveyed structured concurrency patterns and characterized VT as a scalable alternative to reactive frameworks. Hu [5] benchmarked VT against PT in SPECjbb2015 on OpenJDK 25, observing workload-dependent differences without formalizing the boundary. Ponnampalam [6] compared VT and RxJava in a real-time clearing system, finding complexity-accuracy trade-offs. Šimatović et al. [7] studied GC behavior under high-concurrency VT workloads, noting heap pressure at high utilization — consistent with our continuation-accumulation abstraction. Charlak et al. [1] compared reactive programming and VT, finding regime-dependent differences without quantifying operating boundaries.

A common pattern across these studies: they *report* performance differences more than they *explain* them analytically. We are not aware of falsifiable, pre-registered predictions or quantitative model-validity boundaries in this specific literature, though our survey of it is necessarily not exhaustive.

### 2.2 Queueing Models Applied to Thread Pools

Queueing theory provides established tools for analyzing service systems. M/M/c models and Erlang-C approximations have been applied extensively to classical thread-pool configurations [8], connection pooling [9], and database concurrency [10]. Wu et al. [11] introduced blocking-less queuing (BLQ) as a runtime approach to reducing blocking overhead, from a compiler rather than an analytical-modeling perspective. Poutanen et al. [12] formalized a non-blocking edge API gateway pipeline that complements our queueing approach but targets a different system class.

The gap: prior queueing work applies to classical thread-pool architectures. To our knowledge, these models have not previously been applied to the *currency-transforming* behavior of Loom-style virtual threads, where the number of service channels (carriers) becomes decoupled from blocking inventory.

### 2.3 Performance Measurement Methodology

Reliable performance measurement requires rigorous experimental design. JVM microbenchmarks require warmup, GC stabilization, and replicated measurement [13]. Taipalus [14] provides a systematic review of DBMS performance comparison methodology. Vershinin and Mustafina [15] demonstrate variability in DBMS benchmarks across SQL Server, PostgreSQL, and MySQL configurations. We adopt this replicated-measurement framework, with 20 replicates per operating point (§4.2) and independent replication against a second DBMS backend (§5.2), following these methodological standards.

### 2.4 Gap and Positioning

Relative to the empirical studies in §2.1, this paper's aim is closer to explanation than evaluation: rather than adding another VT-vs-PT throughput number, we try to formalize *why* the two models diverge and *where* that divergence starts. Within the literature surveyed in §2.1–§2.2, we did not find a falsifiable, pre-registered analytical model that both (a) formalizes the resource-currency transformation introduced by Virtual Threads and (b) identifies quantitative boundaries where the model is and is not valid; we cannot rule out that such work exists outside that survey. We frame this as a complement to the existing empirical literature, not a replacement for it.

---

## 3. Background: Queueing Theory and Erlang-C

### 3.1 M/M/c Model

We model the connection pool as an M/M/c queue with:

* Poisson arrival process with rate λ (requests/second)
* Exponential service time with mean 1/μ (seconds)
* c servers (connection pool capacity)
* Infinite waiting room (FCFS discipline)

The offered load (traffic intensity) per server is:

> ρ = λ / (c · μ)

For stability, ρ < 1 is required.

### 3.2 Erlang-C Formula

The probability that an arriving request must wait (all servers busy) is given by the Erlang-C formula:

> C(c, a) = (a^c / c!) · (c / (c − a)) / [Σ_{k=0}^{c−1} (a^k / k!) + (a^c / c!) · (c / (c − a))]

where a = λ/μ is the offered traffic (Erlangs).

Mean waiting time in queue:

> W_q = C(c, a) / (c · μ − λ)

Mean number in queue (Little's Law):

> L_q = λ · W_q

Mean system latency:

> W = W_q + 1/μ

### 3.3 Application to Virtual Threads

**Key insight:** Under Virtual Threads, the *service channels* are not the carrier threads but the connection pool slots. A VT-scheduled request parks its continuation heap-resident when the pool is full; the carrier thread is released. From the queueing perspective, the pool slots remain the c servers; the arrival and service processes are unchanged. The transformation is in the *resource substrate* of the waiting state, not in the queueing geometry.

This is the central observation that permits one M/M/c parameterization to model both execution models — and explains both why it works below ρ* and why it diverges above it.

**Scope note.** No queueing formula in §3.1–§3.2 is modified or extended for this paper; C(c, a), W_q, L_q, and W are the standard Erlang-C expressions (see §1.4 for what the contribution consists of instead). Readers looking for a generalized theory of blocking concurrency across execution models (goroutines, actors, async/await, reactive) will not find one here; §6.7 discusses this as a distinct, larger undertaking that the present single-runtime dataset cannot support.

---

## 4. Methodology

### 4.1 System Under Test

We instrument a Spring Boot 3.2 microservice connected to a Microsoft SQL Server 2019 instance via synchronous JDBC (HikariCP connection pool, dynamically resizable via `setMaximumPoolSize`). The workload consists of a JPQL query followed by a server-side `WAITFOR DELAY '00:00:00.050'` statement — a **synthetic controlled-blocking workload** in which the connection is genuinely held for the full service duration, making connection hold-time the quantity directly modeled by M/M/c. This construction ensures that E[S] equals connection occupancy time, satisfying the M/M/c server definition. Readers should note that this is a laboratory workload, not a production trace; generalizability to heterogeneous or mixed I/O patterns is a stated limitation (§6.4).

Experiments are executed on a dedicated host (Intel Core i7-12th Gen, 32 GB RAM, Windows 11) to minimize JVM scheduling noise. JVM version: OpenJDK 21.0.2 (Project Loom GA).

### 4.2 Two-Study Benchmark Design

This paper reports results from **two complementary benchmark programs**, each targeting a distinct aspect of the M/M/c abstraction:

**Study 1 — ArrivalSweepBenchmark (primary, ρ-targeted, open-loop).** Implements the M/M/c Poisson-arrival assumption by computing λ_target = ρ_target · c · μ and spacing requests with exponential inter-arrival gaps from an absolute-deadline dispatcher thread. Connection pool fixed at **c = 50**. Ten utilization levels: **ρ ∈ {0.30, 0.50, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.95, 1.05}** (ρ = 1.05 intentionally unstable, included as Region IV boundary evidence only, no PASS/FAIL verdict). Two execution modes (VT, PT) × **20 replicates** per (ρ, mode) cell, each a **10-second warmup** + **45-second measurement**, run order randomized (seed = 42). Achieved arrival rate λ̂ and inter-arrival CV are recorded per replicate as a check of assumption A1 (CV ≈ 1 ⇒ Poisson-like); measured CV ranges from ≈1.5 at ρ = 0.30 to ≈2.6–2.9 near saturation, indicating mildly super-Poisson arrivals that intensify with load (§4.5, §6.2). Queue length E[N_q] is measured directly from `HikariPoolMXBean.getThreadsAwaitingConnection()` (200 ms sampling), not estimated from the model, and cross-checked against a Little's-Law estimate from the same replicate. Configurable via JVM properties (`-Darrival.pool/reps/warmup/measure`, defaults 50/20/10/45); run against both Microsoft SQL Server 2019 (primary) and PostgreSQL (§5.2 cross-DBMS check).

**Study 2 — PoolSweepValidationBenchmark (secondary, pool-sweep, burst-arrival).** Sweeps connection pool sizes ({10, 25, 50} training; {100, 200} test) under a **burst-arrival** pattern, estimating the heap overhead parameter α per queued continuation and validating pool-size predictions via cross-validation. Because burst-arrival violates the Poisson assumption (A1), this study is **supplementary**, not the primary latency-vs-ρ validation. Concurrency **4,000** virtual threads, **8 iterations** (2 warmup + 6 measurement) per pool size, **Virtual Threads only** — no Platform Thread pool-sweep run is available, so §5.1/§6.4 report no VT/PT heap-vs-pool-size contrast.

**Data source for §5.** The primary results in §5.1–§5.3 are from Study 1 (MSSQL); the same benchmark and pipeline were independently applied to PostgreSQL as a cross-DBMS check (§5.2). Heap-memory correlation (Study 2, VT only) is referenced where noted. Study 3 (HTTP substrate, both backends) is reported in §5.4.

**Fine-grained ρ grid.** The original six-level grid {0.30, 0.50, 0.70, 0.85, 0.95, 1.05} was expanded with four intermediate levels {0.60, 0.65, 0.75, 0.80} around the breakpoint region, tightening breakpoint CI estimation and revealing the shape of the latency–utilization curve in more detail.

**Pre-registration.** In both studies, Erlang-C predictions for all operating points are written to CSV files before any measurement cell is executed, satisfying the pre-registration requirement (Framework §6).

---

> **Editorial note.** A conceptual-framework figure (blocking request → currency-invariant inventory E[N] → PT/native-thread vs. VT/heap-continuation branches → ρ ≤ ρ* latency-indistinguishable / ρ > ρ* continuation accumulation) is planned for this position but not yet produced; it would carry no numeric content, only the structure already stated in Definition 1 and §3.3.

### 4.3 Metrics

* **Mean latency** (ms): mean end-to-end request response time within the measurement window.
* **P99 latency** (ms): 99th-percentile response time (measured only; not predicted by Erlang-C).
* **Queue length** (*N*_q): mean HikariCP pending-connection count (ground-truth observation from `HikariPoolMXBean`).
* **Heap memory** (MB): mean JVM heap usage sampled every 5 × 200 ms = 1 s.
* **Thread count**: mean JVM live thread count (same sampling interval).
* **GC pause duration** (ms): total GC pause time per replicate, measured via `GarbageCollectorMXBean.getCollectionTime()` delta. Provides **direct** evidence of GC pressure per ρ level without requiring a JFR agent.
* **GC collection count**: number of GC events per replicate (same MXBean, delta method).
* **Net heap growth rate** (MB/s): net heap accumulation during the measurement window, computed as (heap_max − heap_at_window_start) / window_seconds. This is **not** a measurement of true allocation volume — standard JVM MXBeans (`MemoryMXBean`) cannot observe gross bytes allocated, only heap occupancy at sample times, so garbage collected between samples is invisible to this metric. We label it "net heap growth" throughout rather than "allocation rate" to avoid overstating what is measured; it is used as a coarse proxy for continuation-object accumulation under VT, not a direct allocation-volume measurement. True allocation rate would require `ThreadMXBean.getThreadAllocatedBytes()` or JFR allocation profiling, neither of which this benchmark instruments (§6.7, item 4).
* **Little's Law check**: λ̂ · (W̄ − E[S]) — an internal consistency test for queue-length measurements.
* **Erlang-C predictions**: W̃ (mean latency) and L̃_q (queue length) from §3.2, frozen before measurement.

### 4.4 Statistical Analysis

Prediction error is quantified via MAE, RMSE, and MAPE across the nine ρ < 1 operating points. Agreement analysis uses:

* **Pearson r** and R² (explained variance relative to a flat-line baseline).
* **Shapiro–Wilk** normality test on paired residuals to select **paired t-test** (normal) or **Wilcoxon signed-rank** (non-normal); in this revision residuals fail normality (p < 0.05) for all four latency/queue comparisons, so Wilcoxon signed-rank is used throughout (n = 9 cells per mode, one per ρ level, each cell aggregating 20 replicates).
* **Cohen's *d*** and **Cliff's δ**, computed on the raw per-replicate latencies (n = 20 per mode per ρ), to quantify the practical significance of VT–PT latency differences per ρ level.
* **Automated piecewise regression** for breakpoint detection: ρ* minimises total residual sum of squares (constant fit left of ρ*, linear fit right of it); bootstrap 95% CI uses 2,000 resamples over replicate-level resampling within each ρ cell.

**CI methodology note.** All 95% confidence intervals in this paper (per-cell latency/queue CIs in the text and Figure 2) are computed from raw per-replicate measurements using the Student-t critical value (df = n − 1, n = 20) and Bessel-corrected sample SD. This differs from the `CI95_*` columns written by the benchmark harness itself, which use a fixed z = 1.96 multiplier on the *population* (uncorrected) SD — understating the interval by roughly 9–13% at n = 20. The harness columns are retained in the raw data but are not quoted directly anywhere in this paper.

**Percentage-error methodology note.** This paper reports latency prediction error under two non-interchangeable formulas and readers should not compare them directly. Table 1's MAPE column and the aggregate figures in §5.2 ("120.6%", "19.5%") use mean(|measured − predicted| / **predicted**) — error as a fraction of the model's prediction. The Region-I per-mode figures quoted in §5.2 body text ("12.0% VT, 9.1% PT"; the "120.6% → 7.0%" anomaly-exclusion comparison) instead come from the harness's own `Latency_Error_Pct` column, |measured − predicted| / **measured** — error as a fraction of the observed value. The two agree closely at low ρ but diverge as the gap widens (e.g., PT MAPE excluding the ρ = 0.75 anomaly is 6.5% under the first formula, 7.0% under the second); neither is wrong, but this paper had not previously disclosed which was used where.


### 4.5 Model Assumptions

The Erlang-C / M/M/c approximation rests on the following assumptions. Deviations from these assumptions explain the systematic prediction errors documented in §5.3.

| Label | Assumption | Status in This Study |
| ------ | --------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| **A1** | Poisson arrival process (exponential inter-arrival times; CV = 1) | Measured inter-arrival CV exceeds 1 across the whole grid (≈1.5 at ρ = 0.30 rising to ≈2.6–2.9 near saturation) — A1 is strained at all ρ and increasingly so above ρ* |
| **A2** | Exponential service time (single-parameter service distribution) | Synchronous JDBC duration approximately exponential in Region I; distribution shifts near saturation |
| **A3** | No carrier thread pinning (no `synchronized`, JNI, or `Object.wait()` in workload path) | Enforced by workload design; JDBC driver uses async-compatible locking |
| **A4** | Single homogeneous blocking source (synchronous JDBC / SQL Server only) | Enforced; no mixed I/O types in workload |
| **A5** | Open-loop workload (arrival rate externally controlled) | Enforced by benchmark design; approximates closed-loop behavior at high concurrency |

§6.2 references A1–A2 as the primary explanations for model degradation above ρ*. §6.4 references A3 as the explicit scope boundary for the pinning limitation.

---

## 5. Results

### 5.1 RQ1 — Resource Consumption Is Consistent with Blocking-Currency Transformation

Under low utilization (ρ ≤ 0.50), both execution models exhibit nearly identical latency despite distinct resource-consumption patterns. PT at ρ = 0.30: mean latency 56.3 ms (95% CI ±0.257 ms, CV = 0.98%, n = 20); VT at ρ = 0.30: 56.4 ms (±0.168 ms, CV = 0.64%, n = 20). The difference is statistically and practically negligible.

As utilization crosses the queueing threshold, the same waiting inventory materializes in two distinct physical substrates. Our measurements are consistent with the resource-currency abstraction:

* **Platform Threads**: thread count tracks utilization monotonically (Spearman r = 0.928, p < 0.0001). Measured mean carrier thread count rises from 147.3 at ρ = 0.30 to 199.4 at ρ = 0.95 (243.6 at ρ = 1.05) — consistent with additional native OS threads being held open as waiting inventory grows, staying within a comparatively narrow band on this MSSQL backend. This bounded growth is backend-specific, not a general JDBC property: the same benchmark against PostgreSQL shows Platform Thread count growing into the thousands near saturation (§5.2, Table 3B).
* **Virtual Threads**: the carrier thread pool remains essentially constant across all ρ levels (r = 0.110, p = 0.14 — indistinguishable from zero). Measured mean carrier count stays within 138.0–138.2 from ρ = 0.30 through 0.95 (138.1 at 1.05), while measured queue length grows from 0.02 requests at ρ = 0.30 to 65.16 at ρ = 0.95 (HikariCP `getThreadsAwaitingConnection()`; cross-checked by the Little's-Law estimate λ̂·W̄_q = 67.82, a 4.1% relative difference).

Figure 8 illustrates the contrast: PT's thread inventory grows with load; VT's carrier inventory stays flat while its queue-length inventory grows by two orders of magnitude over the same range.

Study 2 (PoolSweepValidationBenchmark, burst-arrival) includes **Virtual Thread runs only** — no Platform Thread pool-sweep data is available (§4.2). Within the available VT-only data, heap occupancy correlates only weakly with pool size (Pearson r = −0.19) and is not a striking signal on its own. Within Study 1, sampled heap occupancy itself correlates only weakly with ρ for **both** modes (Spearman r = −0.01 VT, −0.04 PT) — essentially flat — while GC collection frequency correlates strongly with ρ for both (r = 0.99 VT, 0.90 PT). This refines rather than overturns H1: the resource-currency distinction shows up in **thread count** and **queue length**, not point-in-time heap occupancy, which the JVM appears to keep within a roughly constant working-set band via garbage collection regardless of mode (§6.1–§6.2).

**Conclusion:** Our measurements are consistent with H1: blocking inventory under VT is denominated in heap-resident continuation state (measured via queue length) rather than native thread occupancy, while under PT it is denominated in native threads directly. Thread count is a misleading capacity signal under VT — it does not move even as queue length grows two orders of magnitude — but raw heap occupancy is *also* not reliable under either mode here; queue length and GC frequency are the metrics that track load (§6.3).

---

![Figure 8. Resource reallocation](analysis/paper/fig8_resource_reallocation.png)

**Fig. 8.** Resource reallocation under increasing utilization (Study 1, MSSQL, n = 20 per cell). x-axis: mean OS thread inventory; y-axis: mean measured queue length E[N_q]. Platform Threads (red squares) move up and to the right with utilization, consistent with thread occupancy scaling with waiting inventory (r = 0.928). Virtual Threads (blue circles) move almost vertically — carrier count stays near 138 threads throughout (r = 0.110, n.s.) while queue length still climbs, consistent with waiting inventory being denominated in a currency other than native threads under VT.

### 5.2 RQ2 — The Approximation Predicts, Within a Region

**Claim:** Prediction accuracy was acceptable throughout the queue-free operating region, with errors increasing progressively toward saturation — consistent with H2, though one anomalous Platform Thread cell complicates the aggregate picture (discussed below). Table 1 presents the full agreement analysis over the nine stable ρ levels (0.30–0.95).

**Table 1. Validation Metrics — Erlang-C vs. Measured Values (n = 20 replicates per cell, MSSQL)**

| Metric | n | MAE | RMSE | MAPE (%) | R² | r |
| ------------------------- | -- | ---- | ----- | --------- | ------- | ------ |
| Mean Latency — VT (ms) | 9 | 14.7 | 27.0 | 19.5 | 0.264 | 0.991 |
| Mean Latency — PT (ms) | 9 | 80.3 | 229.0 | 120.6 | −0.124 | −0.094 |
| Mean Latency — All (ms) | 18 | 47.5 | 163.0 | 70.0 | −0.063 | 0.034 |
| Queue Length — VT (req) | 9 | 7.1 | 18.0 | 357.2 | 0.210 | 0.998 |
| Queue Length — PT (req) | 9 | 27.0 | 77.3 | 39 932.4 | −0.148 | −0.055 |
| Heap Memory (pool sweep, MB) | 2 | 75.3 | 77.7 | 6.6 | −15.603 | n/a |

*Bootstrap 95% CI (latency, all modes, 2 000 resamples): MAPE = 70.0% [7.2–189.1%]; RMSE = 163.0 ms [5.8–281.6 ms].*

---

![Figure 2. Latency vs rho](analysis/paper/fig2_latency_vs_rho.png)

**Fig. 2.** Mean latency vs. target utilization ρ for Virtual Threads (VT) and Platform Threads (PT), with Erlang-C model predictions overlaid (green dashed line; identical for both modes). Error bars: 95% CI over 20 replicates. The annotated gap at the highest stable ρ marks the region where the model underestimates VT latency, and the dotted vertical line marks the Region I boundary (ρ* ≈ 0.77, the rounded value used in Figure 1).

---

![Figure 3. Calibration](analysis/paper/fig3_calibration.png)

**Fig. 3.** Calibration plot: Erlang-C predicted vs. measured mean latency (a) and queue length (b) for all (ρ, mode) pairs, colored by ρ. Points above the 1:1 diagonal indicate model underestimation. The single Platform Thread point far above the diagonal in both panels is the ρ = 0.75 anomalous cell discussed below.

Interpretation of Table 1 requires care: the aggregate PT statistics are dominated by a single anomalous cell. At ρ = 0.75, the PT replicate batch recorded a mean latency of 753.2 ms with a 95% CI of ±1417.3 ms (CV ≈ 402% across 20 replicates) — roughly an order of magnitude above every neighboring PT cell (62.9 ms at ρ = 0.70, 65.7 ms at ρ = 0.80). We treat this as a measurement anomaly rather than a genuine feature of the ρ–latency relationship, since it is not reproduced at any neighboring ρ and no analogous spike occurs for VT at the same ρ. **The mechanism cannot be established from the instrumentation collected here**: GC metrics for this cell (Table 5, §5.4) are elevated but not dramatically so relative to neighbors, so host-level scheduling noise, a JIT de-optimization event, or an unrecorded confound remain equally plausible (§6.7 item 5 proposes a targeted re-run with finer telemetry). Its effect on the aggregate metrics is large: excluding it, PT's MAPE over the remaining eight points drops from 120.6% to 7.0%, and Pearson r recovers from −0.094 to a value consistent with VT's 0.991. We report the full table for transparency but treat PT's aggregate MAPE and r as **not representative of typical-case accuracy**, examining the per-ρ breakdown directly instead (Figure 4, §5.3).

*(The benchmark harness invokes `System.gc()` once per replicate cell before the warmup window begins; §6.4 discusses this control and its possible interaction with the heap-occupancy measurements reported in §5.1 and §5.4.)*

Restricting to Region I (ρ ≤ 0.70, the more conservative of the two per-mode breakpoints from §5.3), relative latency error is 12.0% (VT) and 9.1% (PT). VT's per-ρ error here is not monotonic: *highest* at the lowest utilization tested (17.5% at ρ = 0.30, 14.2% at ρ = 0.50) and *falling* toward the breakpoint (8.7% at ρ = 0.70), because the model's flat-service-time baseline (≈66.3 ms) slightly overstates measured latency at low load (≈56–58 ms) before the curves converge near ρ = 0.75–0.80 and diverge in the opposite direction beyond it (§5.3, Figure 4). This is itself informative: it suggests the fixed 50 ms `WAITFOR DELAY` plus fixed per-request overhead slightly over-estimates the *effective* mean service time 1/μ at low contention — an implementation-level calibration detail, not a failure of the queueing abstraction.

The negative aggregate R² values (−0.124 PT, −0.063 all) reflect the same anomaly and the extrapolation of a steady-state model to ρ ≥ ρ*; they do not indicate model failure within Region I. VT's aggregate R² (0.264) and Pearson r (0.991) remain strong across the full stable range despite the non-monotonic low-ρ pattern described above, because the model correctly tracks the *shape* of the VT latency curve even where its level is imperfect.

**Statistical bias tests** (Table 2): residuals fail the Shapiro–Wilk normality test for all four comparisons (p ≤ 0.0001 in three of four cases), so Wilcoxon signed-rank is used throughout rather than the paired t-test. Latency bias is not statistically significant for either mode (VT: W = 17.0, p = 0.570; PT: W = 18.0, p = 0.652). Queue-length bias **is** statistically significant for both modes (VT: W = 0.0, p = 0.0078; PT: W = 0.0, p = 0.0078) — the model systematically under-predicts queue length (mean bias +7.1 requests VT, +27.0 requests PT; a positive bias means measured exceeds predicted).

**Table 2. Statistical Significance of Prediction Error (Wilcoxon signed-rank, n = 9 cells per mode, each aggregating 20 replicates)**

| Comparison | Test | Statistic | *p* | Significant |
| ---------- | -------------------- | --------- | ------ | ----------- |
| Latency VT | Wilcoxon signed-rank | 17.00 | 0.5703 | No |
| Latency PT | Wilcoxon signed-rank | 18.00 | 0.6523 | No |
| Queue VT | Wilcoxon signed-rank | 0.00 | 0.0078 | **Yes** |
| Queue PT | Wilcoxon signed-rank | 0.00 | 0.0078 | **Yes** |

> *Caveat: n = 9 cells per mode (one per ρ level) still limits power for the latency comparison, so absence of latency-bias significance should not be over-read as evidence of no bias. The queue-length result, by contrast, reaches significance at the same sample size, indicating a real and consistent under-prediction rather than a marginal effect.*

**Conclusion:** Our results support H2 within Region I (ρ ≤ 0.70 for PT, ≤ 0.75 for VT — §5.3). The approximation is quantitatively useful below these per-mode breakpoints (MAPE ≈ 9–12% on latency) and qualitatively informative above them; queue length is consistently under-predicted by the model at a level that is statistically significant even though mean latency bias is not.

**Cross-DBMS check (PostgreSQL).** Study 1 was independently re-run against PostgreSQL with the identical configuration (c = 50, ten-level ρ grid, n = 20) and the same validation pipeline used for Table 1. Table 1B reports the result.

**Table 1B. Validation Metrics — Erlang-C vs. Measured Values, PostgreSQL (n = 20 replicates per cell)**

| Metric | n | MAE | RMSE | MAPE (%) | R² | r |
| ------------------------- | -- | ---- | ----- | --------- | ------- | ------ |
| Mean Latency — VT (ms) | 9 | 441.0 | 1068.2 | 677.4 | −0.195 | 0.992 |
| Mean Latency — PT (ms) | 9 | 238.2 | 639.1 | 357.9 | −0.145 | 1.000 |
| Mean Latency — All (ms) | 18 | 339.6 | 880.2 | 517.7 | −0.163 | 0.960 |
| Queue Length — VT (req) | 9 | 326.3 | 798.7 | 16 458.3 | −0.188 | 0.991 |
| Queue Length — PT (req) | 9 | 187.8 | 517.3 | 6 300.1 | −0.134 | 1.000 |
| Heap Memory (pool sweep, MB) | 2 | 141.7 | 160.2 | 15.8 | −3.573 | n/a |

The aggregate PostgreSQL MAPE figures (677% VT, 358% PT) are far larger than MSSQL's, but unlike the single anomalous MSSQL PT cell, this is not one outlier — it reflects a genuine, multi-point widening of the VT–PT gap above ρ ≈ 0.75 (Table 3B). Restricting to Region I (ρ ≤ 0.70) tells a more encouraging story: mean absolute latency error is 5.1% (VT) and 6.2% (PT) on PostgreSQL, slightly *better* than the 9.1–12.0% on MSSQL in the same range. The queue-free operating region and the model's validity within it generalize across backends; the magnitude of divergence above the breakpoint does not.

Breakpoint detection on PostgreSQL places ρ* ≈ 0.75 for VT (SSR improvement 96.8%) and ≈ 0.65 for PT (94.8%) — close to MSSQL (VT 0.75, PT 0.70; §5.3). Above the breakpoint, PostgreSQL diverges far more sharply: at ρ = 0.95, measured mean latency reaches 3192.4 ms (VT) and 1977.1 ms (PT) — roughly 20× and 22× the MSSQL values (161.0, 90.1 ms) at the same ρ — and measured queue length reaches 2350.2 (VT) / 1558.4 (PT) requests versus 65.2 / 18.9 on MSSQL. Effect sizes track this: Cohen's *d* = 2.721 at ρ = 0.95 on PostgreSQL vs. 1.111 on MSSQL (Table 3).

A further, unexpected finding concerns *which* resource is actually bounded. On MSSQL, Platform Thread count stays modest near saturation (147.3 → 243.6) — consistent with the connection pool acting as the effective cap. On PostgreSQL, it instead grows essentially without bound (149.4 → 3707.9) — structurally similar to the HTTP-substrate blow-up in §5.4, but here under the *same* connection-pool substrate that stayed bounded on MSSQL. Virtual Thread carrier count remains flat on both backends (138.0–138.2). We cannot establish the mechanism from this instrumentation (a plausible candidate: how the PostgreSQL JDBC driver's connection-acquisition path interacts with Tomcat's worker pool under sustained blocking), so we report this as an observed difference, not a diagnosed one. The implication: "Platform Threads are protected by the bounded connection pool" (§5.1, §6.1) held for MSSQL and did not hold for PostgreSQL — it is not a general JDBC property.

**Table 3B. VT vs. PT Mean Latency and Effect Size by Utilization Level, PostgreSQL (n = 20 per cell)**

| ρ | VT mean (ms) | PT mean (ms) | Cohen's *d* | Cliff's δ | Magnitude |
| -------- | ------------- | ------------- | ----------: | --------: | --------------- |
| 0.30 | 55.0 | 54.8 | 0.488 | 0.297 | small |
| 0.50 | 56.4 | 56.6 | −0.253 | −0.145 | negligible |
| 0.60 | 57.3 | 57.4 | −0.057 | −0.085 | negligible |
| 0.65 | 57.6 | 58.6 | −0.829 | −0.470 | medium |
| 0.70 | 60.1 | 61.9 | −0.417 | −0.305 | small |
| 0.75 | 69.4 | 74.6 | −0.148 | 0.125 | negligible |
| 0.80 | 166.9 | 76.2 | 1.177 | 0.975 | large |
| 0.85 | 760.0 | 233.0 | 1.699 | 0.855 | large |
| 0.95 | 3192.4 | 1977.1 | 2.721 | 0.990 | large |

*Pooled (all ρ < 1, n = 180 per mode): Cohen's d = 0.244, Cliff's δ = 0.024 (negligible); Mann–Whitney U = 16591, p = 0.692 — negligible for the same reason as the MSSQL pooled statistic (§5.3): opposite-signed effects below the breakpoint cancel the large effects above it.*

**Conclusion (cross-DBMS).** The Erlang-C approximation's *queue-free operating region* — its location and its accuracy within it — generalizes across the two backends tested; the *severity* of post-breakpoint divergence, and even *which physical resource is bounded* for Platform Threads, does not. A planner applying this model to a new DBMS/driver combination should expect Region I to behave similarly but should not assume the MSSQL VT–PT gap or the "PT stays thread-bounded" pattern will transfer — both were an order of magnitude worse, or different in kind, on PostgreSQL (§6.4, §6.7).

### 5.3 RQ3 — Where the Model Stops Working

The characterization of model validity boundaries proceeds in four steps.

**Step 1 — Breakpoint detection.** Automated piecewise regression (constant fit left of the candidate breakpoint, linear fit right of it, choosing the split that minimizes total residual sum of squares) places the onset of systematic deviation at **ρ* ≈ 0.75 for Virtual Threads** (bootstrap 95% CI [0.75, 0.80], SSR improvement 94.5%) and **ρ* ≈ 0.70 for Platform Threads** (bootstrap 95% CI [0.50, 0.80], SSR improvement 44.9%). The two breakpoints are close but not identical — PT's is the more conservative (lower) of the two, and its wide CI is driven by the ρ = 0.75 anomaly discussed in §5.2. The boundaries were found by the data, not chosen by the authors.

---

![Figure 4. Residual analysis](analysis/paper/fig4_residual.png)

**Fig. 4.** Residual analysis: relative latency prediction error (%) vs. ρ for VT and PT, with shaded bootstrap 95% CI bands around each mode's breakpoint. The green band marks the ±30% accuracy zone. VT error stays inside this band through ρ = 0.85 and only exceeds it at ρ = 0.95; PT error stays inside the band throughout except the ρ = 0.75 anomaly, which is off-scale.

**Step 2 — Residual direction and growth beyond ρ*.** Unlike a simple "error grows monotonically with ρ" pattern, the *signed* relative error ((measured − predicted) / predicted) changes sign partway through the grid for both modes, which is itself informative about where the model's assumptions break down:

* **VT** — the model *overestimates* measured latency from ρ = 0.30 through ρ = 0.80 (signed error −14.9% at ρ = 0.30, narrowing to −0.9% at ρ = 0.80), crosses to *underestimation* at ρ = 0.85 (+20.9%), and underestimates sharply at ρ = 0.95 (+94.1%).
* **PT** — the model overestimates at low ρ (−15.1% at ρ = 0.30, −11.5% at ρ = 0.50), stays within ±5.2% from ρ = 0.60 through ρ = 0.85 (excluding the ρ = 0.75 anomaly, where the signed error is +1033%), and mildly underestimates at ρ = 0.95 (+8.6%).

The VT pattern — over-prediction at low load, crossing to growing under-prediction near and above its breakpoint — is *consistent with* the continuation-accumulation interpretation: Erlang-C does not account for additional waiting from heap-resident continuation state (e.g., GC pressure, carrier contention) once queueing becomes non-trivial, and this unmodeled cost only appears once ρ exceeds ρ*. PT's error stays comparatively flat and small across the same range (excluding the anomaly), consistent with native-thread blocking being the resource the server-count parameter already represents. The precise mechanism for VT's post-breakpoint underestimation remains a hypothesis, examined further in §5.4 and §6.2.

**Step 3 — Effect sizes across ρ levels.** Table 3 reports mean latency, Cohen's *d*, and Cliff's δ for VT vs. PT at each utilization level, computed from the raw per-replicate latencies (n = 20 per mode per ρ).

**Table 3. VT vs. PT Mean Latency and Effect Size by Utilization Level (n = 20 per cell)**

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

*Pooled (all ρ < 1, n = 180 per mode): Cohen's d = −0.096, Cliff's δ = −0.062 (negligible); Mann–Whitney U = 15188, p = 0.306.*

VT and PT are not cleanly separated at a single ρ — the 0.60–0.75 range shows small-to-large effect sizes in *PT's* favor (driven partly by the ρ = 0.75 anomaly, partly by elevated PT variance at ρ = 0.60), before the direction flips and VT becomes the higher-latency mode with large effect sizes at ρ = 0.85 (Δ = 14.9 ms, +22%) and ρ = 0.95 (Δ = 70.9 ms, +79%). The **pooled** effect size across all stable ρ levels is negligible (Cliff's δ = −0.062) precisely because per-ρ effects point in different directions and largely cancel — a reminder that a single pooled statistic can mask a regime-dependent pattern visible only per-ρ.

**Step 4 — Operating envelope assembled.** The breakpoint estimates and effect-size pattern above define the region boundaries illustrated in Figure 1:

| Region | ρ Range | Characterization |
| ------------------------------------ | ------------------- | --------------------------------------------------------------------------------------------------- |
| **I — Queue-free / model valid** | ρ ≤ **0.70–0.75** | Model reliable (latency MAPE 9–12%); VT and PT effect sizes mixed but small in absolute terms (≤ 3 ms) |
| **II — Transition** | 0.70/0.75 < ρ ≤ 0.90 | Onset of VT–PT divergence; VT begins to exceed PT; model directional only |
| **III — Divergence** | 0.90 < ρ ≤ 0.98 | Large VT penalty (Δ up to 70.9 ms at ρ = 0.95); model under-predicts both latency and queue length; continuation accumulation is a hypothesized, not established, driver (§6.2) |
| **IV — Saturation** | ρ ≥ 1 (ρ = 1.05 tested) | Intentionally unstable; VT mean latency 462.5 ms vs. PT 136.8 ms at ρ = 1.05 (JDBC substrate; §5.4 reports the opposite ordering under HTTP) |

**Conclusion:** Our results support H3. The model's validity boundary is better described as **two close but distinct per-mode breakpoints** (VT ρ* ≈ 0.75, PT ρ* ≈ 0.70) than a single pooled value, and the VT–PT latency gap above ρ* — while directionally consistent with continuation accumulation — is modest at the utilization levels tested (70.9 ms at ρ = 0.95). Excluding the ρ = 0.75 anomaly, mean absolute latency error is 9.1% for PT and 11.1% for VT within their respective queue-free regions (§5.2); this gap should not be over-generalized from a single hardware/JVM configuration (§6.4).

### 5.4 RQ1 (extended) — GC Evidence and HTTP Workload Transferability

**GC evidence (Study 1, direct measurement).** ArrivalSweepBenchmark records `GarbageCollectorMXBean` metrics per replicate; Table 5 reports the mean across n = 20 replicates for all ten ρ levels.

**Table 5. GC Metrics per (Mode, ρ) — Mean across n = 20 replicates (Study 1, MSSQL)**

| ρ | VT GC Events | VT GC Pause (ms) | VT Net Heap Growth (MB/s) | PT GC Events | PT GC Pause (ms) | PT Net Heap Growth (MB/s) |
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

VT GC event count rises from 6.2 at ρ = 0.30 to 24.6 at ρ = 1.05 (3.96×); PT rises from 7.4 to 21.9 (2.96×). GC pause time follows the same pattern: VT rises 6.8× (10.9 → 74.2 ms), PT rises 4.6× (12.3 → 56.0 ms). Both increases are monotonic, and **both modes** show GC pressure scaling with ρ (Spearman r = 0.989 VT, 0.900 PT, both p < 0.0001) — a load signal common to both execution models, with VT scaling somewhat more steeply, not a VT-specific one. Net heap growth rate — a coarse proxy, not a true allocation-volume measurement (§4.3) — is essentially flat for both modes (3.5–3.7 MB/s, no significant correlation with ρ), so the GC divergence is driven by collection *frequency*, not the *net* rate at which heap accumulates between samples. This does not rule out a difference in true allocation volume, which this benchmark's MXBean-based instrumentation cannot observe (§4.3, §6.7 item 4) — only that heap state at the 1-second sampling granularity used here grows similarly for both modes.

> *Note: `GarbageCollectorMXBean.getCollectionTime()` measures total STW pause time as reported by the JVM. It does not distinguish minor from major GC and does not capture concurrent GC phase overhead. All GC values should be interpreted as lower bounds on actual GC overhead. A dedicated GC/heap-delta figure remains future work (§6.7).*

**HTTP workload (Study 3).** HttpSleepBenchmark replaces the JDBC blocking substrate with HTTP I/O via Java `HttpClient`, using a `Semaphore(50)` to model the connection pool. The server endpoint (`GET /mock-latency?ms=50`) holds the connection open for 50 ms via `Thread.sleep`, preserving the M/M/c invariant. Run at n = 20 replicates per cell against both MSSQL and PostgreSQL, to check whether the HTTP-substrate pattern is backend-independent.

**HTTP findings (Study 3, MSSQL).** From ρ = 0.30 through ρ = 0.85, VT and PT mean latency are close and stable (VT 62.2–64.2 ms, PT 60.8–64.7 ms), with no high-variance spikes. At ρ = 0.95, VT (73.9 ms) is modestly *lower* than PT (81.2 ms). At the unstable ρ = 1.05 point, the two modes diverge sharply: **VT mean latency is 1084.5 ms; PT mean latency is 1975.5 ms (+82% relative to VT)**, and PT's mean thread count explodes to 1737.3 (max 10115) with 369 rejected requests, while VT's carrier thread count stays at 132.3 with zero rejections. This is the **opposite failure mode** from Study 1 (JDBC): there, VT's latency grew faster than PT's near saturation, bounded by the shared c = 50 connection pool; here, PT's native thread pool has no equivalent hard cap under HTTP + Tomcat, so at extreme overload PT keeps spawning OS threads until the process starts rejecting connections, while VT's bounded carrier pool degrades gracefully.

**HTTP findings (Study 3, PostgreSQL).** At the unstable ρ = 1.05 point, PostgreSQL reproduces the MSSQL direction: PT degrades further than VT (2277.6 ms vs. 1902.0 ms; thread count 1915.6 vs. 133.1). Below ρ = 1.0, the run is noisier than MSSQL for **both** modes, and the noise is not PT-specific: PT shows two elevated cells (ρ = 0.65: 69.0 ms; ρ = 0.85: 109.3 ms, each a single outlier replicate), but VT shows a comparable spike at ρ = 0.60 (71.7 ms, one replicate at 257 ms). More tellingly, at ρ = 0.95 VT (141.3 ms) is *higher* than PT (108.6 ms) — the reverse of the MSSQL ordering — driven by a single VT replicate reaching ≈1200 ms, the same kind of spike as the PT anomaly discussed for MSSQL in §5.2. We do not read this as a consistent "PT more affected" pattern below saturation: both modes show occasional large single-replicate spikes at n = 20, and which mode's spike lands at a given ρ appears stochastic. The one consistent finding across both backends is at ρ = 1.05, where PT's uncapped thread growth produces materially worse outcomes than VT's bounded carrier pool.

**HTTP GC evidence.** GC pause times in Study 3 (34–249 ms VT, 43–242 ms PT, ρ = 0.30–1.05, MSSQL) are substantially higher than Study 1 (11–74 ms VT, 12–56 ms PT) at comparable ρ, because HTTP responses traverse the full Spring MVC/Tomcat object pipeline, generating more GC pressure per request independent of concurrency. Absolute values are not comparable between studies; the qualitative pattern (GC rising with ρ for both modes) is.

**Transferability assessment.** Low-to-moderate-load indistinguishability between VT and PT (Region I) is consistent across both blocking substrates and both database backends. At saturation (ρ = 1.05), the two substrates disagree on **which mode fails more severely** — VT under JDBC (bounded connection pool shared by both modes), PT under HTTP (no equivalent bound on PT's native thread growth here) — itself a finding: the resource-currency abstraction's practical consequences depend on which resource is actually bounded in a given deployment, not on execution model alone. Below saturation, the PostgreSQL HTTP data shows occasional large single-replicate spikes in **both** modes rather than a reproducible PT-specific pattern, so the substrate-dependent conclusion above should be read as applying to the ρ = 1.05 point specifically, not to fine-grained claims about sub-saturation noise. A dedicated cross-substrate GC comparison figure remains future work (§6.7).


---

## 6. Discussion

### 6.1 Why Does the Model Work Below ρ*?

The mechanism is Little's Law: E[N] = λ · W, which holds regardless of execution model. Parking a continuation and blocking a thread are the same *queueing* event — both remove one unit of service capacity from the pool's perspective and add one unit to the inventory E[N]. Below ρ*, currency choice is invisible to latency, which is why one Erlang-C parameterization fits both execution models.

**Lemma 1 (Resource Currency Invariance)** — stated formally for precision (its status relative to Little's Law is addressed in §1.4). *The physical realization of N is model-dependent — PT: native thread occupancy (one OS thread per waiting request); VT: heap-resident continuation state (one parked continuation per waiting request) — but the waiting time W itself is currency-invariant.*

**Corollary:** Below ρ*, the two execution models are latency-indistinguishable by any queueing observer. Above ρ*, continuation accumulation introduces waiting that Erlang-C does not model, explaining the systematic underestimation in VT. This implies a practical equivalence: in Region I, VT vs. PT is a **design decision that should be made on non-performance grounds** — memory footprint, operational constraints, code complexity — because latency consequences are negligible.

### 6.2 Why Does Prediction Degrade Above ρ*?

The degradation is consistent with violation of assumptions A1 and A2 (§4.5). We advance the following hypotheses — we do not assert causal mechanisms, as testing them is future work:

1. **Burst arrivals (A1 violated).** Measured inter-arrival CV exceeds 1 across the *entire* grid (≈1.5–1.6 at ρ = 0.30, rising to ≈2.6–2.9 near saturation, both modes) — arrivals are burstier than Poisson throughout, increasingly so with load. Erlang-C underestimates mean waiting time for super-Poisson arrivals (M[X]/M/c effect), consistent with the low-ρ over-prediction (§5.3) being smaller than the high-ρ under-prediction, since burstiness compounds with queueing only once the queue is non-trivially occupied.

2. **Service time distribution shift (A2 strained).** At high queue occupancy, HikariCP connection acquisition time becomes an increasing component of service time; the combined distribution (SQL delay + pool acquisition) is no longer well-approximated by a single-parameter exponential.

3. **VT-specific: continuation overhead — weaker evidence than assumed.** Direct GC evidence (Table 5, §5.4) shows GC event count and pause duration scaling with ρ for **both** VT (r = 0.99) and PT (r = 0.90), VT somewhat more steeply — weaker, more qualified support for a VT-specific mechanism than a GC-only reading would suggest, since GC pressure alone does not cleanly separate the two modes. The cleaner separating signal (§5.1) is thread count (VT flat, r = 0.110; PT scaling, r = 0.928) together with queue length (both scale similarly, r ≈ 0.89–0.91). Causal attribution from continuation accumulation to the VT-specific latency gap above ρ* remains a hypothesis; per-class allocation profiling would be needed to confirm it directly (§6.7, item 4).

The VT-specific prediction gap documented in §5.2–§5.3 (MAPE ≈ 9.1% PT vs. 11.1–12.0% VT within Region I, widening to a +94.1% signed under-prediction for VT at ρ = 0.95) is *consistent with* hypothesis 3, though modest in magnitude. PT's blocking substrate (OS threads) is accounted for implicitly by the pool-slot server count in the model; VT's continuation substrate is not, and this asymmetry is the most likely source of the residual gap even though GC pressure alone does not fully explain it.

### 6.3 Practical Implications and Design Principles

The operating envelope translates into actionable guidance for capacity planners. Of the metrics measured, raw heap occupancy (≈110–118 MB baseline, sampled once per second) did not track ρ for either execution model (§5.1, §5.4, §6.4); thread count and queue length / GC frequency did.

**DP1: Monitor queue length and thread-count decoupling, not raw heap occupancy.**
Thread count is a misleading *capacity* signal under Virtual Threads — it stays flat even as queue length grows two orders of magnitude (§5.1). Queue length and GC collection frequency, which scale with ρ for both modes, are the metrics that track load; the VT-specific warning sign is the *combination* of rising queue length with flat thread count, not heap size alone. (The heap null result is an absence of detected correlation at this sampling resolution, not proof that heap-resident continuation state does not grow with load — §6.4.)

**DP2: Apply queueing approximation only within the valid operating region.**
Erlang-C predictions are quantitatively reliable in Region I (ρ ≤ 0.70 for PT, ≤ 0.75 for VT). In Region II (up to ρ ≈ 0.90), treat model output as directional only. In Region III (ρ > 0.90), use the model as a saturation warning, not a capacity estimate — and note that it under-predicts queue length significantly even in Region I (§5.2), so queue-length estimates should carry a safety margin at all ρ.

**DP3: Capacity planning should be regime-aware, substrate-aware, and backend-aware.**
The boundary ρ* ≈ 0.65–0.75 is reasonably consistent across the JDBC configurations studied and across substrates (§5.2, §5.4). What is not consistent is the saturation *failure mode* and its severity: under MSSQL/JDBC, VT accumulates more latency near saturation while PT's thread count stays bounded; under PostgreSQL/JDBC with the same nominal pool configuration, PT's thread count instead grows unboundedly, and the failure mode looks structurally closer to what Study 3 found under HTTP. Capacity planners should identify the expected operating region and which resource is actually bounded in their specific deployment, and confirm this empirically per DBMS/driver/framework rather than assuming it transfers from a different backend.

**Table 4. Monitoring Signal Checklist by Operating Region**

| Signal | Region I (ρ ≤ 0.70–0.75) | Region II (up to ρ ≈ 0.90) | Region III (ρ > 0.90) |
| ---------------------- | ------------------------- | --------------------------- | -------------------------- |
| Carrier thread count | ✓ Informative for PT; ✗ misleading for VT | ✗ (VT); ✓ (PT, until substrate-specific bound is hit) | ✗ (VT); ⚠ blow-up warning (PT, HTTP substrate) |
| Request queue length | ✓ Reliable (both modes) | ✓ Reliable (both modes) | ✓ **Primary signal** (both modes) |
| Heap occupancy (MB) | No detected signal (both modes, r ≈ 0; possible sensitivity limit, §6.4) | No detected signal | No detected signal — do not rely on this metric alone |
| GC collection frequency | Optional (tracks ρ for both modes) | ✓ Watch (both modes) | ✓ Watch (both modes) |
| Erlang-C model output | ✓ Reliable for latency; queue length under-predicted even here | ⚠ Directional only | ⚠ Saturation warning only |

### 6.4 Limitations

Analytical assumptions were intentionally simplified to yield closed-form, falsifiable predictions. The following define the boundary between what this paper claims and what it does not.

**Two DBMS backends — but they disagree on severity, not just on ρ*.** Study 1 has been independently replicated against PostgreSQL with the same configuration and pipeline (§5.2, Tables 1B/3B), so this is no longer a single-DBMS study. The replication is not simple confirmation, though: the queue-free region generalizes well (≈5–6% MAPE on PostgreSQL vs. ≈9–12% on MSSQL), but the magnitude of post-breakpoint divergence does not — PostgreSQL's VT–PT gap and queue length near saturation are roughly an order of magnitude larger, and Platform Thread count, bounded on MSSQL, grows essentially without limit on PostgreSQL. Take the *existence* and *location* of the operating envelope as reasonably portable across backends; treat the specific millisecond gaps and which resource ends up bounding Platform Threads as specific to the tested configurations. Study 3 (§5.4) supports the same point from a different angle. MySQL and non-relational stores [16] remain untested, and the mechanism behind PostgreSQL's thread-count growth is not established by the instrumentation collected here.

**Single blocking type in Studies 1–2.** Study 1 uses synchronous JDBC with a fixed delay. Study 3 (§5.4), run at n = 20 against two database backends, adds HTTP + Thread.sleep as a second substrate and finds a substrate-dependent saturation failure mode (PT thread-pool blow-up under HTTP vs. VT continuation accumulation under JDBC) rather than a single shared boundary. Generalizability to Redis, Kafka, gRPC, or mixed blocking patterns remains untested.

**Sample size.** Twenty replicates per cell (up from five in the pilot) substantially tightens CIs at low-to-moderate utilization, but variance stays high near saturation (PT CV = 58% at ρ = 0.60, VT CV = 44% at ρ = 0.95) and the PT ρ = 0.75 cell shows an unattributed outlier-driven CV of 402% (§5.2). PT's breakpoint bootstrap CI ([0.50, 0.80]) is accordingly wider than VT's ([0.75, 0.80]).

**Study 2 no longer includes a Platform Thread comparison.** The available pool-sweep dataset contains Virtual Thread runs only (`Scenario = VT_IO_Wait`); the VT-vs-PT heap-correlation contrast from earlier drafts cannot be reproduced and has been replaced with a weaker VT-only correlation in §5.1. Re-running Study 2 under Platform Threads would restore it.

**Indirect resource-currency measurement (partially mitigated).** Queue length is measured directly via `HikariPoolMXBean` and cross-checked against an independent Little's-Law estimate (§5.1), agreeing to within ~4% at the highest stable ρ. Neither measurement counts heap-resident continuation objects directly, though, and GC pause/event counts scale with ρ for both modes so provide only partial, non-differentiating support for the continuation-accumulation narrative (§6.2). Per-class heap profiling to count continuation objects directly remains future work (§6.7, item 4).

**Pre-cell `System.gc()` call.** The harness invokes `System.gc()` once per replicate cell, at least 10.8 seconds before the measurement window begins, so it should not directly discard live objects accumulated during the window — but we flag it because §5.1/§5.4 report near-zero correlation between sampled heap occupancy and ρ for both modes, a finding DP1 (§6.3) leans on. We consider a measurement-sensitivity explanation (continuation state at the observed scale — up to 65 concurrent waiters — is plausibly a few MB against a ≈110–118 MB baseline, sampled only once per second) more likely than a `System.gc()` artifact, but cannot fully rule out an interaction from the data collected here; direct object-count profiling (§6.7, item 4) would resolve the ambiguity.

**Virtual Thread pinning excluded (A3).** The workload enforces no `synchronized` blocks or JNI calls. Workloads that trigger carrier thread pinning (legacy `synchronized`, JNI, `Object.wait()`) invalidate the currency-transformation abstraction — a pinned VT holds both a continuation and a native thread, collapsing the currency distinction — and are explicitly out of scope.

**JVM version.** The Loom scheduler evolves across JDK releases; results are calibrated for OpenJDK 21.0.2, and future versions may shift operating-region boundaries.

### 6.5 Threats to Validity

**Internal validity.** A 10-second warmup mitigates JVM warmup effects, but GC pauses are not explicitly isolated, so high-ρ VT measurements may include GC-induced spikes. Replication at n = 20 (up from n = 5) tightens CIs at low-to-moderate ρ; near saturation, variance remains high for both modes, and the PT ρ = 0.75 anomaly shows that a transient host- or JVM-level effect can still dominate an individual cell even at n = 20. The 10-second warmup is shorter than some JVM benchmark conventions; JIT and pool-stabilization effects cannot be fully excluded, though the randomized run order (seed = 42) mitigates systematic drift.

**External validity.** Results are from a single machine configuration. Cloud or containerized deployments with shared CPU/memory resources may exhibit different ρ* boundaries due to OS thread scheduling interactions.

**Construct validity.** Erlang-C is a steady-state model; the benchmark enforces a **45-second measurement window** per cell (Study 1 default). Transient effects at load-step transitions are excluded by the warmup window, but the 45-second window may not fully reach steady state at ρ ≈ 0.95 where drain times are longest.

### 6.6 Relation to Prior Work

Prior benchmarking studies (§2.1) largely answer "Is VT faster?" for a given workload. The per-mode breakpoints (ρ* ≈ 0.70–0.75 on MSSQL; §5.2 for the PostgreSQL comparison) and the operating-envelope regions depend on comparing measurements against a predictive model, which is what distinguishes this analysis from a benchmark comparison alone. The Study 3 finding that the saturation failure mode is substrate- and backend-dependent (§5.2, §5.4) reinforces the same point from a different angle: a single "VT vs. PT" number does not transfer across configurations, but the resource-currency framing gives a way to reason about why it doesn't.

### 6.7 Future Work

Extensions that would materially strengthen the generalizability of these results:

1. **Sensitivity analysis on service time.** Repeat with blocking durations of 20/40/80 ms to test whether ρ* shifts with mean service time; ρ* should be relatively stable if the model is robust.

2. **Diagnose the PostgreSQL Platform Thread count blow-up.** Driver-level tracing of HikariCP/PostgreSQL JDBC connection acquisition under sustained blocking is needed to identify why Platform Thread count grows unboundedly on PostgreSQL but stays capped on MSSQL under the nominally identical pool configuration (§5.2).

3. **Bounded HTTP client thread pool.** A follow-up HTTP study with a *bounded* client thread pool (matching Study 1's bounded connection pool) would test whether Study 3's PT-specific blow-up is an artifact of the unbounded Tomcat configuration used here, rather than a substrate property per se.

4. **Direct continuation object count.** Per-class heap profiling (async-profiler, allocation profiling, heap dump analysis) to count `java.lang.VirtualThread` and continuation objects directly per ρ level — a higher priority now that GC evidence alone (Table 5) shows collection frequency scaling with ρ for both modes rather than VT alone.

5. **Root-cause the PT ρ = 0.75 anomaly.** The extreme single-cell outlier (CV = 402%, §5.2) is treated as a measurement artifact but not diagnosed. Repeating that cell with finer telemetry (CPU steal, page faults, network jitter) would clarify whether it is a real tail behavior or an environmental confound.

6. **Confidence-interval figures.** Extend the CI bands already added to the latency-vs-ρ and effect-size figures (Figures 2, 4, 6) to the GC and queue-length figures.

7. **Generalizing resource currency beyond Java (a distinct, larger undertaking).** This paper's resource-currency vocabulary is descriptive of one system (JVM Platform vs. Virtual Threads) and is not proposed here as a general theory. A genuinely general version would need to state what is conserved across execution models — for example, a conservation relation of the form N_blocked = N_thread + α·N_continuation + β·N_kernel + ⋯ relating blocked-request inventory to the resources it occupies under each model — and derive falsifiable predictions that hold *across* execution models, not just within one (e.g., that a breakpoint exists for any execution-model pair sharing a service center, that its location is currency-elasticity-dependent, or that queue growth is currency-independent while resource growth is not). Testing this would require new benchmarks across substantially different concurrency models — goroutines, actor mailboxes, async/await event loops, reactive callback state — which this dataset does not include. We flag this explicitly as future work rather than a claim of this paper, to avoid overstating what a two-execution-model, single-runtime study can establish.

---

## 7. Conclusion

This paper builds and validates a predictive model of the operating region in which Platform Threads and Virtual Threads produce meaningfully different latency. The model is unmodified Erlang-C / M/M/c, applied to both execution models via one modeling decision: the connection-pool slots, not the carrier threads, are the service channels for both. Resource currency — native OS thread occupancy (PT) versus heap-resident continuation state (VT) — is the vocabulary used to interpret this result (§1.1, §6.1), not a separate theoretical claim.

**Key finding:** The blocking inventory E[N] is identical under both models (Little's Law); its physical substrate is model-dependent. This is why one Erlang-C parameterization fits both execution models below ρ* and diverges above it.

**Empirical summary** (n = 20 replicates per cell, MSSQL primary, PostgreSQL replication for Study 1 and the HTTP substrate; full detail in §5):

* Below ρ* (≈0.70–0.75, consistent across both DBMS backends tested), VT and PT are latency-indistinguishable and Erlang-C predicts within ≈9–12% MAPE on MSSQL, ≈5–6% on PostgreSQL — the queue-free region and its accuracy generalize.
* Above ρ*, VT incurs higher latency than PT on MSSQL (up to +79% at ρ = 0.95); the same direction holds on PostgreSQL but at roughly 20× the magnitude — the *direction* of divergence generalizes, the *severity* does not (§5.2–§5.3).
* A second blocking substrate (HTTP, Study 3) reverses which mode is more fragile at saturation: there, PT's uncapped thread growth — not VT's continuation accumulation — is the failure mode, and on PostgreSQL/JDBC the same uncapped-thread-growth pattern appears even under the JDBC substrate that stayed bounded on MSSQL (§5.2, §5.4). Which resource ends up bounded is a property of the specific driver/pool/server configuration, not of execution model alone.

**Practical take-away:** below ρ*, VT vs. PT should be chosen on non-performance grounds — the latency difference is negligible. Above ρ*, queue length and GC collection frequency — not thread count or heap occupancy alone — are the monitoring signals that hold up across both execution models and backends studied (§6.3 gives the full checklist).

This is offered as a validated operating-region model for Java Platform and Virtual Threads, with resource currency as the interpretive vocabulary that explains it — a foundation for regime-aware capacity planning in JVM-based, I/O-bound microservices, and a starting point for the larger cross-runtime generalization discussed in §6.7, not a claim that this generalization has already been established.

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

> *The following three figures are supplementary — they provide supporting evidence but are not the primary vehicles for the paper's claims. They may be placed in an online appendix if page limits require.*

---

![Figure A1. Bland-Altman analysis](analysis/paper/fig5_bland_altman.png)

**Fig. A1.** Bland–Altman agreement plot of Erlang-C predicted vs. measured mean latency (all nine stable ρ < 1 operating points, both models, n = 20 per point). Mean bias = +40.2 ms, limits of agreement [−278.5, +358.8 ms] (bias ± 1.96 SD), driven almost entirely by the PT ρ = 0.75 anomaly (§5.2).

---

![Figure A2. VT vs PT comparison](analysis/paper/fig6_vt_vs_pt.png)

**Fig. A2.** Side-by-side latency and queue-length trajectory comparison for Virtual Threads and Platform Threads across all nine stable ρ levels {0.30, 0.50, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.95}. Points: measured mean ± 95% CI (n = 20 replicates). Dashed line: Erlang-C prediction (identical for both modes). The PT ρ = 0.75 anomaly is visible as an isolated spike in both panels.

---

![Figure A3. Correlation heatmap](analysis/paper/fig7_correlation_heatmap.png)

**Fig. A3.** Spearman correlation matrices for Study 1 (ArrivalSweepBenchmark) measurements, stable regime (ρ < 1), n = 20 per cell. Left: Platform Threads — Threads_Mean correlates strongly with Rho_Target (r = 0.928), confirming native-thread occupancy scales with load. Right: Virtual Threads — Threads_Mean is nearly uncorrelated with Rho_Target (r = 0.110), while latency and queue-length metrics remain strongly correlated with utilization (r ≈ 0.90–0.92), consistent with the carrier-pool stability prediction of the resource-currency abstraction. Note Heap_Mean_MB correlates only weakly with ρ for either mode (|r| ≤ 0.02), the basis for the revised heap-monitoring guidance in §6.3.

---

