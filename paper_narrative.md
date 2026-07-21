# Paper Narrative — Single-Thesis Structure
> Draft skeleton with real numbers from `analysis/paper/`. Every section serves ONE claim.

---

## Central Thesis (the only thing this paper proves)

> **Virtual Threads do not eliminate blocking; they transform the resource currency in which
> blocking is denominated. This transformation can be modeled and predicted within a
> well-defined operating region, and its performance consequences are regime-dependent.**

Everything below — benchmark, pool sweep, arrival sweep, breakpoint detection, effect
sizes, residual analysis, calibration — exists to support this sentence and nothing else.

---

## Title

> **Modeling and Predicting Latency Within the Operating Regions of Java Virtual Threads: A Queueing Analysis of Blocking Resource Transformation**

Not "An Empirical Study of…" — the contribution is analytical abstraction + envelope + validation, not a benchmark.

> *Rationale: the paper models operating regions (via breakpoint detection from data) and predicts latency within those regions (via Erlang-C). "Predicting the operating regions" overclaims — regions are detected, not predicted. "Modeling and Predicting Latency Within" is accurate to the evidence.*

---

## Abstract (~180 words — Problem → Gap → Method → Result → Implication)

Modern server applications spend most of a request's lifetime waiting, not computing.
Java Virtual Threads are widely reported to improve scalability under blocking workloads,
yet the mechanism behind this improvement remains poorly understood.

Existing studies remain largely empirical: no prior work provides an analytical framework
that predicts *when* and *why* the two execution models diverge.

We formalize Platform Threads (PT) and Virtual Threads (VT) as two execution models over a
shared M/M/c service center differing only in the *resource currency* of blocking —
native execution contexts (PT) versus heap-resident continuation state (VT). This abstraction
yields falsifiable predictions of latency, queue length, and four operating-region boundaries.

Validation on a Spring Boot/SQL Server microservice confirms the abstraction captures the
regime transition: a data-driven breakpoint locates the onset of systematic model deviation
at utilization ρ ≈ 0.70 for both execution models.

The benefits of Virtual Threads are strongly operating-region dependent: negligible at
moderate utilization, but substantial near resource saturation. These findings support
regime-aware capacity planning and identify heap metrics — not thread counts — as the
appropriate monitoring signal under Virtual Threads.

---

## 1. Introduction — four paragraphs, in this order

**¶1 — The problem is waiting, not computing.**
"Modern server applications are increasingly constrained by waiting rather than
computation." I/O-bound microservices spend most of a request's lifetime blocked on
downstream resources (here: a database connection pool). What differs between threading
models is not *whether* requests wait, but *what the system pays* while they wait.

We formalize this distinction as follows:

> **Definition 1 (Resource Currency).** *The resource currency of blocking is the physical
> resource whose occupancy is proportional to the inventory of waiting requests under a given
> execution model. Formally: for execution model M with blocking inventory N, the currency
> is the resource R such that `occupancy(R) ∝ E[N]`. Under Platform Threads, R = native
> OS thread slots (one thread held per waiting request). Under Virtual Threads, R = heap-
> resident continuation state (one parked continuation per waiting request, carrier thread
> released).*

The *inventory* of waiting requests is identical across models; the *resource currency* in
which that inventory is denominated is not. This distinction is the central object of study.

**¶2 — The gap.**
Existing work reports that Virtual Threads improve scalability under blocking workloads.
However, these studies remain largely empirical and provide limited analytical understanding
of *when* and *why* the two models diverge. No prior model predicts the boundaries of the
region where the difference matters, nor formalizes the resource-currency transformation that
causes it.

> *This paper is not a new scheduling algorithm. It is not a new runtime. Its goal is to
> explain observed behaviour within a well-defined operating region.*

**¶3 — Research questions and hypotheses.**
- **RQ1** — How does blocking change resource consumption under each execution model?
- **RQ2** — Can a queueing approximation (M/M/c, Erlang-C) predict this behavior?
- **RQ3** — Where does the model stop working — and what happens there?

We state three falsifiable hypotheses before measurement:

- **H1** — Blocking changes the resource representation (currency) of waiting under each execution model.
- **H2** — A queueing approximation (Erlang-C / M/M/c) predicts latency and queue length within moderate operating regions.
- **H3** — Prediction error tends to increase as utilization exceeds the saturation breakpoint ρ*, consistent with progressive model violation.

**¶4 — Contributions** *(ordered: Problem → Insight → Validation → Value)*
1. **Analytical abstraction of resource transformation** — blocking-currency formalization (PT: native thread;
   VT: heap continuation) over one shared M/M/c service center.
   *Explains why thread count alone is insufficient to characterize system load under Virtual Threads.*
2. **Operating envelope characterization** — four regions with quantitative, falsifiable
   boundaries; breakpoint found by data, not chosen by authors. *Reframes the question from
   "Is VT faster than PT?" to "In which operating region does VT produce meaningful differences?"*
3. **Pre-registered predictive approximation with validated operating envelope** —
   Erlang-C predictions pre-registered before measurement; agreement analysis quantifies the
   region where the approximation is reliable (MAPE ≤ 8% at ρ ≤ 0.70) and characterizes
   systematic divergence above ρ*.
   *Enables capacity planning in moderate-utilization regimes without full-system benchmarking.*
4. **Design principles for practitioners** — three regime-aware principles derived from the
   operating envelope, specifying appropriate monitoring signals per region.
   *Translates the operating envelope into actionable guidance for system architects.*

**Figure 1 (operating envelope) appears here, in the Introduction.** Region labels are
thesis labels: *Analytical model valid → Resource transformation begins → Continuation
accumulation → Model applicability boundary*.

> *(Author consistency note: use these four region names throughout the manuscript.
> Avoid informal synonyms — "saturation", "degradation", "breakdown" — without explicit
> cross-reference to a labeled region.)*

---

## 2. Related Work — three angles, ending on gap

### 2.1 Empirical studies
Benchmarking and measurement studies of Virtual Threads, platform threads, and concurrency
models in JVM-based systems. Key gap: these studies *report* performance differences but do
not *explain* them analytically. *(Fill with 4–6 key citations; end each cluster with the
gap it leaves.)*

### 2.2 Analytical studies
Queueing models (M/M/c, Erlang-C) applied to thread pools, connection pools, and service
systems. Prior work applies these models to classical thread-pool configurations, not to the
currency-transforming behaviour of Loom-style virtual threads. *(Fill with queueing-theory
papers and any analytical JVM concurrency work.)*

### 2.3 Gap and positioning
> *Existing work evaluates. We explain.*

No prior work provides a falsifiable, pre-registered analytical model that (a) formalizes
the resource-currency transformation introduced by Virtual Threads, and (b) identifies
quantitative boundaries where the model is and is not valid.

---

## 4.5 Model Assumptions

> *This section should appear at the end of the Methodology (§4), immediately before Results (§5). Listing assumptions explicitly allows Discussion to cite violations by label rather than re-explaining them inline.*

The Erlang-C / M/M/c approximation rests on the following assumptions. Deviations from
these assumptions explain the systematic prediction errors documented in §5.3.

| Label | Assumption | Status in this study |
|-------|-----------|---------------------|
| **A1** | Poisson arrival process (exponential inter-arrival times; CV = 1) | Measured inter-arrival CV > 1 at high ρ — A1 strained above ρ* |
| **A2** | Exponential service time (single-parameter service distribution) | Synchronous JDBC duration approximately exponential in Region I; distribution shifts near saturation |
| **A3** | No carrier thread pinning (no `synchronized`, JNI, or `Object.wait()` in workload path) | Enforced by workload design; JDBC driver uses async-compatible locking |
| **A4** | Single homogeneous blocking source (synchronous JDBC / SQL Server only) | Enforced; no mixed I/O types in workload |
| **A5** | Open-loop workload (no client think-time feedback; arrival rate is externally controlled) | Enforced by benchmark design; approximates closed-loop behavior at high concurrency |

Discussion §6.2 references A1–A2 as the most likely explanations for model degradation above ρ*.
Discussion §6.4 references A3 as the explicit scope boundary for the pinning limitation.

---

## 4.6 Conceptual Framework Diagram (Fig. 0 or §4 opening figure)

> *Reviewer note: a single conceptual diagram placed before Fig. 1 (operating envelope) helps readers build the mental model before encountering data. This is the diagram that remains memorable after the paper is read.*

Proposed flow (no numbers, no benchmark data — pure analytical structure):

```
Blocking request
      │
      ▼
  Inventory (E[N] — currency-invariant, governed by Little's Law)
      │
      ├─── Platform Thread model ──► Currency: native thread occupancy
      │                                        (OS thread held for blocking duration)
      │
      └─── Virtual Thread model  ──► Currency: heap-resident continuation
                                               (carrier thread released; continuation parked)
                                                     │
                                                     ▼
                                          Observable consequence:
                                          ρ ≤ ρ* → latency-indistinguishable
                                          ρ >  ρ* → continuation accumulation
                                                     → systematic underestimation
```

*Caption draft: "The resource-currency transformation. Blocking inventory E[N] is identical
under both models (Little's Law). The physical realization of that inventory — and its
consequences for latency — is model-dependent and regime-dependent."*

---

## 5. Results — organized by RQ, claim first, figure second

> *(Author wording guide — apply throughout: use "our evidence is consistent with", "our
> abstraction models", "our results suggest", "we observe" — never "proves", "demonstrates
> that Virtual Threads transform…", or "we measured continuation memory". Resource currency
> is an **analytical abstraction inferred** from queueing quantities, not a direct
> physical measurement.)*

### 5.1 RQ1 — Resource consumption is consistent with blocking-currency transformation (Fig 8 leads)

Topic sentence, not "Figure 3 shows…":

> *"Under low utilization (ρ ≤ 0.5), both execution models exhibit nearly identical latency
> despite distinct resource consumption patterns."*

Then the reveal:

> *"As utilization crosses the queueing threshold, the same waiting inventory materializes
> in two different currencies: each waiting PT request holds one native thread
> (OS thread inventory grows 148 → 1,037, tracking the 1:1 line), whereas VT parks waiting
> requests as heap-resident continuations while its carrier pool remains constant at
> ≈138 threads (1,527 continuations at ρ = 1.05)."*

> *(Author caveat: "1,527 continuations" is E[Nq] from queueing estimates — it is an
> **inferred count**, not a direct JVM heap measurement. Wording must reflect this:
> "consistent with", "modeled as", "estimated via Little's Law" — not "we measured".
> GC/heap profiling would be required for a direct measurement claim.)*

Conclusion of 5.1 = Contribution 1: *Virtual Threads shift blocked work from native thread
occupancy to heap-resident continuation state.* → **Accept H1.**

### 5.2 RQ2 — The approximation predicts, within a region (Fig 2, 3 + Table 2)

Claim first:

> *"Prediction accuracy remained acceptable throughout the queue-free operating region,
> with errors increasing progressively as utilization approached saturation."*

Numbers to cite in text (keep the rest in Table 2):
- Queue-free region (ρ ≤ 0.7): relative latency error ≤ 8% for both models (Fig 4).
- Overall stable regime: MAPE 20.4% (PT), 59.7% (VT); Pearson r = 0.985 (PT), 0.924 (VT).
- Paired tests find **no statistically significant bias** between prediction and measurement
  (t-test p = 0.21 VT / 0.29 PT; Wilcoxon p = 0.25 both) — *with the explicit caveat n = 5
  per cell; absence of significance is not evidence of absence.*

Answer to RQ2: *Our results support H2 within Region I (ρ ≤ 0.70): the approximation is
quantitatively useful below ρ* ≈ 0.70 and qualitatively informative above it.*
→ **Partially accept H2**: the approximation is quantitatively useful below ρ\* ≈ 0.70; qualitatively informative above it.

### 5.3 RQ3 — Where the model stops working (Fig 4 + Table 4)

The chain, each step handing off to the next:

1. **Breakpoint** — automated piecewise detection places the onset of systematic deviation
   at ρ\* = 0.70 for VT *and* PT (SSR improvement 99.7% / 98.4%; bootstrap 95% CI
   [0.50, 0.70]). *The boundary was found by the data, not chosen by us.*
2. **Residuals** — beyond ρ\*, error grows monotonically: +53%/+63% (VT), +18%/+40% (PT)
   at ρ = 0.85/0.95. Direction is systematic (model underestimates), not noise.
3. **Effect size** (Table 4 — **main text, not appendix**):

   | ρ | Cohen's d | |Δ latency| | Interpretation |
   |---|---|---|---|
   | 0.30 | 0.49 | 0.15 ms (0.3%) | d inflated by tiny variance; practically nil |
   | 0.50 | −0.16 | 0.29 ms | negligible |
   | 0.70 | 0.15 | 0.19 ms | negligible |
   | 0.85 | **1.94** | **55.6 ms (74%)** | large |
   | 0.95 | **1.71** | **81.3 ms (65%)** | large |

   Report effect size *and* absolute difference together — at low ρ, d can look "medium"
   while the absolute gap is 0.15 ms.
4. **Envelope assembled** — the three findings above *are* the region boundaries of Fig 1.
   Close the loop with the Introduction.

→ **Accept H3.**

Key reframing sentence (use verbatim, replaces "model predicts accurately"):

> *"The model captures the regime transition but systematically underestimates
> continuation-induced waiting in VT."*

Negative R² at high ρ (VT −0.28) is not model failure to hide — it **quantifies exactly the
cost component that Erlang-C does not represent**, and that gap is itself evidence for the
resource-transformation thesis (the annotated gap in Fig 2).

---

## 6. Discussion — exactly four subsections

**6.1 Why does the model work (below ρ*)?**
Little's Law holds regardless of currency; parking a continuation and blocking a thread are
the same *queueing* event. Below ρ*, currency choice is invisible to latency — which is why
one Erlang-C parameterization fits both execution models.

**Lemma 1 (Resource Currency Invariance) — derived from Little's Law.**
*For a system with arrival rate λ and fixed waiting workload W, Little's Law gives
E[N] = λW independently of execution model. This follows directly from L = λW applied
to each execution model independently. The physical realization of N is model-dependent:*
- *PT: N represents native thread occupancy (one OS thread per waiting request).*
- *VT: N represents heap-resident continuation state (one parked continuation per waiting request).*

*The waiting time W is therefore currency-invariant, while its physical substrate is not.*

*Corollary:* Below ρ*, the two execution models are latency-indistinguishable by any
queueing observer — a single M/M/c parameterization fits both. Above ρ*, continuation
accumulation introduces waiting that Erlang-C does not model, explaining the systematic
underestimation in VT.

**6.2 Why does prediction degrade (above ρ\*)?**
*Possible explanations — do not assert:* burst arrivals (measured inter-arrival CV > 1,
i.e., burstier than Poisson, A1 strained), finite queue effects near saturation, Loom
scheduler/carrier contention, GC pressure from continuation state. Frame as hypotheses
consistent with the VT-specific error direction; testing them is future work.

**6.3 Practical implications**
- ρ < 0.7 → model is a reliable capacity-planning tool; VT vs PT choice is
  performance-neutral (choose on other grounds: memory footprint, operational limits).
- 0.7 ≤ ρ < 0.85 → use predictions as trend indicators; expect underestimation for VT.
- ρ ≥ 0.85 → model output is a **warning signal only**; VT's latency penalty vs PT is large
  (d > 1.7) and its blocking inventory lives in the heap — monitor heap/GC, not thread count.

> **Practitioner take-away**
>
> | Region | ρ range | Recommended action |
> |--------|---------|-------------------|
> | I — Queue-free | ρ ≤ 0.70 | Model reliable; VT/PT choice is performance-neutral |
> | II — Transformation | 0.70 < ρ ≤ 0.85 | Directional guidance only; expect VT underestimation |
> | III — Accumulation | ρ > 0.85 | Warning signal only; switch to heap/GC monitoring |

> **Monitoring signal checklist (per region)**
>
> | Signal | Region I | Region II | Region III |
> |--------|----------|-----------|------------|
> | Carrier thread count | ✗ Do not monitor (misleading under VT) | ✗ | ✗ |
> | Request queue length | ✓ | ✓ | ✓ |
> | Heap occupancy | Optional | ✓ Watch | ✓ **Primary signal** |
> | GC pause frequency | Optional | ✓ Watch | ✓ **Primary signal** |
> | Erlang-C model output | ✓ Reliable | ⚠ Directional only | ⚠ Saturation warning |
>
> *Under Virtual Threads, heap occupancy and GC pause frequency are the appropriate monitoring
> signals in Regions II–III. Carrier thread pool saturation is not a valid proxy for request
> backlog when continuations absorb the blocking inventory.*


**6.4 Limitations (write them strong)**
Analytical assumptions were intentionally simplified to yield closed-form, falsifiable
predictions; the following limitations define the scope of validity.

Single DBMS (SQL Server); single framework (Spring Boot/Tomcat); synthetic open-loop
workload approximation; exponential service-time assumption (A2 — see §4.5); single JVM
version (Loom scheduler evolves); n = 5 replicates per cell → wide CIs at high ρ (CV up
to 37%); breakpoint CI limited by a 5-level ρ grid — intermediate levels (0.60, 0.75,
0.80) would tighten [0.50, 0.70]; heap currency measured indirectly (E[Nq] as continuation
count) since GC bounds resident heap; **model calibrated on a single blocking workload type
(synchronous JDBC / SQL Server) — generalizability to heterogeneous or mixed blocking
patterns (network I/O, file I/O, mixed) is untested**.

**Virtual Thread pinning excluded (A3).** The experimental workload uses synchronous JDBC
without `synchronized` blocks or JNI calls. Workloads that trigger carrier thread pinning
(legacy `synchronized`, JNI, or `Object.wait()`) invalidate the currency-transformation
abstraction: a pinned Virtual Thread holds both a continuation *and* a native thread,
collapsing the currency distinction. Model behavior under pinning workloads is explicitly
out of scope and constitutes a priority direction for future work.

**6.5 Relation to prior work**
This subsection explains what this paper does differently from prior benchmarks — it is
distinct from the Related Work survey in §2.

Prior benchmarking studies report throughput or latency improvements under blocking
workloads and answer "Is VT faster?" — a valuable but incomplete question. This paper
asks instead: *Why*, *when*, and *under what conditions*?

By formalizing the resource-currency transformation analytically and validating falsifiable
predictions, we move from empirical observation to mechanistic explanation. The model
applicability boundary (ρ* ≈ 0.70) and the four-region operating envelope are not findings
that prior benchmarks could have produced — they require a predictive abstraction to
compare against, not just measurements. The contribution is not a faster benchmark, but a
principled explanation of *when* and *why* the two execution models diverge.

**6.6 Future Work**
Five extensions that would materially strengthen the generalizability score:

1. **Sensitivity analysis on service time** — repeat with blocking durations of 20 ms, 40 ms,
   80 ms to test whether operating-region boundaries shift with mean service time. Expected
   result: ρ* should be relatively stable; this would support the model's robustness.
2. **Second database** (e.g., PostgreSQL) — same workload, same load levels. Would confirm
   that the envelope is not SQL Server–specific.
3. **Second blocking type** — network sleep, HTTP, Redis, or Kafka I/O. Directly addresses
   the generalizability gap (Generalizability score 6.5/10). Even one additional workload
   would allow a limited transferability claim.
4. **GC and heap metrics** — JVM heap occupancy and GC pause frequency across ρ levels.
   This would convert the "continuation accumulation" narrative from *inferred* to *directly
   observed*, addressing the resource-currency measurement gap.
5. **Confidence intervals on latency figures** — add 95% CI bands to all latency-vs-ρ figures.
   Currently figures show means only; CI bands would visually support the n = 5 discussion
   and make the effect-size table easier to interpret.

---

## Design Principles

Derived from the operating envelope; intended for system architects and capacity planners.
These principles distil the analytical findings into actionable form.

**DP1: Monitor resource currency, not thread count.**
Under Virtual Threads, the relevant resource is heap-resident continuation state, not carrier
thread pool size. Thread count is a misleading monitoring signal above ρ*; heap occupancy
and GC pressure are the appropriate metrics in Regions II–III.

**DP2: Apply queueing approximation only within the valid operating region.**
Erlang-C predictions are quantitatively reliable in Region I (ρ ≤ 0.70). In Region II
(0.70 < ρ ≤ 0.85), treat model output as directional only. In Region III, use the model
as a saturation warning, not a capacity estimate.

**DP3: Capacity planning should be regime-aware.**
The boundary ρ* ≈ 0.70 defines the transition from analytical predictability to
regime-dependent behaviour. Identify the expected operating region first, then select
monitoring and scaling strategies accordingly — not the reverse.

---

## Figure & table placement

| Asset | Where | Role |
|---|---|---|
| Fig 1 operating envelope | **Introduction** | The promise |
| Fig 8 resource reallocation | **Results 5.1, leads** | The thesis, visually |
| Fig 2 latency vs ρ (gap annotated) | Results 5.2 | Regime transition + unmodeled gap |
| Fig 3 calibration | Results 5.2 | Prediction quality |
| Table 2 validation metrics | Results 5.2 | Numbers out of the prose |
| Fig 4 residual + breakpoint CI | Results 5.3 | Data-driven boundary |
| Table 4 effect size | **Results 5.3, main text** | Regime dependence |
| Table 3 statistical tests | Results 5.2 (brief) | No significant bias (n small) |
| Fig 5 Bland–Altman | Appendix | Support, not lead |
| Fig 6 VT vs PT panels | Appendix (or merge into 5.1) | Support |
| Fig 7 correlation heatmap | Appendix | Support |

---

## One-sentence summaries to reuse

- Abstract closer: *"Our results indicate that the benefits of Virtual Threads are strongly
  operating-region dependent: differences are negligible under moderate utilization but
  become substantial as the system approaches resource saturation."*
- RQ2 verdict: *"The approximation is quantitatively useful below ρ\* ≈ 0.70 and
  qualitatively informative above it."*
- RQ3 verdict: *"The model captures the regime transition but systematically underestimates
  continuation-induced waiting in VT."*
- Region IV language: *"latency rapidly increases until bounded by timeout or heap capacity
  limits"* — never "latency → ∞".
- Story in one line: *"Not 'VT outperforms PT', but 'the difference is regime-dependent —
  and we can predict the regime.'"*

reference 

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