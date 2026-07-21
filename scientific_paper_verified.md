# Modeling and Predicting Latency Within the Operating Regions of Java Virtual Threads: A Queueing Analysis of Blocking Resource Transformation

*Empirical validation on a Spring Boot 3.4.2 / Java 21 microservice against two containerized DBMS backends (Microsoft SQL Server 2022 and PostgreSQL 15), executed on a single Intel Core i7 (8th generation) workstation with 16 GB RAM.*

---

## Abstract

Modern server applications spend most of a request's lifetime waiting on I/O, not computing. Java Virtual Threads (Project Loom) are widely reported to improve scalability under blocking workloads, but the mechanism behind this improvement is rarely formalized. We model Platform Threads (PT) and Virtual Threads (VT) as two execution strategies over a shared M/M/c service center that differ only in the *resource currency* of blocking: native OS thread occupancy for PT versus heap-resident continuation state for VT. We validate this abstraction with three benchmark studies on a rice-store inventory microservice: (1) a burst-arrival pool sweep that demonstrates why an uncontrolled arrival process breaks Erlang-C validation outright; (2) an open-loop Poisson arrival sweep across ten utilization levels (ρ = 0.30–1.05) that is the primary validation study; and (3) a second blocking substrate (HTTP) used to test whether the findings generalize beyond JDBC. Both DBMS backends ran as separate Docker containers on the same host. In the queue-free operating region (ρ ≤ 0.70) the Erlang-C approximation predicts mean latency with 5.1–10.6% MAPE across both database engines. Beyond that region, prediction error grows and, critically, the two execution models diverge in opposite directions depending on the blocking substrate: under JDBC/connection-pool blocking, Virtual Threads show *higher* mean latency than Platform Threads as utilization approaches saturation (Cohen's d up to 2.72 on PostgreSQL at ρ = 0.95), while Platform Threads instead exhibit rare but catastrophic native-thread-exhaustion collapses (single replicates reaching latencies 200× the median). Under HTTP blocking, this ordering partially reverses. We report all effect sizes, breakpoints, and errors as measured — not as originally hypothesized — and discuss why the "VT is strictly better under load" narrative common in informal benchmarks does not hold uniformly in our data.

---

## 1. Introduction

### 1.1 The problem is waiting, not computing

Modern server applications are increasingly constrained by waiting rather than computation. I/O-bound microservices spend most of a request's lifetime blocked on a downstream resource — here, a relational database connection. What differs between Platform Threads and Virtual Threads is not *whether* requests wait, but *what the system pays while they wait*.

**Definition 1 (Resource Currency).** *The resource currency of blocking is the physical resource whose occupancy is proportional to the inventory of waiting requests under a given execution model. Under Platform Threads, that resource is a native OS thread (one thread held per waiting request, subject to the OS thread limit and Tomcat's configured maximum). Under Virtual Threads, that resource is heap-resident continuation state (one parked continuation per waiting request; the carrier thread is released back to the scheduler).*

The *inventory* of waiting requests is, by Little's Law, identical across execution models for a given arrival rate and wait time. The *currency* in which that inventory is denominated is not. This is the object of study in this paper.

### 1.2 The gap

Existing empirical studies (surveyed in §2) report that Virtual Threads improve throughput or reduce thread-management overhead under blocking workloads. Few provide an analytical model that predicts *when* the two execution models will differ and by how much, and fewer still test whether such a model's predictions survive contact with more than one database engine or more than one blocking substrate.

### 1.3 Research questions and hypotheses

- **RQ1.** How does the physical resource consumed by blocked requests differ between PT and VT as utilization increases?
- **RQ2.** Can an M/M/c / Erlang-C queueing approximation predict mean latency and queue length for both execution models, and over what utilization range?
- **RQ3.** Where does the approximation stop working, and does the qualitative behavior of PT and VT in that region differ — including across two independent DBMS backends and two independent blocking substrates?

Hypotheses (stated before measurement, per the benchmark's built-in prediction-then-measure design — see §4):
- **H1** — Blocking changes the resource representation of waiting differently under each execution model (native thread vs. heap continuation).
- **H2** — Erlang-C predicts mean latency and queue length within a moderate-utilization operating region.
- **H3** — Prediction error increases as utilization exceeds a data-detected breakpoint ρ\*, and the *direction* of the PT/VT latency gap is not assumed in advance.

### 1.4 Contributions

1. **A currency-transformation abstraction** for PT vs. VT blocking, validated against directly observed carrier-thread counts and Hikari-pending-connection counts (not merely inferred).
2. **Three-study empirical design** — a burst-arrival study that documents a genuine methodological failure mode of naively applied Erlang-C, an open-loop Poisson study that is the primary validation instrument, and a second-blocking-substrate (HTTP) study for generalizability — all executed identically against two independently containerized DBMS engines.
3. **A data-driven operating envelope** with breakpoints located by piecewise regression rather than chosen by the authors, reported separately for VT, PT, MSSQL, and PostgreSQL because they do not coincide.
4. **An honest account of a non-obvious reversal**: near saturation, Virtual Threads do not uniformly outperform Platform Threads in this workload. The direction depends on the blocking substrate. We report this because it is what the data shows, and because it refines rather than undermines the resource-currency thesis: the *mechanism* (currency transformation) is well supported; the *latency consequence* is regime- and substrate-dependent, not a foregone conclusion in VT's favor.

---

## 2. Related Work

**Empirical studies.** Several recent papers benchmark Java Virtual Threads against platform threads or reactive programming under blocking I/O workloads [1]–[8], generally reporting throughput or resource-usage improvements for VT. These studies report *that* differences exist but rarely provide an analytical account of *why*, or of the boundary conditions under which the difference disappears or reverses.

**Analytical / queueing studies.** M/M/c and Erlang-C models have long been applied to classical thread-pool and connection-pool sizing problems [11]–[13], and more recently to asynchronous and edge-gateway pipelines [10], [12]. This body of work has not been applied to the specific currency-transforming behavior introduced by continuation-based concurrency (Project Loom / virtual threads).

**Database and infrastructure comparisons.** A separate literature compares DBMS engines and query-optimization strategies on performance grounds [14]–[17], [21], generally without connecting those results to JVM thread-scheduling behavior. Our design borrows from this literature only insofar as it motivates running the same JVM-level benchmark against two DBMS engines rather than one.

**Gap and positioning.** No prior work we are aware of formalizes the resource-currency transformation, locates its operating-region boundaries by data-driven breakpoint detection, and tests whether the resulting model and its qualitative conclusions survive a change of DBMS engine and a change of blocking substrate. We evaluate all three together and report where the model's practical implications change and where they do not.

---

## 3. Analytical Framework

### 3.1 Shared queueing model

Both execution models are treated as clients of the same M/M/c service center: c servers (the JDBC connection pool, size fixed per experiment), Poisson arrivals at rate λ, exponential service time with rate μ (one connection = one server). Traffic intensity ρ = λ/(cμ). The Erlang-C probability that an arriving request must wait is

C(c, ρ) = [ (cρ)^c / c! · 1/(1−ρ) ] / [ Σ_{k=0}^{c−1} (cρ)^k/k! + (cρ)^c / c! · 1/(1−ρ) ]

computed in log-space (as implemented in `ArrivalSweepBenchmark.erlangC` / `PoolSweepValidationBenchmark.erlangC`) to avoid factorial overflow at pool sizes up to c = 200. From C(c, ρ) the model derives the expected queue length E[N_q] = C(c,ρ)·ρ/(1−ρ), expected wait E[W_q] = C(c,ρ) / (cμ(1−ρ)), and expected mean response time E[W] = E[W_q] + 1/μ.

### 3.2 Currency-specific quantities

Both models share the same E[N_q] under Little's Law — the queueing *event* is identical. What differs is where that inventory is physically realized:

- **PT**: a waiting request holds a native OS thread. Carrier/worker thread count should track E[N_q] approximately 1:1 as ρ increases, up to the OS/Tomcat thread ceiling.
- **VT**: a waiting request parks a continuation; the carrier thread pool (default sized to available cores) is released and should remain flat regardless of ρ. The Hikari "threads awaiting connection" counter is used as the ground-truth, directly measured proxy for continuation-level queueing (not an inferred quantity), since HikariCP itself is unaware of which execution model is calling it.

### 3.3 Heap-cost parameter α

For the pool-sweep study, an effective parameter α (MB of heap per queued continuation) is estimated from training pools as α = (heap_measured − heap_baseline) / N_q,Hikari, and used to predict heap usage at held-out test pools: heap_predicted = heap_baseline + ᾱ·E[N_q]. α is explicitly an *effective* parameter (it absorbs JDBC objects, ResultSet references, and GC survivor overhead co-located with parked continuations), not a measurement of raw continuation size.

---

## 4. Methodology

### 4.1 Materials

| Component | Configuration |
|---|---|
| Host hardware | Intel Core i7, 8th generation; 16 GB RAM |
| Application | Spring Boot 3.4.2, Java 21 (Project Loom), Tomcat `server.tomcat.threads.max=10000` |
| Connection pool | HikariCP; pool size varied per experiment (10–200) |
| Database A | Microsoft SQL Server 2022 (`mcr.microsoft.com/mssql/server:2022-latest`), Docker container, 4 GB memory limit/reservation |
| Database B | PostgreSQL 15 (`postgres:15-alpine`), Docker container, 4 GB memory limit/reservation |
| Dataset | Synthetic "rice store" inventory schema: 50 stores, 2,000 products, 50,000 customers, 1,000,000 invoices, generated via batched JDBC inserts (`FakeData` / `FakeDataPostgres`) |
| Blocking substrate 1 | `WAITFOR DELAY` (SQL Server) / `pg_sleep()` (PostgreSQL) inside a JDBC statement — the connection is held in the database for the full simulated 50 ms service time |
| Blocking substrate 2 | `Thread.sleep(50 ms)` inside a Spring REST endpoint (`GET /research/delay`), consuming an HTTP connection slot rather than a JDBC connection |

Both database containers ran on the same physical host as the JVM application; no network hop separated the JVM from either database, isolating the comparison to execution-model behavior rather than network variance.

### 4.2 Three benchmark studies

**Study 1 — Pool Sweep Validation Benchmark (burst arrival, preliminary/diagnostic).** Training pools {10, 25, 50}, held-out test pools {100, 200}; concurrency 4,000, 40,000 requests per iteration, 8 iterations (2 warm-up + 6 measured). Arrival rate λ was measured as (requests submitted)/(submission-window duration) — a *burst* approximation, not a controlled Poisson process. This is documented in the benchmark's own source comments as a known limitation later corrected by Study 2.

**Study 2 — Arrival Sweep Benchmark (open-loop Poisson, primary validation study).** Pool fixed at c = 50. A dedicated dispatcher thread generates exponential inter-arrival gaps with absolute-deadline scheduling, directly controlling ρ = λ/(cμ) rather than inferring it after the fact. Ten target utilization levels: ρ ∈ {0.30, 0.50, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.95, 1.05}, with the fine-grained levels {0.60, 0.65, 0.75, 0.80} added around the expected breakpoint to tighten later breakpoint estimation. Twenty replicates per (execution model × ρ) cell, run in randomized order (fixed seed) to decorrelate environmental drift from operating point; 10 s warm-up + 30 s measurement window per replicate. μ was estimated from a low-load baseline (pool = 200, concurrency = 10, ρ ≈ 0) *before* the sweep, and predictions for all ten ρ levels were written to disk before any sweep measurement was taken (predict-then-measure). ρ = 1.05 is intentionally unstable (M/M/c has no steady state there) and is reported as boundary-crossing evidence only, excluded from all "stable-region" statistics below.

**Study 3 — HTTP Sleep Benchmark (second blocking substrate).** Replaces the JDBC/WAITFOR blocking source with an HTTP call to a local endpoint that blocks on `Thread.sleep`; a client-side `Semaphore(50)` plays the role of "c servers." Same open-loop Poisson dispatcher design as Study 2, same ρ grid, 20 replicates per cell. This tests whether conclusions from Study 2 are an artifact of JDBC/connection-pool blocking specifically.

### 4.3 Measurement and instrumentation

Per replicate: dispatched/completed/rejected counts, mean and P99 latency, HikariCP "threads awaiting connection" sampled every 200 ms (ground truth for VT-side queueing, since Hikari's pending-thread counter is currency-agnostic), heap usage and live thread count sampled every second via `MemoryMXBean`/`ThreadMXBean`, and GC collection counts/pause time via `GarbageCollectorMXBean` deltas across the measurement window. Little's Law internal-consistency check: N_q ≈ λ·(W − 1/μ), computed independently of the Hikari-observed value as a cross-check.

### 4.4 Statistical methods

Mean, standard deviation, and 95% CI (t-distribution) per (mode, ρ) cell from n = 20 replicates. Validation metrics (MAE, RMSE, MAPE, R², Pearson r) between frozen model predictions and measured means. Bootstrap 95% CIs (2,000 resamples) for MAPE and RMSE. Shapiro–Wilk normality test on paired residuals, followed by paired t-test (normal) or Wilcoxon signed-rank test (non-normal) for systematic bias, α = 0.05. Cohen's d (pooled-variance) and Cliff's δ (non-parametric dominance) for VT-vs-PT effect size at each ρ. Operating-region breakpoints located by piecewise regression: candidate breakpoints scanned over all interior ρ values; each candidate fits a constant to the left segment and a linear model to the right segment of the latency-error-vs-ρ curve; the breakpoint minimizing total sum-of-squared residuals is reported, together with the % SSR improvement over a single-segment (no-breakpoint) fit.

### 4.5 Model assumptions

| Label | Assumption | Status observed in this study |
|---|---|---|
| A1 | Poisson arrivals (inter-arrival CV = 1) | Measured inter-arrival CV ranged 1.5–2.9 across all ρ levels and both DBMS — consistently burstier than ideal Poisson, attributable to OS timer granularity (~1–15 ms) in the dispatcher's park/spin loop. A1 is strained throughout, not only at high ρ. |
| A2 | Exponential service time | Approximately holds at low ρ; service-time distribution widens near saturation (see P99/mean divergence in §5). |
| A3 | No carrier-thread pinning | Enforced by workload design (no `synchronized`/JNI/`Object.wait()` in the blocking path). |
| A4 | Single homogeneous blocking source per study | Enforced within each study; Study 2 uses JDBC only, Study 3 uses HTTP only. |
| A5 | Open-loop workload (arrival rate externally controlled) | Enforced by dispatcher design in Studies 2–3; Study 1 is closed/burst by construction, which is precisely the condition shown to break the model (§5). |

### 4.6 Conceptual framework

```
Blocking request
      │
      ▼
Inventory E[N] (currency-invariant; Little's Law)
      │
      ├── Platform Thread ──► Currency: native OS thread occupancy
      │                                (one thread held per waiting request)
      │
      └── Virtual Thread ───► Currency: heap-resident continuation
                                        (carrier thread released; continuation parked)
                                              │
                                              ▼
                                   Observable consequence (measured, not assumed):
                                   direction and magnitude of the PT/VT latency gap
                                   depend on ρ, on the DBMS engine, and on the
                                   blocking substrate (JDBC vs. HTTP) — see §5.3.
```

---

## 5. Results

All figures below are computed directly from the CSV outputs in `data/` (Studies 1–3) using the analysis pipeline in `analysis/`; every number is reproducible from those files.

### 5.0 Study 1 — why an uncontrolled (burst) arrival process breaks Erlang-C validation outright

Before the open-loop Poisson design of Study 2 existed, Study 1 measured λ as (requests submitted)/(submission-window duration). Because 4,000 concurrent VT tasks are submitted essentially instantaneously, this produced measured arrival rates around 4.0–5.8×10⁵ rps for every pool size tested (10–200), forcing ρ ≈ 0.9999 and the model into the "Resource-Exhaustion" region regardless of the actual pool size. The heap-usage side of the model still degraded gracefully — predicted vs. measured heap error was 9.0% (pool 100) / 5.1% (pool 200) on SQL Server and 19.5% (pool 100) / 7.0% (pool 200) on PostgreSQL, with the effective heap-per-continuation parameter α ≈ 0.108–0.114 MB (SQL Server, deviation < 15% across training pools 10/25/50 — "approximately linear") and α ≈ 0.074–0.105 MB (PostgreSQL, 25% deviation at pool 50 — "moderate non-linearity"). But the *latency* side failed outright: predicted vs. measured mean-latency error was 200.7% (pool 100) / 53.2% (pool 200) on SQL Server and 298.3% (pool 100) / 104.3% (pool 200) on PostgreSQL — every cell reported `Validation_Pass = FAIL`. A cross-validation pass with flipped training/test pool assignment made this worse, not better: extrapolating to pool = 10 produced latency errors of 2,324% (SQL Server) and 3,178% (PostgreSQL). This is not a marginal calibration issue; it is the direct, measured consequence of feeding a burst (non-Poisson) arrival process into a model whose Erlang-C term assumes memoryless arrivals (assumption A1, §4.5) — and it is the reason Study 2's open-loop Poisson dispatcher exists. We report Study 1's numbers here precisely because a paper that only reported Study 2 would be presenting a model that "happens" to validate without showing the failure mode its own design had to correct.

**Extended α sensitivity across all five measured pool sizes.** The benchmark's own linearity assessment (§3.3) is computed only from the three training pools (10, 25, 50). Using the `Est_NQueue` and measured heap columns available for all five pool sizes in `POOL_SWEEP_SUMMARY` (including the two held-out test pools, 100 and 200), we compute an implied α at every pool size:

| Pool size | SQL Server α (MB/continuation) | PostgreSQL α (MB/continuation) |
|---|---|---|
| 10 | 0.114 | 0.074 |
| 25 | 0.104 | 0.074 |
| 50 | 0.108 | 0.105 |
| 100 | 0.099 | 0.106 |
| 200 | 0.103 | 0.091 |

On SQL Server, α stays within 0.099–0.114 MB across all five pool sizes (≈14% spread), consistent with the benchmark's own "approximately linear" verdict. On PostgreSQL, α is noticeably lower and flatter at the two smallest pools (0.074) before jumping to 0.09–0.106 at pools 50–200 (≈30% spread), consistent with the "moderate non-linearity" verdict already recorded in the raw data.

This extended view must be read with one important caveat we flag explicitly: because Study 1's burst arrival rate forces ρ ≈ 0.9999 at *every* pool size tested (§5.0 above), the `Est_NQueue` denominator used in this calculation is itself nearly constant across all five pool sizes (9,981–9,995) rather than a value that genuinely scales with pool size the way it would under controlled ρ. The apparent "linearity" of α in this table is therefore driven mainly by how flat measured heap usage is across pool sizes (1,049–1,197 MB, SQL Server; 792–1,113 MB, PostgreSQL), not by a clean, independently-varying measurement of queue depth. We report the table because it is the most complete view the existing data supports, but we do not treat it as a stronger linearity validation than the original three-point estimate — a genuine sensitivity analysis (e.g., pool sizes swept under Study 2's controlled-ρ design rather than Study 1's burst design) is listed as future work (§6.6).

### 5.1 RQ1 — Resource consumption diverges by currency, confirmed by direct measurement

Across the full ρ grid in Study 2, the VT carrier-thread pool is essentially flat: mean live thread count stays at 138.0–138.2 for every ρ from 0.30 to 1.05, on **both** SQL Server and PostgreSQL. Platform Threads instead grow with ρ: mean thread count rises from ≈147 (ρ = 0.30) to 243.6 (ρ = 1.05, MSSQL) and from ≈149 to 3,707.9 (ρ = 1.05, PostgreSQL) — a qualitatively different growth pattern between the two DBMS backends, discussed in §5.3.

Meanwhile, the Hikari-observed queue depth (ground truth for the "currency" absorbed by VT) grows from 0.02 to 310.3 (MSSQL, ρ 0.30→1.05) and from 0.00 to 4,343.0 (PostgreSQL, ρ 0.30→1.05), while PT's own Hikari queue depth grows on a similar or smaller scale (0.00→58.2 MSSQL; 0.00→3,480.2 PostgreSQL) because PT resolves queueing pressure by acquiring more native threads rather than by letting Hikari-visible queue depth grow as much.

These are measured HikariCP and JVM MXBean quantities, not inferred continuation counts. They support **H1**: the physical resource that grows with load is different under each execution model — native thread inventory for PT, Hikari-visible connection-wait inventory (the observable correlate of parked continuations) for VT — on both database engines tested.

### 5.2 RQ2 — Erlang-C predicts well in a queue-free region, with DBMS-dependent accuracy

Restricting to the "stable" utilization range (ρ ≤ 0.95, i.e. excluding the intentionally unstable ρ = 1.05 point) and further restricting to the queue-free region ρ ≤ 0.70:

| DBMS | Mode | MAPE (ρ ≤ 0.70) | Max per-cell error (ρ ≤ 0.70) |
|---|---|---|---|
| SQL Server | VT | 10.6% | 17.5% |
| SQL Server | PT | 8.2% | 17.8% |
| PostgreSQL | VT | 5.1% | 9.2% |
| PostgreSQL | PT | 6.2% | 11.9% |

Over the *full* stable range (ρ up to 0.95), accuracy diverges sharply by DBMS and mode:

| DBMS | Mode | MAE (ms) | RMSE (ms) | MAPE | R² | Pearson r (p) |
|---|---|---|---|---|---|---|
| SQL Server | VT | 14.7 | 27.0 | 19.5% | 0.264 | 0.991 (p = 2.0×10⁻⁷) |
| SQL Server | PT | 80.3 | 229.0 | 120.6% | −0.124 | −0.094 (p = 0.81, n.s.) |
| PostgreSQL | VT | 441.0 | 1068.2 | 677.4% | −0.195 | 0.992 (p = 1.5×10⁻⁷) |
| PostgreSQL | PT | 238.2 | 639.1 | 357.9% | −0.145 | 0.9997 (p = 1.5×10⁻¹²) |

The SQL Server PT row is dominated by a single catastrophic replicate at ρ = 0.75 (see §5.3), which destroys its correlation despite an otherwise well-behaved cell. The PostgreSQL rows show very high linear correlation (the model still tracks the *trend* correctly) but very large magnitude error at high ρ, because measured latency there (up to 3,192 ms at ρ = 0.95, VT) vastly exceeds what a single fixed-μ Erlang-C prediction can produce.

Paired significance tests (Wilcoxon signed-rank, since Shapiro–Wilk rejected normality of residuals in all cases) on the ≤0.95 stable range: on SQL Server, mean latency bias is **not** statistically significant for either mode (VT: p = 0.570, mean bias +5.8 ms; PT: p = 0.652, mean bias +74.5 ms, inflated by the single storm replicate). On PostgreSQL, mean latency bias **is** statistically significant for both modes (VT: p = 0.0039, mean bias +441.0 ms; PT: p = 0.0039, mean bias +238.2 ms) — i.e., on PostgreSQL the model's under-prediction at high ρ is systematic, not noise. Queue-length (Hikari N_q) predictions are significantly biased on both DBMS and both modes (p ≤ 0.008 in every case): the model systematically under-predicts observed queueing, consistent with inter-arrival CV > 1 (A1 strained) inflating real queue buildup beyond the Poisson-arrival prediction.

**Answer to RQ2:** the approximation is quantitatively useful in the queue-free region (ρ ≤ 0.70) on both DBMS, with single-digit-to-high-teens percentage error. Outside that region its reliability depends on the DBMS engine: on SQL Server the *mean* bias across replicates remains statistically indistinguishable from zero (masked by high replicate variance, not by accuracy), while on PostgreSQL the model's under-prediction becomes systematic and statistically significant.

### 5.3 RQ3 — Where the model breaks, and what breaks differently by mode, DBMS, and substrate

**Breakpoint detection** (piecewise regression on latency-error-vs-ρ, Study 2, JDBC substrate):

| DBMS | Mode | Breakpoint ρ\* | SSR improvement |
|---|---|---|---|
| SQL Server | VT | 0.75 | 94.5% |
| SQL Server | PT | 0.70 | 44.9% (weak fit — corrupted by the ρ=0.75 storm replicate) |
| PostgreSQL | VT | 0.75 | 96.8% |
| PostgreSQL | PT | 0.65 | 94.8% |

The breakpoints cluster in the 0.65–0.75 range but do **not** coincide exactly across mode or DBMS, unlike a single uniform ρ\* ≈ 0.70 figure. We report the detected values rather than a rounded common figure.

**Effect size, VT vs. PT mean latency, by ρ (Study 2, JDBC substrate; Cohen's d, positive = VT slower):**

| ρ | SQL Server d | SQL Server VT/PT mean (ms) | PostgreSQL d | PostgreSQL VT/PT mean (ms) |
|---|---|---|---|---|
| 0.30 | 0.25 | 56.4 / 56.3 | 0.49 | 55.0 / 54.8 |
| 0.50 | −0.30 | 58.0 / 58.7 | −0.25 | 56.4 / 56.6 |
| 0.70 | −1.20 | 61.0 / 62.9 | −0.42 | 60.1 / 61.9 |
| 0.80 | 0.16 | 66.2 / 65.7 | 1.18 | 166.9 / 76.2 |
| 0.85 | 1.33 | 82.2 / 67.2 | 1.70 | 760.0 / 233.0 |
| 0.95 | 1.11 | 161.0 / 90.1 | 2.72 | 3192.4 / 1977.1 |

At low-to-moderate ρ the two execution models are effectively indistinguishable (|d| < 0.5), consistent with the currency-invariance argument in §6.1. **Above ρ ≈ 0.80, Virtual Threads show substantially higher mean latency than Platform Threads on both database engines**, with the gap widening dramatically on PostgreSQL (d = 2.72, a 62% higher mean latency for VT at ρ = 0.95). This is the opposite ordering from the "VT is uniformly faster under load" claim common in informal benchmarks, and it is a direct, measured consequence of the resource-currency thesis: near saturation, the heap-resident continuation backlog that VT accumulates imposes real latency cost (GC pressure, allocation overhead) that a bare Erlang-C model does not capture, and in this workload that cost exceeds the cost PT pays for holding more native threads — **up to the point where PT's native-thread strategy fails outright** (below).

**Catastrophic Platform Thread failure modes.** Two distinct failure patterns appear for PT, and they differ by DBMS:

- *SQL Server*: a rare, severe, single-replicate collapse. At ρ = 0.75, 1 of 20 PT replicates recorded mean latency 13,617 ms (vs. a same-cell median of 64.0 ms — a 213× spike), with Hikari-observed queue depth reaching 9,044 and live thread count reaching 9,206, before recovering to normal behavior in the very next replicate. This single event inflates the SQL-Server PT MAPE and destroys its Pearson correlation (§5.2); excluding it, PT tracks the model closely across the rest of the grid.
- *PostgreSQL*: a systematic, gradual thread-count inflation rather than a rare spike. Mean PT thread count rises smoothly from 149 (ρ=0.30) through 1,763 (ρ=0.95) to 3,708 (ρ=1.05, unstable region), i.e. nearly the entire replicate cohort is affected as ρ approaches saturation, not a single outlier replicate.

At the explicitly unstable point ρ = 1.05 (Region IV, reported as boundary-crossing evidence only, not scored PASS/FAIL), the HTTP substrate (Study 3) shows the same qualitative pattern even more starkly: on SQL Server, PT mean thread count reaches 1,737 (max 10,115) with 369 requests rejected by the safety valve, while VT mean thread count stays at 132.3 (max 134) with **zero** rejections.

**Substrate reversal (Study 2 vs. Study 3).** Under HTTP blocking (Study 3), the high-ρ ordering partially reverses relative to JDBC blocking (Study 2): at ρ = 0.95 on SQL Server, VT mean latency (73.9 ms) is *lower* than PT (81.2 ms); at ρ = 1.05, VT (1,084 ms) is dramatically lower than PT (1,975 ms). On PostgreSQL, the picture is mixed — at ρ = 0.85 VT (66.3 ms) beats PT (109.3 ms), but at ρ = 0.95 PT (108.6 ms) beats VT (141.3 ms). **The direction of the PT/VT latency gap near saturation is not a fixed property of the execution model; it depends on which physical resource is the actual bottleneck for the specific blocking substrate** — JDBC connection-pool blocking (Study 2) versus HTTP semaphore-modeled blocking (Study 3).

**Answer to RQ3:** the operating-region boundary is real and data-detected (ρ\* in the 0.65–0.75 range depending on mode/DBMS), but the qualitative story above the boundary is more complex than a single "VT wins" or "PT wins" statement: Platform Threads are the more latency-stable choice in the JDBC/connection-pool substrate up to the point of catastrophic, low-probability (SQL Server) or systematic (PostgreSQL) native-thread exhaustion, while Virtual Threads avoid that specific failure mode entirely (zero rejections observed in every VT cell across all three studies) at the cost of higher mean latency in the pre-collapse regime for this substrate — and the ordering can reverse under a different blocking substrate.

### 5.4 Testing the proposed causal chain (continuation → heap → GC → latency): a correlational check, honestly reported

The resource-currency thesis implies a specific causal chain for VT: parked continuations accumulate on the heap, which increases GC pressure, which in turn inflates latency beyond what Erlang-C predicts. Study 2's raw data includes per-replicate GC event counts and total GC pause time (`GarbageCollectorMXBean` deltas) and a crude allocation-rate proxy, which lets us test — not merely assert — a correlational version of this chain. We report the result as found, including where it does not cleanly support the hypothesis.

Spearman correlations, stable region (ρ < 1.0), computed separately per mode:

| DBMS | Mode | ρ ↔ GC pause | Hikari N_q ↔ GC pause | ρ ↔ Alloc. rate | GC pause ↔ Latency |
|---|---|---|---|---|---|
| SQL Server | VT | r = 0.879 (p < 0.001) | r = 0.800 (p < 0.001) | r = 0.045 (n.s.) | r = 0.891 (p < 0.001) |
| SQL Server | PT | r = 0.811 (p < 0.001) | r = 0.715 (p < 0.001) | r = −0.024 (n.s.) | r = 0.757 (p < 0.001) |
| PostgreSQL | VT | r = 0.844 (p < 0.001) | r = 0.823 (p < 0.001) | r = −0.134 (n.s.) | r = 0.867 (p < 0.001) |
| PostgreSQL | PT | r = 0.355 (p < 0.001) | r = 0.341 (p < 0.001) | r = −0.020 (n.s.) | r = 0.383 (p < 0.001) |

Two observations follow directly from this table, and they point in different directions:

1. **GC pause time correlates with utilization and with latency for both execution models, not only for VT** (SQL Server PT: r = 0.811 and r = 0.757; PostgreSQL PT: r = 0.355 and r = 0.383, weaker but still significant). This means the correlational data **do not, by themselves, isolate a VT-specific GC effect** — rising GC pressure under load is a property of this workload in general, not something unique to continuation accumulation. A causal chain specific to VT's currency requires evidence this correlational analysis cannot supply (see below).
2. The *magnitude* of GC pause at matched high-ρ operating points does differ by mode, and does so differently by DBMS. On PostgreSQL, VT's mean GC pause total is 2.6–2.75× PT's at ρ = 0.95–1.05 (91.2 ms and 126.0 ms for VT vs. 33.1 ms and 48.5 ms for PT). On SQL Server the same comparison shows only a 1.2–1.3× difference (66.7 ms and 74.2 ms for VT vs. 54.2 ms and 56.0 ms for PT) — a much weaker separation.

**Honest interpretation:** the data are consistent with a VT-specific GC-pressure contribution on PostgreSQL at high ρ, but provide only weak, inconsistent support for the same claim on SQL Server. Because allocation rate itself does not correlate with ρ for either mode (the proxy is too coarse, or per-request allocation is genuinely flat and only *volume* of concurrent requests rises), and because GC pause rises with load under Platform Threads too, we cannot claim to have demonstrated the continuation → heap → GC → latency chain from this data. What we can claim is narrower and stated precisely: GC pause time is associated with latency and utilization under both execution models, the association is materially stronger for VT than for PT on PostgreSQL but not clearly so on SQL Server, and a full test of the proposed causal chain would require direct continuation/heap profiling (JFR, async-profiler) rather than MXBean-level aggregate counters, as noted in §6.6.

---

## 6. Discussion

### 6.1 Why the model works below ρ\*

Little's Law holds regardless of currency: parking a continuation and blocking a native thread are the same *queueing* event, so a single Erlang-C parameterization fits both execution models while the system is far from saturation (ρ ≤ ~0.70, where MAPE stays in the single digits to high teens on both DBMS). Below that point, currency choice is largely invisible to mean latency — the two execution models are statistically indistinguishable (Cohen's d < 0.5 for every ρ ≤ 0.70 cell in Study 2).

### 6.2 Why prediction degrades above ρ\*, and why the degradation differs by DBMS

Measured inter-arrival CV of 1.5–2.9 (vs. the CV = 1 assumed by Poisson arrivals, A1) indicates the realized arrival process is burstier than ideal Poisson throughout the grid, not only near saturation; this alone would inflate real queueing above the Erlang-C prediction. Beyond that, §5.4 tested — rather than assumed — whether GC pressure specifically explains VT's additional latency cost near saturation. The honest result is mixed: GC pause time rises with utilization for *both* execution models, so it is not, by itself, evidence of a VT-specific mechanism; the magnitude gap between VT and PT GC pause at high ρ is substantial on PostgreSQL (2.6–2.75×) but weak on SQL Server (1.2–1.3×). We therefore do not claim to have established the continuation → heap → GC → latency chain; we report that it is plausible and partially consistent with the PostgreSQL data, not demonstrated, and that OS-level thread-scheduling/creation contention remains a separate, better-supported explanation for PT's failure mode (§5.3) than for VT's latency cost. Closing this gap requires direct continuation/heap profiling, not aggregate MXBean counters (§6.6).

### 6.3 Practical implications

- **ρ < 0.70**: Erlang-C is a reliable capacity-planning tool on both DBMS tested; VT-vs-PT choice is close to latency-neutral, so choose on other grounds (memory footprint, operational simplicity, code style).
- **0.70 ≤ ρ < 0.85**: treat model output as directional, not exact — expect under-prediction, more severely so on PostgreSQL than on SQL Server in our data.
- **ρ ≥ 0.85, JDBC-style blocking**: expect Virtual Threads to show *higher* mean latency than Platform Threads in steady (non-collapsed) operation, but also expect Platform Threads to carry non-trivial tail risk of a severe, request-rejecting native-thread exhaustion event that Virtual Threads do not exhibit in this data (zero rejections in any VT cell, any study, any DBMS). The right choice at this utilization is therefore a tail-risk-vs-typical-latency trade-off, not a simple "VT is faster."
- **Monitoring**: under Virtual Threads, native carrier-thread count is a poor proxy for backlog (it stays flat by design); Hikari-pending-connection count, heap occupancy, and GC pause frequency are the signals that actually move with load. Under Platform Threads, live thread count is itself the leading indicator of an approaching collapse.

### 6.4 Limitations

Single-host measurement (Intel Core i7, 8th generation, 16 GB RAM) with both DBMS containers competing for the same physical resources as the JVM — cross-host or cross-hardware generalizability is untested. n = 20 replicates per cell still leaves wide confidence intervals at high ρ (coefficient of variation up to several hundred percent in cells affected by storm events, e.g. SQL Server PT at ρ = 0.75). The burst-arrival design (Study 1) is retained in this paper specifically *because* its failure is itself a finding — it should not be read as a validation attempt in its own right. The pinning limitation (A3) is enforced by workload design but not tested under a pinning workload; this remains explicitly out of scope. Two DBMS engines and two blocking substrates were tested, which is broader than most prior single-configuration studies, but both are still relational-database or simple-HTTP workloads; message queues, file I/O, and other blocking substrates remain untested.

### 6.5 Relation to prior work

Prior benchmarking studies largely ask "Is VT faster?" and answer it for a single configuration. By running the identical benchmark harness against two independently containerized DBMS engines and two blocking substrates, and by reporting the resulting breakpoints and effect sizes exactly as measured — including the cases where they contradict the commonly assumed direction — this paper turns "is VT faster" into "under which conditions, and by how much, and with what tail risk," which is the more actionable question for capacity planning.

### 6.6 Future work

1. Repeat Study 2 on a third and fourth DBMS engine (e.g., MySQL, Oracle) to determine whether the PostgreSQL-vs-SQL-Server divergence in §5.2–5.3 is a general pattern or specific to these two engines.
2. Direct heap/GC profiling (JFR) of the continuation backlog itself, rather than the Hikari-pending proxy, to convert the "continuation accumulation" account from inferred to directly observed.
3. Repeat the HTTP substrate study (Study 3) with a larger ρ grid resolution near saturation to locate its own breakpoint with the same rigor as Study 2.
4. Multi-host / networked deployment to separate host-contention effects (both DBMS containers and the JVM shared one 16 GB host in this study) from execution-model effects.
5. Root-cause investigation of the single catastrophic SQL-Server PT replicate at ρ = 0.75 — was it thread-creation contention, OS scheduler interaction, or GC coincidence? — since as a single-replicate event it is currently reported descriptively (n = 1) rather than statistically characterized.

---

## Design Principles

**DP1 — Monitor resource currency, not thread count, under Virtual Threads.** Carrier thread count is flat by construction and is not a backlog signal; Hikari-pending-connection count and heap/GC metrics are.

**DP2 — Apply the Erlang-C approximation only within its validated region.** Reliable (single-digit-to-high-teens % error) at ρ ≤ 0.70 on both DBMS tested here; directional only in 0.70–0.85; a saturation warning, not a capacity estimate, above 0.85.

**DP3 — Do not assume Virtual Threads dominate Platform Threads near saturation.** In this workload, under JDBC-style blocking, VT showed materially higher mean latency than PT above ρ ≈ 0.80 on both DBMS, while PT carried tail risk of catastrophic, request-rejecting collapse that VT did not exhibit. The correct choice is workload- and risk-tolerance-dependent, and should be re-verified per blocking substrate (§5.3 shows the ordering is not the same under HTTP blocking as under JDBC blocking).

---

## Conclusion

Virtual Threads do not eliminate blocking; they change the physical resource in which waiting is denominated, from native OS threads to heap-resident continuations. This transformation is directly observable (flat VT carrier-thread counts vs. growing Hikari-pending and PT thread counts) and is well-approximated by a shared Erlang-C model in a queue-free operating region (ρ ≤ 0.70, 5.1–10.6% MAPE across two independent DBMS engines). Beyond that region, the practical consequence of the transformation is not uniformly favorable to Virtual Threads: in our JDBC-blocking measurements, VT incurred higher mean latency than PT near saturation on both database engines, while PT instead risked rare-to-systematic native-thread exhaustion that VT never exhibited — and this ordering was not the same under an HTTP blocking substrate. Capacity planning under either execution model should therefore be regime-aware, substrate-aware, and should treat "Virtual Threads are faster under load" as a claim to verify per workload rather than a default assumption.

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

## Appendix A — Data provenance

All numbers in §5 are computed directly from the following files in `data/` (no numbers are taken from the earlier narrative draft or from any source other than these CSVs and the summary statistics recomputed from them):

- `POOL_SWEEP_{RAW,SUMMARY,VALIDATION,ALPHA,CROSSVAL}_20260715_162055_MSSQL.csv`, `..._20260715_174241_POSTGRESQL.csv` (Study 1)
- `ARRIVAL_SWEEP_{RAW,SUMMARY,VALIDATION,BOUNDARIES,PREDICTIONS}_20260715_181339_MSSQL.csv`, `..._20260716_105611_POSTGRESQL.csv` (Study 2)
- `HTTP_SWEEP_{RAW,SUMMARY}_20260717_145805_MSSQL.csv`, `..._20260717_193728_POSTGRESQL.csv` (Study 3)

Validation metrics, effect sizes, and breakpoints were recomputed independently for this paper (including the PostgreSQL-side statistics, which the pre-existing `analysis/` pipeline in the repository did not compute) using the same methods implemented in `analysis/02_validation.py` and `analysis/03_statistics.py`, applied symmetrically to both DBMS datasets.
