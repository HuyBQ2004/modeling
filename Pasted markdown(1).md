# Theoretical Framework — Version 3
## Queueing-Based Resource Saturation Model for Java Virtual Threads

> **Status:** Working draft for theoretical contribution section.
> All corrections from review rounds 1–3 incorporated.

---

## Central Thesis

> **Virtual Threads do not eliminate blocking; they transform the resource currency in which
> blocking time is denominated.**

| Execution Model | Blocking currency |
|---|---|
| Platform Thread | Native execution context (kernel thread, OS-managed) |
| Virtual Thread | Heap-resident continuation state (JVM-managed) |

The shift in currency — not the elimination of blocking — explains all empirically observed
differences: OS thread reduction, heap increase, and latency similarity at equivalent load.

This reframing is the conceptual contribution. The queueing model below provides the
analytical formalization. The experiments provide validation.

---

## Figure 1 (Operating Envelope — to appear in Introduction)

```
Concurrent Users (n)
──────────────────────────────────────────────────────────────────→

 │ Region I    │    Region II    │  Region III   │   Region IV    │
 │   SAFE      │   EFFICIENT     │   DEGRADED    │   CRITICAL     │
 │             │                 │               │                │
 │ Both models │ VT: queue grows │ PT: nearing   │ PT: exhausted  │
 │ stable,     │ PT: threads     │ T_max limit   │ VT: degrading  │
 │ similar     │ growing         │ VT: heap-dom. │ but alive      │
 │             │                 │               │                │
 ├─────────────┼─────────────────┼───────────────┼────────────────┤
 │  P(wait)    │  P(wait) ≥ 5%  │  P99 ≥ 2×Lₒ  │  Timeout/OOM   │
 │  < 5%       │  P99 < 2×Lₒ   │  or ρ ≥ 0.9   │  or n ≥ T_max  │
 └─────────────┴─────────────────┴───────────────┴────────────────┘
      n₀             n_warn             n_deg              n_crit
```

Boundaries n₀, n_warn, n_deg are derived from the model (Section 3).
Boundaries are quantitative, falsifiable, and workload-specific.
`Lₒ` = baseline P99 latency at n → 0 (measured).

---

## 1. System Architecture and Shared Bottleneck

```
HTTP Request
     ↓
Tomcat Acceptor
     ↓
Worker (Platform Thread | Virtual Thread)
     ↓
HikariCP Connection Pool (c = pool_size)  ← single service center, shared
     ↓
SQL Server
```

**Key claim:** The database connection pool is the dominant service center.
Both execution models contend for the same `c` DB connections.
The difference is not *where* requests queue, but *what resource is consumed while queuing*.

---

## 2. Blocking Currency Model

Define **blocking currency** B as the resource consumed by a request waiting time τ:

```
B_PT(τ) = 1 native execution context held for duration τ
           → contributes to OS thread inventory

B_VT(τ) = heap-resident continuation state maintained for duration τ
           → contributes α(pool) bytes to JVM heap inventory
```

Both represent the same waiting time τ, denominated in different resource units.
This unifies the empirical observations under a single accounting principle.

**Important:** α(pool) is written as a function of pool size.
Whether α is approximately constant or pool-dependent is an empirical question —
the data decides. This is itself a research finding (see Section 5).

---

## 3. Queueing Model

### 3.1 Model Assumptions

| ID | Assumption | Justification / Risk |
|---|---|---|
| **A1** | Poisson arrivals (M/M/c) | Standard load generator (JMeter) approximates Poisson at high concurrency. Validated via inter-arrival time distribution check. |
| **A2** | IID service times (exponential) | Each request hits same query; exponential is first-order approximation. Non-exponential service → model is conservative estimate. |
| **A3** | Non-pinning workload | No `synchronized` blocks or JNI calls in hot path. If violated, N_pinned > 0, carrier count rises. Tested by monitoring VT thread count. |
| **A4** | Connection pool is dominant bottleneck | Validated empirically: PT thread count grows with n, not with CPU saturation. Sensitivity analysis (pool=50 vs 100) confirms pool as bottleneck. |

These assumptions are acknowledged approximations.
Deviations are characterized in Section 6 (Discussion of model limitations).

### 3.2 M/M/c Service System

**One queue. Two execution models. Same service system.**

> *"Both Platform Threads and Virtual Threads operate over the same underlying M/M/c service
> system. They differ in the runtime resource used to represent waiting requests."*

Parameters:
```
c   = pool_size                (number of DB connections = servers)
λ   = request arrival rate     (measured from load generator)
μ   = 1 / E[service_time]     (per connection; estimated from low-load measurements)
ρ   = λ / (c · μ)             (utilization; ρ < 1 required for stability)
```

M/M/c results:
```
C(c, ρ)    = Erlang C formula          P(new request must wait)
E[W_q]     = C(c,ρ) / (c·μ·(1-ρ))    mean waiting time in queue
E[N_q]     = λ · E[W_q]               mean number waiting  [Little's Law]
E[N_sys]   = λ · (E[W_q] + 1/μ)      mean number in system [Little's Law]
```

### 3.3 Phase Boundaries (model predictions)

```
n₀     : onset of queueing     → n such that C(c, λ_n/cμ) > 0.05
n_warn : latency doubles       → n such that E[W_q] > 1/μ   (i.e., P99 ≈ 2×baseline)
n_deg  : high heap / ρ → 0.9  → n such that ρ > 0.9
n_crit : PT thread exhaustion  → n = T_max (empirically measured)
```

These are **predictions made before the corresponding measurements**.
Validation compares predicted boundaries against observed phase transitions.

---

## 4. Resource Occupancy Model

Rather than applying Little's Law directly to threads (which requires careful justification),
we use a **Resource Occupancy** formulation:

```
Occupancy(resource, n) = Arrival Rate × Mean Residence Time in resource
```

This is structurally identical to Little's Law but phrased in resource terms.

### 4.1 OS Thread Occupancy

**Platform Threads** (under assumption A3, I/O-bound):

Each request in system holds one native execution context for its entire sojourn W:
```
OS_occupancy_PT(n) = λ · E[W(n)]
                   = E[N_sys(n)]          [by Little's Law, given 1:1 mapping]
                   ≈ n                    [under overload: all requests waiting]
```

*Justification of 1:1 mapping:* A PT request in the waiting state holds its kernel thread
blocked on HikariCP's condition variable. The thread is not returned to any pool.
Therefore: one in-flight request = one occupied OS thread (measurable, verified empirically).

**Virtual Threads:**

A VT request holds a carrier thread only during the compute phase (not I/O wait):
```
OS_occupancy_VT(n) = N_carrier(n) + N_system_overhead
N_carrier(n)       = λ · E[W_cpu]         (compute-phase occupancy only)
                   ≈ small constant        (under A3: no pinning)
```

Carrier count is **workload-dependent and bounded**, not a JVM constant.
The observed value (e.g., 136 threads) is empirical; the model predicts "bounded and small
relative to n", not a specific number.

### 4.2 Heap Occupancy

```
Heap_PT(n) = Heap_base + ε_PT(n)
             (waiting threads use native stack → no heap contribution from blocking)

Heap_VT(n) = Heap_base + α(pool) · E[N_q(n)] + ε_VT(n)
```

Where:
- `α(pool)` = mean heap bytes per parked continuation as function of pool configuration
- `E[N_q(n)]` = from Erlang C (M/M/c queue length)
- `ε_VT(n)` = residual: GC overhead, StackChunk fragmentation, allocation metadata,
               continuation depth variance, object lifetime effects

**Research finding from α(pool):**
- If α ≈ constant across pool sizes → linear approximation is sufficient
- If α varies → heap growth is non-linear; more complex model needed
- Both outcomes are publishable findings; do not assume in advance

---

## 5. Quantitative Phase Boundaries

| Region | Boundary condition | Resource indicator |
|---|---|---|
| SAFE → EFFICIENT | `P(wait) ≥ 5%` | Queue begins forming |
| EFFICIENT → DEGRADED | `E[P99] ≥ 2·L₀` or `ρ ≥ 0.9` | Latency significantly elevated |
| DEGRADED → CRITICAL | `n ≥ T_max` (PT) or `Heap ≥ H_max` (VT) | Resource exhaustion |

**For VT in Region IV:**
Latency increases rapidly, bounded above by operational constraints:
- Connection timeout (120s in experiment)
- GC pressure limiting heap growth
- OOM (heap) in extreme cases

Do NOT use `latency → ∞`. Use: *"latency rapidly increases until bounded by
timeout or heap capacity limits"*.

---

## 6. Prediction Before Measurement

The model makes concrete, falsifiable predictions before the corresponding experiments.
Example (to be filled with actual model estimates before running experiments):

| Prediction | Pool | Model predicts | Measured | Error |
|---|---|---|---|---|
| Heap_VT at peak | 100 | ___ MB | ___ MB | ___% |
| Heap_VT at peak | 200 | ___ MB | ___ MB | ___% |
| n₀ (queue onset) | 50  | ___ users | ___ users | ___% |
| n_warn (P99×2)   | 50  | ___ users | ___ users | ___% |

This table should appear in the paper before the experiment section.
Filling in predictions first, then measuring, prevents post-hoc rationalization.

---

## 7. Validation Design

### 7.1 Parameter Estimation (training set)
```
Pool sizes: {10, 25, 50}
Estimate: μ (from low-load P50 latency), α(pool) at each pool size
Verify: Erlang C E[N_q] trend matches observed queue behavior
```

### 7.2 Out-of-Sample Prediction (test set)
```
Pool sizes: {100, 200}
Predict first: Heap_VT, Latency P99, OS_threads_VT
Then measure
Report: relative prediction error, confidence intervals
Accept criterion: error < 20% for useful approximation
```

If error > 40%: model is illustrative. Report honestly. Explain which assumption (A1–A4)
most likely causes deviation. This is still publishable — it bounds the model's validity.

### 7.3 Operating Region Validation
```
Verify that empirically observed transitions (heap surge, thread explosion, latency jump)
occur within ±10% of model-predicted boundary values n₀, n_warn, n_deg.
```

---

## 8. Research Questions (final version)

```
RQ1: Can a queueing-theoretic model explain the distinct resource occupancy behavior of
     Platform Threads and Virtual Threads as two execution models over a shared M/M/c
     service system?

RQ2: Can the proposed model predict resource utilization (OS thread count, heap consumption,
     request latency) and operating region boundaries under varying connection pool
     configurations, with predictions validated through out-of-sample experiments?
```

---

## 9. Contribution Statement (final version)

> *"We present a Queueing-Based Resource Saturation Model that formalizes Platform Threads
> and Virtual Threads as two execution models operating over a shared M/M/c service center,
> differing in the resource currency used to represent blocking time: native execution contexts
> for Platform Threads, and heap-resident continuation state for Virtual Threads. From this
> model, we derive quantitative predictions for OS thread occupancy, heap consumption, and
> four operating region boundaries as functions of arrival rate, service time, and connection
> pool size. We validate predictions against controlled experiments in a Spring Boot
> microservice under CPU and memory constraints, reporting out-of-sample prediction accuracy
> and characterizing systematic deviations attributable to JVM runtime effects."*

---

## 10. Novelty Framing (safe language)

> *"To our knowledge, prior empirical studies of Java Virtual Threads report throughput and
> latency measurements under varying concurrency levels [refs]. Analytical models that
> characterize the resource-currency transformation introduced by Virtual Threads — specifically,
> the shift from native execution context occupancy to heap-resident continuation state — and
> that provide quantitative predictions of operating region boundaries, remain limited in the
> literature. This work addresses that gap."*

---

## 11. Mandatory Checklist Before Submission

- [ ] Model Assumptions (A1–A4) each have their own justification paragraph in paper
- [ ] α(pool) is measured at ≥ 3 pool sizes; linearity is reported as finding, not assumption
- [ ] Phase boundaries are stated as model predictions *before* the experiment section
- [ ] Out-of-sample validation uses pool configs not used in parameter estimation
- [ ] Region IV described as "latency rapidly increases until bounded by operational constraints"
- [ ] No "competing M/M/c systems" — ONE shared system, two execution models
- [ ] No "latency → ∞"
- [ ] No "nobody has done this" — use safe novelty framing above
- [ ] Cross-validation table appears in paper with predictions filled before measurement
- [ ] Operating Envelope figure appears early (Introduction or Section 2)
- [ ] Replication package: Docker + scripts + data on GitHub

