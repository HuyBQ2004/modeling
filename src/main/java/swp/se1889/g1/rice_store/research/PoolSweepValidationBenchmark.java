package swp.se1889.g1.rice_store.research;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import swp.se1889.g1.rice_store.repository.InvoicesRepository;

import javax.sql.DataSource;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pool Sweep Validation Benchmark
 *
 * Purpose: Validate the Queueing-Based Resource Saturation Model for Java Virtual Threads.
 *
 * Design:
 *   TRAINING pools {10, 25, 50}  → estimate μ (service rate), α(pool) (heap overhead per continuation)
 *   TEST pools     {100, 200}    → predict FIRST, then measure, then compare
 *   CROSS-VAL      training={25, 50, 100}, test={10, 200} — uses existing measurements, no re-run
 *
 * Methodological notes (reviewer-facing):
 *
 *   λ: measured as total_submitted / submission_window_duration.
 *      This is a BURST arrival approximation, NOT a Poisson arrival process.
 *      M/M/c assumes memoryless (Poisson) arrivals. We report λ as an average-rate
 *      approximation and discuss arrival-process shape as a limitation.
 *
 *   μ: estimated from MEAN service time at low load (BASELINE_CONCURRENT=10,
 *      pool=BASELINE_POOL=200 → ρ ≈ 0 → W ≈ S). Mean is used, not P99.
 *
 *   Erlang C outputs MEAN waiting time → model predicts MEAN response time.
 *      P99 is measured separately as a reference column (not predicted).
 *
 *   Erlang C uses log-space arithmetic to avoid factorial overflow at c=200.
 *
 *   α is an EFFECTIVE parameter: it captures heap overhead per queued continuation,
 *      including JDBC objects, ResultSet references, ORM state, GC survivors, and
 *      other per-request allocations co-occurring with continuation parking.
 *      It is NOT the raw continuation size.
 *
 *   Region labels (Queue-free/Queueing/Saturated/Resource-Exhaustion) are operational
 *      categories for interpretation, NOT theoretical phase transitions.
 *      Boundaries (P(wait) < 5%/50%/90%) are engineering thresholds.
 *
 * Outputs:
 *   POOL_SWEEP_RAW_<ts>.csv        — raw per-iteration measurements
 *   POOL_SWEEP_SUMMARY_<ts>.csv    — per-pool mean ± std ± 95% CI
 *   POOL_SWEEP_VALIDATION_<ts>.csv — prediction vs. measurement + MAPE/RMSE
 *   POOL_SWEEP_ALPHA_<ts>.csv      — α(pool) values to test linearity assumption
 *   POOL_SWEEP_CROSSVAL_<ts>.csv   — cross-validation results (flipped training/test)
 */
@Component
@Profile("pool-sweep")
public class PoolSweepValidationBenchmark implements CommandLineRunner {

    @Autowired private DataSource dataSource;
    @Autowired private InvoicesRepository invoicesRepository;

    // ===== EXPERIMENT CONFIG =====

    private static final int[] TRAINING_POOLS = {10, 25, 50};
    private static final int[] TEST_POOLS     = {100, 200};

    private static final int CONCURRENCY            = 4000;
    private static final int REQUESTS_PER_ITERATION = CONCURRENCY * 10;
    private static final int TOTAL_ITERATIONS       = 8;
    private static final int WARMUP_ROUNDS          = 2;
    private static final int MEASUREMENT_ROUNDS     = TOTAL_ITERATIONS - WARMUP_ROUNDS;
    private static final int SIMULATED_LATENCY_MS   = 50;
    private static final long TEST_STORE_ID         = 1L;

    /**
     * Baseline load configuration for μ estimation.
     *
     * BASELINE_POOL >> BASELINE_CONCURRENT ensures ρ ≈ 0 (queue-free).
     * Under ρ ≈ 0: response time ≈ service time → E[S] = mean(response times).
     * Using 10 concurrent into a pool of 200: ρ ≈ 0.05 — effectively queue-free.
     *
     * Previous value of 50 concurrent was borderline; borderline cases risk
     * including small wait times in E[S], which would inflate μ estimates.
     */
    private static final int BASELINE_POOL       = 200;
    private static final int BASELINE_CONCURRENT = 10;

    /**
     * Enables cross-validation after the main experiment (Phase 4).
     * Flips training to {25, 50, 100} and test to {10, 200}.
     * Reuses already-collected measurements — no additional execution time.
     */
    private static final boolean RUN_CROSS_VALIDATION = true;

    /**
     * Validation thresholds — empirical engineering criteria, NOT model-derived bounds.
     * Heap (25%): GC non-determinism and OS allocation granularity.
     * Latency (30%): scheduler jitter and measurement noise across iterations.
     * Reported as operational thresholds in the paper, not theoretical bounds.
     */
    private static final double HEAP_ERROR_THRESHOLD_PCT    = 25.0;
    private static final double LATENCY_ERROR_THRESHOLD_PCT = 30.0;

    // ===== BASELINE MEASUREMENTS =====
    private long   baselineHeapMb = -1;
    private double baselineMeanMs = -1;  // E[S] at low load — used for μ = 1/E[S]
    private double baselineP99Ms  = -1;  // reference only, not used in model

    // ===== MODEL PARAMETERS (estimated from training data) =====
    private double mu_estimated  = -1;
    private double lambda_actual = -1;
    private double rho_training  = -1;

    private final Map<Integer, Double>     alphaByPool     = new LinkedHashMap<>();
    private final Map<Integer, PoolResult> trainingResults = new LinkedHashMap<>();
    private final Map<Integer, PoolResult> testResults     = new LinkedHashMap<>();

    // ===== OUTPUT FILES =====
    private final String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    private final String FILE_RAW        = "data/POOL_SWEEP_RAW_"        + timestamp + ".csv";
    private final String FILE_SUMMARY    = "data/POOL_SWEEP_SUMMARY_"    + timestamp + ".csv";
    private final String FILE_VALIDATION = "data/POOL_SWEEP_VALIDATION_" + timestamp + ".csv";
    private final String FILE_ALPHA      = "data/POOL_SWEEP_ALPHA_"      + timestamp + ".csv";
    private final String FILE_CROSS_VAL  = "data/POOL_SWEEP_CROSSVAL_"   + timestamp + ".csv";

    // ===================================================================
    // MAIN ENTRY
    // ===================================================================

    @Override
    public void run(String... args) throws Exception {
        System.out.println("============================================================");
        System.out.println("  POOL SWEEP VALIDATION BENCHMARK");
        System.out.println("  Model: Queueing-Based Resource Saturation (VT)");
        System.out.println("============================================================");

        captureBaseline();

        try (PrintWriter rawWriter     = openCsvWriter(FILE_RAW);
             PrintWriter summaryWriter = openCsvWriter(FILE_SUMMARY);
             PrintWriter alphaWriter   = openCsvWriter(FILE_ALPHA);
             PrintWriter valWriter     = openCsvWriter(FILE_VALIDATION)) {

            writeRawHeader(rawWriter);
            writeSummaryHeader(summaryWriter);
            writeAlphaHeader(alphaWriter);

            // --- PHASE 1: TRAINING ---
            System.out.println("\n--- PHASE 1: TRAINING (parameter estimation) ---");
            for (int pool : TRAINING_POOLS) {
                PoolResult result = runPoolExperiment(pool, rawWriter);
                trainingResults.put(pool, result);
                writeSummaryRow(summaryWriter, result, "TRAINING");
                computeAndRecordAlpha(alphaWriter, pool, result);
            }

            // Estimate model parameters from training observations
            estimateModelParameters(trainingResults, true);

            // --- PHASE 2: PREDICTIONS (before any test measurement) ---
            System.out.println("\n--- PHASE 2: PREDICTIONS (frozen before measurement) ---");
            Map<Integer, ModelPrediction> predictions = new LinkedHashMap<>();
            for (int pool : TEST_POOLS) {
                ModelPrediction pred = predictForPool(pool, mu_estimated, lambda_actual, alphaByPool);
                predictions.put(pool, pred);
                System.out.printf(
                        "  Pool=%3d | Heap=%.0f MB | Mean=%.0f ms | ρ=%.3f | P(wait)=%.3f | %s%n",
                        pool, pred.heapMb, pred.meanLatencyMs, pred.rho, pred.pWait, pred.region);
            }

            // --- PHASE 3: TEST (measure, then compare) ---
            System.out.println("\n--- PHASE 3: TEST (measure + compare to frozen predictions) ---");
            writeValidationHeader(valWriter);
            List<Double> heapErrors    = new ArrayList<>();
            List<Double> latencyErrors = new ArrayList<>();
            for (int pool : TEST_POOLS) {
                PoolResult result = runPoolExperiment(pool, rawWriter);
                testResults.put(pool, result);
                writeSummaryRow(summaryWriter, result, "TEST");
                double[] errs = writeValidationRow(valWriter, pool, predictions.get(pool), result);
                heapErrors.add(errs[0]);
                latencyErrors.add(errs[1]);
            }
            printPredictionAccuracy("PRIMARY", heapErrors, latencyErrors);
        }

        // --- PHASE 4: CROSS-VALIDATION (no new measurements) ---
        if (RUN_CROSS_VALIDATION) {
            System.out.println("\n--- PHASE 4: CROSS-VALIDATION (flipped training/test) ---");
            runCrossValidation();
        }

        System.out.println("\n============================================================");
        System.out.println("  BENCHMARK COMPLETE");
        System.out.printf("  μ = %.4f req/s/conn  [E[S] = %.1f ms]%n",
                mu_estimated, 1000.0 / mu_estimated);
        System.out.printf("  λ = %.2f req/s  [burst-rate approximation]%n", lambda_actual);
        System.out.printf("  α linearity: %s%n", assessAlphaLinearity(alphaByPool));
        System.out.printf("  Files: %s | %s | %s | %s%n",
                FILE_SUMMARY, FILE_VALIDATION, FILE_ALPHA,
                RUN_CROSS_VALIDATION ? FILE_CROSS_VAL : "(cross-val disabled)");
        System.out.println("============================================================");
    }

    // ===================================================================
    // CROSS-VALIDATION (Phase 4)
    // ===================================================================

    /**
     * Cross-validation with flipped training/test assignment.
     *
     * Training: {25, 50, 100} — measured in phases 1 and 3.
     * Test:     {10, 200}    — measured in phases 1 and 3.
     *
     * No additional measurement runs are needed; existing PoolResult objects are reused.
     * This tests whether the model generalizes to pools outside the original training range
     * (pool=10 is extrapolation below; pool=200 is extrapolation above).
     */
    private void runCrossValidation() throws Exception {
        // Build cross-val training set from already-collected measurements
        Map<Integer, PoolResult> cvTraining = new LinkedHashMap<>();
        if (trainingResults.containsKey(25))  cvTraining.put(25,  trainingResults.get(25));
        if (trainingResults.containsKey(50))  cvTraining.put(50,  trainingResults.get(50));
        if (testResults.containsKey(100))     cvTraining.put(100, testResults.get(100));

        if (cvTraining.size() < 2) {
            System.out.println("  [CROSS-VAL] Insufficient data, skipping.");
            return;
        }

        // Re-estimate α from cross-val training pools
        Map<Integer, Double> cvAlpha = new LinkedHashMap<>();
        for (Map.Entry<Integer, PoolResult> e : cvTraining.entrySet()) {
            int        pool = e.getKey();
            PoolResult r    = e.getValue();
            double     eNq  = estimateQueueLength(pool, r.meanArrivalRate, mu_estimated);
            double     alpha = (eNq > 0.1)
                    ? Math.max(0, r.meanHeapMb - baselineHeapMb) / eNq : 0;
            cvAlpha.put(pool, alpha);
        }

        // μ is from baseline (unchanged); λ re-estimated from cross-val training set
        double cvLambda = cvTraining.values().stream()
                .mapToDouble(r -> r.meanArrivalRate)
                .average().orElse(lambda_actual);

        System.out.printf("  Training pools: %s  |  Test pools: {10, 200}%n",
                cvTraining.keySet());
        System.out.printf("  μ=%.4f req/s  λ=%.2f req/s%n", mu_estimated, cvLambda);

        // Predict for pools 10 and 200 using cross-val parameters
        int[] cvTestPools = {10, 200};
        Map<Integer, ModelPrediction> cvPreds = new LinkedHashMap<>();
        for (int pool : cvTestPools) {
            ModelPrediction pred = predictForPool(pool, mu_estimated, cvLambda, cvAlpha);
            cvPreds.put(pool, pred);
            System.out.printf("  [PRED] Pool=%3d | Heap=%.0f MB | Mean=%.0f ms | ρ=%.3f%n",
                    pool, pred.heapMb, pred.meanLatencyMs, pred.rho);
        }

        // Compare to already-measured results; write cross-val CSV
        try (PrintWriter cvWriter = openCsvWriter(FILE_CROSS_VAL)) {
            writeValidationHeader(cvWriter);
            List<Double> heapErrors    = new ArrayList<>();
            List<Double> latencyErrors = new ArrayList<>();
            for (int pool : cvTestPools) {
                PoolResult measured = (pool <= 50)
                        ? trainingResults.get(pool) : testResults.get(pool);
                if (measured == null || !cvPreds.containsKey(pool)) continue;
                double[] errs = writeValidationRow(cvWriter, pool, cvPreds.get(pool), measured);
                heapErrors.add(errs[0]);
                latencyErrors.add(errs[1]);
            }
            printPredictionAccuracy("CROSS-VAL", heapErrors, latencyErrors);
            System.out.printf("  Output: %s%n", FILE_CROSS_VAL);
        }
    }

    // ===================================================================
    // CORE EXPERIMENT RUNNER
    // ===================================================================

    private PoolResult runPoolExperiment(int poolSize, PrintWriter rawWriter) throws Exception {
        setPoolSize(poolSize);
        System.out.printf("%n[Pool=%3d] Running %d iterations (%d warmup)...%n",
                poolSize, TOTAL_ITERATIONS, WARMUP_ROUNDS);

        ExecutorService vtExecutor = Executors.newVirtualThreadPerTaskExecutor();

        List<Double> throughputs   = new ArrayList<>();
        List<Double> arrivalRates  = new ArrayList<>();
        List<Double> p99s          = new ArrayList<>();
        List<Double> meanLatencies = new ArrayList<>();
        List<Double> heaps         = new ArrayList<>();
        List<Double> threadCounts  = new ArrayList<>();

        for (int i = 1; i <= TOTAL_ITERATIONS; i++) {
            System.gc();
            Thread.sleep(800);

            RunResult     run = runIteration(vtExecutor);
            SystemMetrics sys = captureSystemMetrics();

            rawWriter.printf("VT_IO_Wait,%d,%d,%.2f,%.2f,%.2f,%.2f,%d,%d%n",
                    poolSize, i,
                    run.arrivalRate, run.throughput, run.meanMs, run.p99Ms,
                    sys.heapUsedMb, sys.threadCount);
            rawWriter.flush();

            if (i > WARMUP_ROUNDS) {
                arrivalRates.add(run.arrivalRate);
                throughputs.add(run.throughput);
                p99s.add(run.p99Ms);
                meanLatencies.add(run.meanMs);
                heaps.add((double) sys.heapUsedMb);
                threadCounts.add((double) sys.threadCount);
                System.out.printf(
                        "  iter %2d: λ=%.1f | tp=%.1f rps | mean=%.0f ms | P99=%.0f ms | heap=%d MB%n",
                        i, run.arrivalRate, run.throughput, run.meanMs, run.p99Ms, sys.heapUsedMb);
            } else {
                System.out.printf("  iter %2d: [WARMUP]%n", i);
            }
        }

        vtExecutor.shutdownNow();

        double meanArrivalRate = mean(arrivalRates);
        double eNqueue         = estimateQueueLength(poolSize, meanArrivalRate, mu_estimated);

        return new PoolResult(
                poolSize,
                mean(throughputs),    stddev(throughputs),
                meanArrivalRate,
                mean(p99s),           stddev(p99s),           ci95(p99s),
                mean(meanLatencies),  stddev(meanLatencies),  ci95(meanLatencies),
                mean(heaps),          stddev(heaps),           ci95(heaps),
                mean(threadCounts),
                eNqueue
        );
    }

    private RunResult runIteration(ExecutorService executor) throws InterruptedException {
        CountDownLatch latch     = new CountDownLatch(REQUESTS_PER_ITERATION);
        List<Long>     latencies = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger  success   = new AtomicInteger();

        // --- λ measurement via submission-window timestamps ---
        // λ = REQUESTS_PER_ITERATION / submission_window_duration.
        //
        // LIMITATION: This is a BURST arrival process (tight sequential loop),
        // not a Poisson arrival process. M/M/c assumes memoryless (Poisson) arrivals.
        // We use λ as an average-rate approximation of the arrival intensity.
        // The sensitivity of results to arrival-process shape (burst vs. Poisson)
        // is acknowledged as a limitation and discussed in the paper.
        long submitStart = System.nanoTime();
        for (int i = 0; i < REQUESTS_PER_ITERATION; i++) {
            executor.submit(() -> {
                long t0 = System.nanoTime();
                try {
                    performIoTask();
                    success.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    latencies.add(System.nanoTime() - t0);
                    latch.countDown();
                }
            });
        }
        long   submitEnd     = System.nanoTime();
        double submitDurSec  = Math.max((submitEnd - submitStart) / 1_000_000_000.0, 1e-9);
        double arrivalRate   = REQUESTS_PER_ITERATION / submitDurSec;

        latch.await(300, TimeUnit.SECONDS);

        long   completionEnd    = System.nanoTime();
        double completionDurSec = (completionEnd - submitStart) / 1_000_000_000.0;

        latencies.sort(Long::compare);
        double p99   = latencies.isEmpty() ? 0
                : latencies.get((int) (latencies.size() * 0.99)) / 1_000_000.0;
        double meanMs = latencies.isEmpty() ? 0
                : latencies.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;

        return new RunResult(
                arrivalRate,
                success.get() / completionDurSec,
                meanMs,
                p99,
                success.get() * 100.0 / REQUESTS_PER_ITERATION
        );
    }

    private void performIoTask() throws Exception {
        Pageable limit = PageRequest.of(0, 20);
        invoicesRepository.findTop5000JPQLDTO(TEST_STORE_ID, limit);
        Thread.sleep(SIMULATED_LATENCY_MS);
    }

    // ===================================================================
    // BASELINE CAPTURE
    // ===================================================================

    private void captureBaseline() throws Exception {
        System.out.println("\n--- Capturing baseline (idle system) ---");
        System.gc();
        Thread.sleep(2000);

        SystemMetrics idle = captureSystemMetrics();
        baselineHeapMb = idle.heapUsedMb;
        System.out.printf("  Baseline heap: %d MB%n", baselineHeapMb);

        // Measure E[S] under queue-free conditions.
        // Pool = BASELINE_POOL (200) >> BASELINE_CONCURRENT (10) → ρ ≈ 0.
        // Under ρ ≈ 0: response time ≈ service time, so mean(response) ≈ E[S].
        // μ = 1 / E[S] per M/M/c requirements — MEAN is used, NOT P99.
        setPoolSize(BASELINE_POOL);
        ExecutorService small = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch  latch = new CountDownLatch(BASELINE_CONCURRENT);
        List<Long>      lats  = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < BASELINE_CONCURRENT; i++) {
            small.submit(() -> {
                long t = System.nanoTime();
                try { performIoTask(); } catch (Exception ignored) {}
                finally { lats.add(System.nanoTime() - t); latch.countDown(); }
            });
        }
        latch.await(60, TimeUnit.SECONDS);
        small.shutdownNow();
        lats.sort(Long::compare);

        baselineP99Ms  = lats.isEmpty() ? SIMULATED_LATENCY_MS
                : lats.get((int) (lats.size() * 0.99)) / 1_000_000.0;
        baselineMeanMs = lats.isEmpty() ? SIMULATED_LATENCY_MS
                : lats.stream().mapToLong(Long::longValue).average()
                       .orElse(SIMULATED_LATENCY_MS * 1_000_000L) / 1_000_000.0;

        System.out.printf(
                "  Baseline E[S] = %.1f ms  →  μ = %.4f req/s/conn  [pool=%d, concurrent=%d, ρ≈0]%n",
                baselineMeanMs, 1000.0 / baselineMeanMs, BASELINE_POOL, BASELINE_CONCURRENT);
        System.out.printf("  Baseline P99 = %.0f ms  [reference only, not used in model]%n",
                baselineP99Ms);
    }

    // ===================================================================
    // MODEL PARAMETER ESTIMATION
    // ===================================================================

    /**
     * Estimate μ and λ from a given measurement set.
     *
     * @param measurements  pool → PoolResult observations
     * @param updateGlobals if true, writes results into class-level mu_estimated / lambda_actual
     */
    private void estimateModelParameters(Map<Integer, PoolResult> measurements,
                                         boolean updateGlobals) {
        // μ = 1 / E[S], where E[S] = mean service time at low load.
        // Using P99 would overestimate service time → underestimate μ → overestimate ρ.
        double mu = (baselineMeanMs > 0)
                ? 1000.0 / baselineMeanMs
                : 1000.0 / SIMULATED_LATENCY_MS;

        // λ = mean arrival rate across training pools.
        // Arrival rate is measured from submission timestamps — NOT completion rate.
        // Under overload: arrival rate > completion rate (queue accumulates).
        double lambda = measurements.values().stream()
                .mapToDouble(r -> r.meanArrivalRate)
                .average()
                .orElse(0);

        if (updateGlobals) {
            mu_estimated  = mu;
            lambda_actual = lambda;
            if (measurements.containsKey(50)) {
                rho_training = lambda / (50 * mu);
            }
        }

        System.out.printf("%n[Model Parameters  training=%s]%n", measurements.keySet());
        System.out.printf("  μ = %.4f req/s/conn  [E[S] = %.1f ms, measured at ρ≈0]%n",
                mu, 1000.0 / mu);
        System.out.printf("  λ = %.2f req/s  [burst-rate approximation, see paper §Limitations]%n",
                lambda);
        if (updateGlobals && rho_training >= 0) {
            System.out.printf("  ρ at pool=50: %.4f%n", rho_training);
        }
        System.out.printf("  α(pool): %s%n", assessAlphaLinearity(alphaByPool));
    }

    // ===================================================================
    // ALPHA ESTIMATION
    // ===================================================================

    private void computeAndRecordAlpha(PrintWriter alphaWriter, int pool, PoolResult result) {
        // Prefer Hikari-observed pending threads to break potential circular dependency:
        //   model → E[N_queue] → α → model
        // Hikari pending count is a ground-truth observation.
        long   hikariPending = getHikariPendingThreads();
        double eNqueue;
        String nqueueSource;
        if (hikariPending > 0) {
            eNqueue      = hikariPending;
            nqueueSource = "HIKARI_OBSERVED";
        } else {
            eNqueue      = estimateQueueLength(pool, result.meanArrivalRate, mu_estimated);
            nqueueSource = "MODEL_ESTIMATED";
        }

        double alpha = (eNqueue > 0.1)
                ? Math.max(0, result.meanHeapMb - baselineHeapMb) / eNqueue
                : 0;
        alphaByPool.put(pool, alpha);

        // α is an EFFECTIVE parameter capturing heap overhead per queued continuation.
        // It includes JDBC objects, ResultSet references, ORM state, GC survivors,
        // and other per-request allocations that co-occur with continuation parking.
        // Reported as "heap overhead per active continuation", NOT raw continuation size.
        alphaWriter.printf("%d,%.2f,%d,%.2f,%.2f,%s,%.4f,%s%n",
                pool, result.meanHeapMb, baselineHeapMb,
                result.meanHeapMb - baselineHeapMb,
                eNqueue, nqueueSource, alpha,
                assessAlphaLinearity(alphaByPool));
        alphaWriter.flush();
    }

    // ===================================================================
    // PREDICTION ENGINE
    // ===================================================================

    private ModelPrediction predictForPool(int pool, double mu, double lambda,
                                           Map<Integer, Double> alphaMap) {
        // ρ = λ / (c·μ)
        double rho = (mu > 0 && pool > 0) ? lambda / (pool * mu) : 0;
        rho = Math.min(rho, 0.9999);

        // P(wait) = Erlang C — probability a new request must queue.
        double pWait   = erlangC(pool, rho);
        double eNqueue = erlangC_Nqueue(pool, rho, lambda);

        // Heap prediction: baseline + mean_α × E[N_queue]
        double meanAlpha     = alphaMap.values().stream()
                .mapToDouble(Double::doubleValue).average().orElse(0);
        double predictedHeap = baselineHeapMb + meanAlpha * eNqueue;

        // Mean response time: E[W] = E[W_q] + 1/μ
        // This is the CORRECT Erlang C output — MEAN, not P99.
        // P99 would require knowing the waiting-time distribution (e.g., exponential),
        // which is an additional untested assumption. P99 is measured, not predicted.
        double eWq_sec         = erlangC_Wq(pool, rho, mu);
        double predictedMeanMs = eWq_sec * 1000.0 + (1000.0 / mu);

        // Region labels based on P(wait) — grounded in queueing theory.
        // These are OPERATIONAL CATEGORIES for interpretation, NOT theoretical phase transitions.
        // The boundaries 5%/50%/90% are engineering thresholds chosen for interpretability.
        String region = pWait < 0.05 ? "Queue-free"
                : pWait < 0.50      ? "Queueing"
                : pWait < 0.90      ? "Saturated"
                : "Resource-Exhaustion";

        return new ModelPrediction(pool, rho, pWait, eNqueue,
                predictedHeap, predictedMeanMs, meanAlpha, region);
    }

    // ===================================================================
    // ERLANG C — LOG-SPACE (numerically stable for large c)
    // ===================================================================

    /**
     * Erlang C: P(new request must wait) for M/M/c queue.
     *
     * Uses log-space arithmetic to avoid factorial overflow at large c.
     * Naive implementation overflows at c > 170 (c! > Double.MAX_VALUE),
     * producing NaN or Infinity for pool sizes such as c=200.
     *
     * @param c   number of servers (DB connection pool size)
     * @param rho traffic intensity ρ = λ/(c·μ), must be in [0, 1)
     * @return    C(c, ρ) ∈ [0, 1]
     */
    private double erlangC(int c, double rho) {
        if (rho >= 1.0) return 1.0;
        if (c <= 0)     return 0.0;

        double a    = c * rho;
        double logA = (a > 0) ? Math.log(a) : Double.NEGATIVE_INFINITY;

        // log(a^c / c! · 1/(1-ρ)) = c·log(a) - logΓ(c+1) - log(1-ρ)
        double logNum = c * logA - logGamma(c + 1) - Math.log(1.0 - rho);

        // Accumulate log(Σ_{k=0}^{c-1} a^k/k!) via log-sum-exp
        double logSum = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < c; k++) {
            double logTerm = (k == 0) ? 0.0 : k * logA - logGamma(k + 1);
            logSum = logSumExp(logSum, logTerm);
        }

        double logDenom = logSumExp(logSum, logNum);
        double result   = Math.exp(logNum - logDenom);
        return (Double.isNaN(result) || result < 0) ? 0.0 : Math.min(result, 1.0);
    }

    /** log(e^a + e^b) — numerically stable, avoids overflow/underflow */
    private double logSumExp(double a, double b) {
        if (a == Double.NEGATIVE_INFINITY) return b;
        if (b == Double.NEGATIVE_INFINITY) return a;
        double max = Math.max(a, b);
        return max + Math.log(Math.exp(a - max) + Math.exp(b - max));
    }

    /** log(Γ(n)) = log((n-1)!) for integer n ≥ 1 */
    private double logGamma(int n) {
        if (n <= 1) return 0.0;
        double r = 0.0;
        for (int i = 2; i < n; i++) r += Math.log(i);
        return r;
    }

    /** E[N_queue] = C(c,ρ)·ρ / (1-ρ) */
    private double erlangC_Nqueue(int c, double rho, double lambda) {
        if (rho >= 1.0) return lambda * 120;
        return erlangC(c, rho) * rho / (1.0 - rho);
    }

    /** E[W_q] = C(c,ρ) / (c·μ·(1-ρ))  [seconds] */
    private double erlangC_Wq(int c, double rho, double mu) {
        if (rho >= 1.0) return 120.0;
        return erlangC(c, rho) / (c * mu * (1.0 - rho));
    }

    /** Estimate E[N_queue] via Little's Law using the given arrival rate. */
    private double estimateQueueLength(int pool, double arrivalRate, double mu) {
        double effectiveMu = (mu > 0) ? mu
                : 1000.0 / (baselineMeanMs > 0 ? baselineMeanMs : SIMULATED_LATENCY_MS);
        double rho = Math.min(arrivalRate / (pool * effectiveMu), 0.9999);
        return erlangC_Nqueue(pool, rho, arrivalRate);
    }

    // ===================================================================
    // HIKARI QUEUE OBSERVER
    // ===================================================================

    /**
     * Ground-truth N_queue: threads currently blocked awaiting a DB connection.
     * Preferred over model-estimated N_queue for α calibration to avoid circularity.
     * Returns 0 if pool is not saturated or MXBean is unavailable.
     */
    private long getHikariPendingThreads() {
        try {
            if (dataSource instanceof HikariDataSource hikari) {
                HikariPoolMXBean bean = hikari.getHikariPoolMXBean();
                if (bean != null) return bean.getThreadsAwaitingConnection();
            }
        } catch (Exception ignored) {}
        return 0;
    }

    // ===================================================================
    // ALPHA LINEARITY
    // ===================================================================

    private String assessAlphaLinearity(Map<Integer, Double> alpha) {
        if (alpha.size() < 2) return "insufficient data";
        double[] v   = alpha.values().stream().mapToDouble(Double::doubleValue).toArray();
        double mean  = Arrays.stream(v).average().orElse(0);
        double maxD  = Arrays.stream(v).map(x -> Math.abs(x - mean)).max().orElse(0);
        double relD  = (mean > 0) ? maxD / mean : 0;
        if (relD < 0.15) return "APPROXIMATELY LINEAR (deviation < 15%)";
        if (relD < 0.40) return "MODERATE NON-LINEARITY (deviation " + String.format("%.0f%%", relD * 100) + ")";
        return "SIGNIFICANT NON-LINEARITY (deviation " + String.format("%.0f%%", relD * 100)
                + " — report as empirical finding)";
    }

    // ===================================================================
    // PREDICTION ACCURACY METRICS
    // ===================================================================

    /**
     * Prints MAPE and RMSE for heap and latency predictions across all validated pools.
     *
     * MAPE (Mean Absolute Percentage Error): scale-independent, directly interpretable.
     * RMSE (Root Mean Squared Error): penalises large deviations more heavily.
     *
     * Both metrics reported for heap and latency separately.
     */
    private void printPredictionAccuracy(String label,
                                         List<Double> heapErrors,
                                         List<Double> latencyErrors) {
        double heapMAPE    = mean(heapErrors);
        double latencyMAPE = mean(latencyErrors);
        double heapRMSE    = Math.sqrt(heapErrors.stream()
                .mapToDouble(e -> e * e).average().orElse(0));
        double latencyRMSE = Math.sqrt(latencyErrors.stream()
                .mapToDouble(e -> e * e).average().orElse(0));

        System.out.println("\n  [" + label + " Prediction Accuracy]");
        System.out.printf("    Heap     MAPE=%.1f%%  RMSE=%.1f%%%n", heapMAPE,    heapRMSE);
        System.out.printf("    Latency  MAPE=%.1f%%  RMSE=%.1f%%%n", latencyMAPE, latencyRMSE);
    }

    // ===================================================================
    // CSV WRITERS
    // ===================================================================

    private PrintWriter openCsvWriter(String filename) throws Exception {
        java.io.File dir = new java.io.File("data");
        if (!dir.exists()) dir.mkdirs();
        return new PrintWriter(new FileWriter(filename, false));
    }

    private void writeRawHeader(PrintWriter w) {
        // Arrival_Rate_RPS ≠ Throughput_RPS under overload
        w.println("Scenario,Pool_Size,Iteration," +
                  "Arrival_Rate_RPS,Throughput_RPS,Mean_Latency_ms,P99_ms," +
                  "Heap_MB,Thread_Count");
    }

    private void writeSummaryHeader(PrintWriter w) {
        w.println("Pool_Size,Phase," +
                  "Mean_Throughput,Std_Throughput," +
                  "Mean_Arrival_Rate_RPS," +
                  "Mean_P99_ms,Std_P99_ms,CI95_P99_ms," +
                  "Mean_Latency_ms,Std_Latency_ms,CI95_Latency_ms," +
                  "Mean_Heap_MB,Std_Heap_MB,CI95_Heap_MB," +
                  "Mean_Threads,Est_NQueue");
    }

    private void writeSummaryRow(PrintWriter w, PoolResult r, String phase) {
        w.printf("%d,%s,%.2f,%.2f,%.2f," +
                 "%.2f,%.2f,%.2f," +
                 "%.2f,%.2f,%.2f," +
                 "%.2f,%.2f,%.2f," +
                 "%.1f,%.2f%n",
                r.poolSize, phase,
                r.meanThroughput, r.stdThroughput,
                r.meanArrivalRate,
                r.meanP99,        r.stdP99,        r.ci95P99,
                r.meanLatencyMs,  r.stdLatencyMs,  r.ci95LatencyMs,
                r.meanHeapMb,     r.stdHeapMb,     r.ci95HeapMb,
                r.meanThreadCount, r.meanQueueLength);
        w.flush();
    }

    private void writeAlphaHeader(PrintWriter w) {
        // NQueue_Source: HIKARI_OBSERVED (ground-truth) vs MODEL_ESTIMATED (fallback)
        w.println("Pool_Size,Mean_Heap_MB,Baseline_Heap_MB,Delta_Heap_MB," +
                  "Est_NQueue,NQueue_Source,Alpha_MB_per_continuation,Linearity_Note");
    }

    private void writeValidationHeader(PrintWriter w) {
        w.println("Pool_Size,Model_rho,Model_P_wait,Model_Region," +
                  // Primary validation target: mean latency (what Erlang C predicts)
                  "Model_Mean_Latency_ms,Measured_Mean_Latency_ms,CI95_Latency_ms,Latency_Error_Pct," +
                  // P99 measured for reference — NOT predicted by this model
                  "Measured_P99_ms,CI95_P99_ms," +
                  // Heap validation
                  "Model_Heap_MB,Measured_Heap_MB,CI95_Heap_MB,Heap_Error_Pct," +
                  "Validation_Pass");
    }

    /**
     * Write one validation row and return [heapErrorPct, latencyErrorPct] for MAPE/RMSE.
     */
    private double[] writeValidationRow(PrintWriter w, int pool,
                                        ModelPrediction pred, PoolResult measured) {
        double latencyErr = Math.abs(pred.meanLatencyMs - measured.meanLatencyMs)
                / Math.max(measured.meanLatencyMs, 1.0) * 100;
        double heapErr    = Math.abs(pred.heapMb - measured.meanHeapMb)
                / Math.max(measured.meanHeapMb, 1.0) * 100;
        boolean pass      = latencyErr < LATENCY_ERROR_THRESHOLD_PCT
                         && heapErr    < HEAP_ERROR_THRESHOLD_PCT;

        w.printf("%d,%.4f,%.4f,%s," +
                 "%.2f,%.2f,±%.2f,%.1f%%," +
                 "%.2f,±%.2f," +
                 "%.2f,%.2f,±%.2f,%.1f%%," +
                 "%s%n",
                pool, pred.rho, pred.pWait, pred.region,
                pred.meanLatencyMs, measured.meanLatencyMs, measured.ci95LatencyMs, latencyErr,
                measured.meanP99, measured.ci95P99,
                pred.heapMb, measured.meanHeapMb, measured.ci95HeapMb, heapErr,
                pass ? "PASS" : "FAIL");
        w.flush();

        System.out.printf(
                "  Pool=%3d | Mean: pred=%.0f vs meas=%.0f±%.1f ms (err=%.1f%%) | " +
                "Heap: pred=%.0f vs meas=%.0f±%.1f MB (err=%.1f%%) | %s%n",
                pool,
                pred.meanLatencyMs, measured.meanLatencyMs, measured.ci95LatencyMs, latencyErr,
                pred.heapMb, measured.meanHeapMb, measured.ci95HeapMb, heapErr,
                pass ? "✓ PASS" : "✗ FAIL");

        return new double[]{heapErr, latencyErr};
    }

    // ===================================================================
    // INFRASTRUCTURE
    // ===================================================================

    private void setPoolSize(int size) throws Exception {
        if (dataSource instanceof HikariDataSource hikari) {
            hikari.setMaximumPoolSize(size);
            hikari.setMinimumIdle(Math.min(size / 4 + 1, size));
            Thread.sleep(500);
        } else {
            throw new IllegalStateException("DataSource is not HikariDataSource");
        }
    }

    private SystemMetrics captureSystemMetrics() {
        MemoryMXBean mem     = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long heap  = mem.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        int  count = threads.getThreadCount();
        return new SystemMetrics(heap, count);
    }

    private double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private double stddev(List<Double> values) {
        double m = mean(values);
        return Math.sqrt(values.stream().mapToDouble(v -> (v - m) * (v - m)).average().orElse(0));
    }

    /**
     * 95% confidence interval half-width: 1.96 × σ / √n.
     * Assumes approximately normal sampling distribution (reasonable for n ≥ 6).
     */
    private double ci95(List<Double> values) {
        int n = values.size();
        return (n < 2) ? 0.0 : 1.96 * stddev(values) / Math.sqrt(n);
    }

    // ===================================================================
    // RECORDS
    // ===================================================================

    /**
     * Raw result from a single iteration.
     * arrivalRate = λ burst approximation (NOT Poisson).
     * meanMs      = mean response time — PRIMARY validation target.
     * p99Ms       = P99 — REFERENCE only, not predicted by model.
     */
    record RunResult(
            double arrivalRate,
            double throughput,
            double meanMs,
            double p99Ms,
            double successRate
    ) {}

    record SystemMetrics(long heapUsedMb, int threadCount) {}

    /**
     * Aggregated result for one pool configuration.
     * CI95 fields allow residual plots with error bars in the paper.
     */
    record PoolResult(
            int    poolSize,
            double meanThroughput,  double stdThroughput,
            double meanArrivalRate,
            double meanP99,         double stdP99,        double ci95P99,
            double meanLatencyMs,   double stdLatencyMs,  double ci95LatencyMs,
            double meanHeapMb,      double stdHeapMb,     double ci95HeapMb,
            double meanThreadCount,
            double meanQueueLength
    ) {}

    /**
     * Model prediction for a given pool.
     * meanLatencyMs = E[W] = E[W_q] + 1/μ  (MEAN response time, NOT P99).
     * pWait         = P(wait) = Erlang C probability — basis for region label.
     */
    record ModelPrediction(
            int    poolSize,
            double rho,
            double pWait,
            double eNqueue,
            double heapMb,
            double meanLatencyMs,
            double alpha,
            String region
    ) {}
}
