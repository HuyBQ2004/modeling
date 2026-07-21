package swp.se1889.g1.rice_store.research;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * HttpSleepBenchmark — Second blocking type validation (Reviewer response §2b).
 *
 * PURPOSE
 *   Validate that the resource-currency transformation and operating-region boundary (rho* approx 0.70)
 *   are not specific to the SQL Server + JDBC blocking substrate used in Study 1.
 *   This study uses HTTP I/O as the blocking source: each request calls a local Spring endpoint
 *   that performs Thread.sleep(DELAY_MS), consuming the HTTP connection for the full service duration.
 *
 * BLOCKING SUBSTRATE
 *   JDBC + WAITFOR DELAY (Study 1)  =>  HTTP + Thread.sleep (this study)
 *   The M/M/c model applies in both cases: the "c servers" are HTTP connection slots
 *   bounded by a Semaphore of capacity HTTP_CONCURRENCY_LIMIT.
 *
 * PREREQUISITE
 *   The Spring application must expose: GET /research/delay?ms=N
 *   The endpoint calls Thread.sleep(N) on a Tomcat (PT) thread and returns 200 OK.
 *   Use @Profile("!http-sweep") to ensure the endpoint does NOT run with VT executor.
 *
 * DESIGN
 *   - Open-loop Poisson arrivals (same dispatcher design as ArrivalSweepBenchmark)
 *   - Semaphore(HTTP_CONCURRENCY_LIMIT) models the connection pool: acquire blocks when pool full
 *   - Both VT and PT execution models
 *   - Fine-grained rho grid: {0.30, 0.50, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.95, 1.05}
 *   - GC snapshot metrics (same GarbageCollectorMXBean method as ArrivalSweepBenchmark)
 *
 * OUTPUTS (data/)
 *   HTTP_SWEEP_RAW_<ts>.csv      - one row per replicate
 *   HTTP_SWEEP_SUMMARY_<ts>.csv  - per (mode, rho): mean +- std +- CI95
 *
 * RUNTIME
 *   10 rho x 2 modes x 5 reps x (10s warmup + 45s measure) approx 1.5-2 h
 *
 * ACTIVATION
 *   Run with Spring profile: --spring.profiles.active=http-sweep
 */
@Component
@Profile("http-sweep")
public class HttpSleepBenchmark implements CommandLineRunner {

    // ===== CONFIG =====

    /** Simulated service time - same as Study 1 for direct comparability. */
    private static final int    DELAY_MS               = 50;

    /** Semaphore capacity - acts as the "connection pool" for HTTP (analogous to c in M/M/c). */
    private static final int    HTTP_CONCURRENCY_LIMIT = 50;

    // NOTE: Port must match server.port in application.properties (default 9090, not 8080)
    private static final String BASE_URL =
            "http://localhost:9090/mock-latency?ms=" + DELAY_MS;

    private static final double[] RHO_TARGETS = {
        0.30, 0.50, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.95, 1.05
    };

    private static final int REPS            = intProp("http.reps",    20);
    private static final int WARMUP_SECONDS  = intProp("http.warmup",  10);
    private static final int MEASURE_SECONDS = intProp("http.measure", 30);
    private static final int DRAIN_TIMEOUT_S = 120;

    private static final int  SAMPLE_MS        = 200;
    private static final int  SYS_SAMPLE_TICKS = 5;
    private static final int  MAX_IN_FLIGHT     = 10_000;
    private static final long RANDOM_ORDER_SEED = 42L;

    private enum Mode { VT, PT }

    // ===== ESTIMATED MU =====
    private double mu = -1;

    // ===== OUTPUT FILES =====
    private final String ts           = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    private final String FILE_RAW     = "data/HTTP_SWEEP_RAW_"     + ts + ".csv";
    private final String FILE_SUMMARY = "data/HTTP_SWEEP_SUMMARY_" + ts + ".csv";

    private HttpClient httpClient;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("============================================================");
        System.out.println("  HTTP SLEEP BENCHMARK  (second blocking type, rho-targeted)");
        System.out.printf ("  concurrency_limit=%d  delay=%dms  reps=%d  warmup=%ds  measure=%ds%n",
                HTTP_CONCURRENCY_LIMIT, DELAY_MS, REPS, WARMUP_SECONDS, MEASURE_SECONDS);
        System.out.println("  Blocking substrate: HTTP GET /research/delay?ms=" + DELAY_MS);
        System.out.println("============================================================");

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();

        estimateMu();

        // --- Randomized cells ---
        List<Cell> cells = new ArrayList<>();
        for (Mode m : Mode.values())
            for (double rho : RHO_TARGETS)
                for (int rep = 1; rep <= REPS; rep++)
                    cells.add(new Cell(m, rho, rep));
        Collections.shuffle(cells, new Random(RANDOM_ORDER_SEED));

        Map<String, List<RunResult>> byCell = new LinkedHashMap<>();
        try (PrintWriter raw = openCsv(FILE_RAW)) {
            raw.println("Mode,Rho_Target,Rep,Lambda_Target_rps,Lambda_Achieved_rps," +
                        "InterArrival_CV,Dispatched,Completed,Rejected,Success_Pct," +
                        "Throughput_rps,Mean_Latency_ms,P99_ms," +
                        "Nq_Semaphore_Mean,Nq_Semaphore_Max,Little_Nq_Check," +
                        "Heap_Mean_MB,Heap_Max_MB,Threads_Mean,Threads_Max," +
                        "GC_Collections,GC_Pause_Total_ms,GC_Pause_Mean_ms," +
                        "Alloc_Rate_MB_s,Heap_Delta_MB");
            int i = 0;
            for (Cell cell : cells) {
                i++;
                double lambda = cell.rho * HTTP_CONCURRENCY_LIMIT * mu;
                System.out.printf("%n[%d/%d] mode=%s rho=%.2f rep=%d  lambda_target=%.1f rps%n",
                        i, cells.size(), cell.mode, cell.rho, cell.rep, lambda);
                RunResult r = runCell(cell.mode, lambda);
                writeRawRow(raw, cell, r, lambda);
                byCell.computeIfAbsent(cell.mode + "|" + cell.rho, k -> new ArrayList<>()).add(r);
            }
        }

        writeSummary(byCell);

        System.out.println("\n============================================================");
        System.out.printf("  DONE. mu=%.4f req/s/conn (E[S]=%.1f ms)%n", mu, 1000.0 / mu);
        System.out.printf("  Files: %s%n         %s%n", FILE_RAW, FILE_SUMMARY);
        System.out.println("============================================================");
    }

    // ===================================================================
    // mu ESTIMATION
    // ===================================================================

    private void estimateMu() throws Exception {
        System.out.println("\n--- Baseline: estimating mu via HTTP sleep at low concurrency ---");
        System.out.println("  Endpoint: " + BASE_URL);

        // Connectivity check: fail fast if server is not reachable
        try {
            doHttpRequest();
            System.out.println("  [OK] Server reachable.");
        } catch (Exception e) {
            throw new IllegalStateException(
                "[FATAL] Cannot reach " + BASE_URL + "\n" +
                "  Check that the Spring server is running on the correct port.\n" +
                "  server.port in application.properties = 9090, " +
                "not 8080. Error: " + e.getMessage(), e);
        }

        // JIT warmup: 50 sequential requests
        for (int i = 0; i < 50; i++) {
            try { doHttpRequest(); } catch (Exception ignored) {}
        }
        // Estimate E[S] from 100 sequential requests (concurrency ~ 1, so rho ~ 0)
        List<Long> lats = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            long t = System.nanoTime();
            try { doHttpRequest(); } catch (Exception ignored) {}
            lats.add(System.nanoTime() - t);
        }
        double meanMs = lats.stream().mapToLong(Long::longValue).average()
                .orElse(DELAY_MS * 1_000_000.0) / 1_000_000.0;
        // Safety: if meanMs is 0 or unreasonably small, fall back to theoretical value
        if (meanMs < 1.0) {
            System.out.printf("  [WARN] Measured E[S]=%.2f ms is too small; " +
                    "using theoretical DELAY_MS=%d ms%n", meanMs, DELAY_MS);
            meanMs = DELAY_MS;
        }
        mu = 1000.0 / meanMs;
        System.out.printf("  E[S]=%.1f ms  ->  mu=%.4f req/s/conn  (HTTP baseline)%n", meanMs, mu);
    }

    // ===================================================================
    // ONE MEASUREMENT CELL
    // ===================================================================

    private RunResult runCell(Mode mode, double lambda) throws Exception {
        System.gc();
        Thread.sleep(800);

        // Semaphore models the "connection pool" of capacity c = HTTP_CONCURRENCY_LIMIT
        final Semaphore pool = new Semaphore(HTTP_CONCURRENCY_LIMIT, true);

        ExecutorService executor = (mode == Mode.VT)
                ? Executors.newVirtualThreadPerTaskExecutor()
                : Executors.newThreadPerTaskExecutor(
                        Thread.ofPlatform().name("pt-http-", 0).factory());

        final long warmupNanos  = TimeUnit.SECONDS.toNanos(WARMUP_SECONDS);
        final long measureNanos = TimeUnit.SECONDS.toNanos(MEASURE_SECONDS);
        final long t0           = System.nanoTime();
        final long measureStart = t0 + warmupNanos;
        final long measureEnd   = measureStart + measureNanos;

        final List<Long>    latencies      = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger inFlight       = new AtomicInteger();
        final AtomicInteger dispatchedMeas = new AtomicInteger();
        final AtomicInteger completedMeas  = new AtomicInteger();
        final AtomicInteger successMeas    = new AtomicInteger();
        final AtomicInteger rejected       = new AtomicInteger();

        // GC snapshot BEFORE measurement window
        GcSnapshot gcBefore = snapshotGc();
        long heapAtWindowStart = captureHeapMb();

        // Background sampler: semaphore queue depth (proxy for N_q), heap, threads
        final List<Long> nqSamples     = Collections.synchronizedList(new ArrayList<>());
        final List<Long> heapSamples   = Collections.synchronizedList(new ArrayList<>());
        final List<Long> threadSamples = Collections.synchronizedList(new ArrayList<>());
        final Thread sampler = Thread.ofPlatform().daemon().name("http-sampler").start(() -> {
            int tick = 0;
            while (!Thread.currentThread().isInterrupted()) {
                long now = System.nanoTime();
                if (now >= measureStart && now < measureEnd) {
                    // Semaphore queue length = threads/VTs waiting for a pool slot
                    nqSamples.add((long) pool.getQueueLength());
                    if (++tick % SYS_SAMPLE_TICKS == 0) {
                        heapSamples.add(captureHeapMb());
                        threadSamples.add((long) ManagementFactory.getThreadMXBean().getThreadCount());
                    }
                }
                if (now >= measureEnd) break;
                try { Thread.sleep(SAMPLE_MS); } catch (InterruptedException e) { break; }
            }
        });

        // Dispatcher: exponential inter-arrival gaps, absolute-deadline scheduling
        final List<Long> realizedGaps = new ArrayList<>();
        long nextDeadline = t0;
        long prevDispatch = -1;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        System.out.printf("  [DEBUG] runCell mode=%s lambda=%.2f starting dispatcher. Warmup %ds, Measure %ds.%n",
                mode, lambda, WARMUP_SECONDS, MEASURE_SECONDS);

        while (nextDeadline < measureEnd) {
            long now = System.nanoTime();
            while (now < nextDeadline) {
                long remaining = nextDeadline - now;
                if (remaining > 2_000_000L) LockSupport.parkNanos(remaining - 2_000_000L);
                else Thread.onSpinWait();
                now = System.nanoTime();
            }
            final long dispatchTime = now;
            final boolean inWindow  = dispatchTime >= measureStart && dispatchTime < measureEnd;
            if (inWindow) {
                if (prevDispatch > 0) realizedGaps.add(dispatchTime - prevDispatch);
                prevDispatch = dispatchTime;
            }

            if (inFlight.get() >= MAX_IN_FLIGHT) {
                if (inWindow) rejected.incrementAndGet();
            } else {
                inFlight.incrementAndGet();
                if (inWindow) dispatchedMeas.incrementAndGet();
                try {
                    executor.submit(() -> {
                        long s = System.nanoTime();
                        boolean ok = false;
                        try {
                            // Acquire semaphore slot: this IS the queuing event (blocks when pool full)
                            // Under VT: parks the continuation; carrier thread released
                            // Under PT: blocks the OS thread
                            pool.acquire();
                            try {
                                doHttpRequest();
                                ok = true;
                            } finally {
                                pool.release();
                            }
                        } catch (Exception ignored) {}
                        finally {
                            long lat = System.nanoTime() - s;
                            if (inWindow) {
                                latencies.add(lat);
                                completedMeas.incrementAndGet();
                                if (ok) successMeas.incrementAndGet();
                            }
                            inFlight.decrementAndGet();
                        }
                    });
                } catch (Throwable t) {
                    inFlight.decrementAndGet();
                    if (inWindow) { dispatchedMeas.decrementAndGet(); rejected.incrementAndGet(); }
                }
            }

            double gapSec = Math.min(-Math.log(1.0 - rnd.nextDouble()) / lambda, 10.0);
            nextDeadline += (long) (gapSec * 1e9);
        }

        System.out.printf("  [DEBUG] Dispatcher loop finished. Dispatched: %d (meas: %d), InFlight currently: %d.%n",
                dispatchedMeas.get() + (mode == Mode.VT ? 0 : 0), dispatchedMeas.get(), inFlight.get());

        // Drain in-flight requests
        System.out.println("  [DEBUG] Entering drain loop...");
        long drainDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DRAIN_TIMEOUT_S);
        long lastPrint = 0;
        while (inFlight.get() > 0 && System.nanoTime() < drainDeadline) {
            long now = System.nanoTime();
            if (now - lastPrint > 1_000_000_000L) { // print every 1 second
                System.out.printf("    [DEBUG] Draining... inFlight=%d, elapsed=%d/%ds%n",
                        inFlight.get(),
                        TimeUnit.NANOSECONDS.toSeconds(now - measureEnd),
                        DRAIN_TIMEOUT_S);
                lastPrint = now;
            }
            Thread.sleep(100);
        }
        System.out.printf("  [DEBUG] Drain finished. Final inFlight=%d.%n", inFlight.get());

        sampler.interrupt();
        executor.shutdownNow();

        // GC snapshot AFTER measurement window
        GcSnapshot gcAfter = snapshotGc();
        long heapAtWindowEnd = captureHeapMb();

        long   gcCollections  = gcAfter.totalCollections - gcBefore.totalCollections;
        long   gcPauseTotalMs = gcAfter.totalPauseMs     - gcBefore.totalPauseMs;
        double gcPauseMeanMs  = gcCollections > 0 ? (double) gcPauseTotalMs / gcCollections : 0.0;
        double heapDeltaMb    = heapAtWindowEnd - heapAtWindowStart;
        double allocRateMbS   = heapSamples.isEmpty() ? 0
                : (heapSamples.stream().mapToLong(Long::longValue).max().orElse(0)
                   - heapAtWindowStart) / (double) MEASURE_SECONDS;

        double windowSec      = MEASURE_SECONDS;
        double achievedLambda = dispatchedMeas.get() / windowSec;

        double gapMean = realizedGaps.stream().mapToLong(Long::longValue).average().orElse(0);
        double gapStd  = stddevL(realizedGaps, gapMean);
        double gapCv   = gapMean > 0 ? gapStd / gapMean : 0;

        List<Long> sorted = new ArrayList<>(latencies);
        sorted.sort(Long::compare);
        double meanMs = sorted.isEmpty() ? 0
                : sorted.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6;
        double p99Ms = sorted.isEmpty() ? 0
                : sorted.get(Math.min(sorted.size() - 1, (int) (sorted.size() * 0.99))) / 1e6;

        double nqMean  = nqSamples.stream().mapToLong(Long::longValue).average().orElse(0);
        long   nqMax   = nqSamples.stream().mapToLong(Long::longValue).max().orElse(0);
        double heapMean= heapSamples.stream().mapToLong(Long::longValue).average().orElse(0);
        long   heapMax = heapSamples.stream().mapToLong(Long::longValue).max().orElse(0);
        double thrMean = threadSamples.stream().mapToLong(Long::longValue).average().orElse(0);
        long   thrMax  = threadSamples.stream().mapToLong(Long::longValue).max().orElse(0);

        double baselineMeanMs = 1000.0 / mu;
        double wqMeasSec      = Math.max(meanMs - baselineMeanMs, 0) / 1000.0;
        double littleNq       = achievedLambda * wqMeasSec;
        double throughput     = completedMeas.get() / windowSec;
        double successPct     = dispatchedMeas.get() > 0
                ? successMeas.get() * 100.0 / dispatchedMeas.get() : 0;

        System.out.printf(
                "  lambda: target=%.1f achieved=%.1f (CV=%.2f) | tp=%.1f rps | mean=%.0f ms " +
                "P99=%.0f ms | Nq=%.1f (max %d, Little=%.1f) | heap=%.0f MB | threads=%.0f" +
                " | GC: %d events %.0f ms | allocRate=%.1f MB/s%s%n",
                lambda, achievedLambda, gapCv, throughput, meanMs, p99Ms,
                nqMean, nqMax, littleNq, heapMean, thrMean,
                gcCollections, (double) gcPauseTotalMs, allocRateMbS,
                rejected.get() > 0 ? " | REJECTED=" + rejected.get() : "");

        return new RunResult(achievedLambda, gapCv,
                dispatchedMeas.get(), completedMeas.get(), rejected.get(), successPct,
                throughput, meanMs, p99Ms, nqMean, nqMax, littleNq,
                heapMean, heapMax, thrMean, thrMax,
                gcCollections, gcPauseTotalMs, gcPauseMeanMs, allocRateMbS, heapDeltaMb);
    }

    // ===================================================================
    // HTTP REQUEST
    // ===================================================================

    private void doHttpRequest() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }

    // ===================================================================
    // AGGREGATION
    // ===================================================================

    private void writeSummary(Map<String, List<RunResult>> byCell) throws Exception {
        try (PrintWriter sum = openCsv(FILE_SUMMARY)) {
            sum.println("Mode,Rho_Target,Reps,Lambda_Achieved_Mean,InterArrival_CV_Mean," +
                        "Throughput_Mean,Mean_Latency_ms,Std_Latency_ms,CI95_Latency_ms," +
                        "P99_ms_Mean,Nq_Mean,Std_Nq,CI95_Nq,Little_Nq_Mean," +
                        "Heap_Mean_MB,Heap_Max_MB,Threads_Mean,Threads_Max,Rejected_Total," +
                        "GC_Collections_Mean,GC_Pause_Total_ms_Mean,GC_Pause_Mean_ms," +
                        "Alloc_Rate_MB_s_Mean,Heap_Delta_MB_Mean");

            for (Mode mode : Mode.values()) {
                for (double rho : RHO_TARGETS) {
                    List<RunResult> rs = byCell.get(mode + "|" + rho);
                    if (rs == null || rs.isEmpty()) continue;
                    List<Double> lat = rs.stream().map(r -> r.meanMs).toList();
                    List<Double> nq  = rs.stream().map(r -> r.nqMean).toList();
                    double latMean = mean(lat), latStd = stddev(lat), latCi = ci95(lat);
                    double nqMean  = mean(nq),  nqStd  = stddev(nq),  nqCi  = ci95(nq);
                    sum.printf(Locale.US,
                            "%s,%.2f,%d,%.2f,%.3f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f," +
                            "%.1f,%d,%.1f,%d,%d,%.2f,%.2f,%.2f,%.2f,%.2f%n",
                            mode, rho, rs.size(),
                            rs.stream().mapToDouble(r -> r.achievedLambda).average().orElse(0),
                            rs.stream().mapToDouble(r -> r.gapCv).average().orElse(0),
                            rs.stream().mapToDouble(r -> r.throughput).average().orElse(0),
                            latMean, latStd, latCi,
                            rs.stream().mapToDouble(r -> r.p99Ms).average().orElse(0),
                            nqMean, nqStd, nqCi,
                            rs.stream().mapToDouble(r -> r.littleNq).average().orElse(0),
                            rs.stream().mapToDouble(r -> r.heapMean).average().orElse(0),
                            rs.stream().mapToLong(r -> r.heapMax).max().orElse(0),
                            rs.stream().mapToDouble(r -> r.thrMean).average().orElse(0),
                            rs.stream().mapToLong(r -> r.thrMax).max().orElse(0),
                            rs.stream().mapToInt(r -> r.rejected).sum(),
                            rs.stream().mapToDouble(r -> r.gcCollections).average().orElse(0),
                            rs.stream().mapToDouble(r -> r.gcPauseTotalMs).average().orElse(0),
                            rs.stream().mapToDouble(r -> r.gcPauseMeanMs).average().orElse(0),
                            rs.stream().mapToDouble(r -> r.allocRateMbS).average().orElse(0),
                            rs.stream().mapToDouble(r -> r.heapDeltaMb).average().orElse(0));
                }
            }
        }
    }

    // ===================================================================
    // INFRASTRUCTURE
    // ===================================================================

    private void writeRawRow(PrintWriter raw, Cell c, RunResult r, double lambdaTarget) {
        raw.printf(Locale.US,
                "%s,%.2f,%d,%.2f,%.2f,%.3f,%d,%d,%d,%.1f,%.2f,%.2f,%.2f," +
                "%.2f,%d,%.2f,%.1f,%d,%.1f,%d," +
                "%d,%d,%.2f,%.2f,%.2f%n",
                c.mode, c.rho, c.rep, lambdaTarget, r.achievedLambda, r.gapCv,
                r.dispatched, r.completed, r.rejected, r.successPct,
                r.throughput, r.meanMs, r.p99Ms,
                r.nqMean, r.nqMax, r.littleNq,
                r.heapMean, r.heapMax, r.thrMean, r.thrMax,
                r.gcCollections, r.gcPauseTotalMs, r.gcPauseMeanMs,
                r.allocRateMbS, r.heapDeltaMb);
        raw.flush();
    }

    private GcSnapshot snapshotGc() {
        long totalCollections = 0;
        long totalPauseMs     = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = gc.getCollectionCount();
            long time  = gc.getCollectionTime();
            if (count > 0) totalCollections += count;
            if (time  > 0) totalPauseMs     += time;
        }
        return new GcSnapshot(totalCollections, totalPauseMs);
    }

    private long captureHeapMb() {
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        return mem.getHeapMemoryUsage().getUsed() / (1024 * 1024);
    }

    private PrintWriter openCsv(String filename) throws Exception {
        java.io.File dir = new java.io.File("data");
        if (!dir.exists()) dir.mkdirs();
        return new PrintWriter(new FileWriter(filename, false));
    }

    private double mean(List<Double> v) {
        return v.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private double stddev(List<Double> v) {
        double m = mean(v);
        return Math.sqrt(v.stream().mapToDouble(x -> (x - m) * (x - m)).average().orElse(0));
    }

    private double stddevL(List<Long> v, double m) {
        return Math.sqrt(v.stream().mapToDouble(x -> (x - m) * (x - m)).average().orElse(0));
    }

    private double ci95(List<Double> v) {
        int n = v.size();
        return (n < 2) ? 0.0 : 1.96 * stddev(v) / Math.sqrt(n);
    }

    private static int intProp(String key, int def) {
        try { return Integer.parseInt(System.getProperty(key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }

    // ===================================================================
    // RECORDS
    // ===================================================================

    private record Cell(Mode mode, double rho, int rep) {}

    private record GcSnapshot(long totalCollections, long totalPauseMs) {}

    private record RunResult(
            double achievedLambda, double gapCv,
            int dispatched, int completed, int rejected, double successPct,
            double throughput, double meanMs, double p99Ms,
            double nqMean, long nqMax, double littleNq,
            double heapMean, long heapMax, double thrMean, long thrMax,
            long gcCollections, long gcPauseTotalMs, double gcPauseMeanMs,
            double allocRateMbS, double heapDeltaMb) {}
}
