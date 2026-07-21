package swp.se1889.g1.rice_store.research;

import com.zaxxer.hikari.HikariDataSource;
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

@Component
@Profile("sensitivity")
public class PoolSizeSensitivityBenchmark implements CommandLineRunner {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private InvoicesRepository invoicesRepository;

    // ===== CONFIG =====
    private static final int[] CONCURRENT_USERS = {4000, 8000};
    private static final int TOTAL_ITERATIONS = 5;
    private static final int WARMUP_ROUNDS = 1;
    private static final long TEST_STORE_ID = 1L;
    private static final int SIMULATED_LATENCY_MS = 50;

    private final String timeStamp =
            new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    private final String OUTPUT =
            "research_result_POOL_SENSITIVITY_" + timeStamp + ".csv";

    @Override
    public void run(String... args) throws Exception {

        // ===== OVERRIDE HIKARI POOL SIZE (KHÔNG ĐỤNG application.properties) =====
        int poolSize = 100;
        if (dataSource instanceof HikariDataSource hikari) {
            hikari.setMaximumPoolSize(poolSize);
            hikari.setMinimumIdle(poolSize / 2);
        } else {
            throw new IllegalStateException("DataSource is not HikariDataSource");
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(OUTPUT, true))) {

            writer.println(
                    "Scenario,Concurrency,Iteration,Throughput_RPS,P99_ms,Success_Rate,Heap_MB,Thread_Count,Pool_Size"
            );

            List<Scenario> scenarios = List.of(
                    new Scenario("PT_IO_Wait", false),
                    new Scenario("VT_IO_Wait", true)
            );

            for (Scenario scenario : scenarios) {
                for (int concurrency : CONCURRENT_USERS) {

                    ExecutorService executor = scenario.virtualThreads
                            ? Executors.newVirtualThreadPerTaskExecutor()
                            : Executors.newFixedThreadPool(concurrency);

                    int totalRequests = concurrency * 10;

                    for (int i = 1; i <= TOTAL_ITERATIONS; i++) {

                        System.gc();
                        Thread.sleep(500);

                        RunMetrics metrics =
                                runExperiment(executor, scenario, totalRequests);

                        SystemMetrics sys = captureSystemMetrics();

                        if (i > WARMUP_ROUNDS) {
                            writer.printf(
                                    "%s,%d,%d,%.2f,%.2f,%.2f,%d,%d,%d%n",
                                    scenario.name,
                                    concurrency,
                                    i,
                                    metrics.throughput,
                                    metrics.p99,
                                    metrics.successRate,
                                    sys.heapUsedMb,
                                    sys.threadCount,
                                    poolSize
                            );
                            writer.flush();
                        }
                    }

                    executor.shutdownNow();
                }
            }
        }

        System.out.println("=== POOL SIZE SENSITIVITY BENCHMARK COMPLETED ===");
    }

    // ===== CORE WORKLOAD =====
    private RunMetrics runExperiment(
            ExecutorService executor,
            Scenario scenario,
            int totalRequests
    ) throws InterruptedException {

        CountDownLatch latch = new CountDownLatch(totalRequests);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger success = new AtomicInteger();

        long start = System.nanoTime();

        for (int i = 0; i < totalRequests; i++) {
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

        latch.await(180, TimeUnit.SECONDS);

        double durationSec =
                (System.nanoTime() - start) / 1_000_000_000.0;

        double throughput = success.get() / durationSec;
        double successRate = success.get() * 100.0 / totalRequests;

        latencies.sort(Long::compare);
        double p99 = latencies.isEmpty()
                ? 0
                : latencies.get((int) (latencies.size() * 0.99)) / 1_000_000.0;

        return new RunMetrics(throughput, p99, successRate);
    }

    private void performIoTask() throws Exception {
        Pageable limit = PageRequest.of(0, 20);
        invoicesRepository.findTop5000JPQLDTO(TEST_STORE_ID, limit);
        Thread.sleep(SIMULATED_LATENCY_MS);
    }

    // ===== METRICS =====
    private SystemMetrics captureSystemMetrics() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        long heap =
                memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        int threads = threadBean.getThreadCount();

        return new SystemMetrics(heap, threads);
    }

    // ===== RECORDS =====
    record Scenario(String name, boolean virtualThreads) {
    }

    record RunMetrics(double throughput, double p99, double successRate) {
    }

    record SystemMetrics(long heapUsedMb, int threadCount) {
    }
}
