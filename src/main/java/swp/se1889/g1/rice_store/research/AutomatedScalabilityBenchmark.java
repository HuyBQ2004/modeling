package swp.se1889.g1.rice_store.research;

import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import swp.se1889.g1.rice_store.repository.InvoicesRepository;

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
@Profile("benchmark")
public class AutomatedScalabilityBenchmark implements CommandLineRunner {

    private final InvoicesRepository invoicesRepository;

    // CONFIG CHO JOURNAL IST
    private static final int[] CONCURRENT_USERS_LIST = {100, 500, 1000, 2000, 4000, 8000};
    private static final int TOTAL_ITERATIONS = 10;
    private static final int WARMUP_ROUNDS = 3;
    private static final Long TEST_STORE_ID = 1L;

    // Giả lập độ trễ IO thực tế (50ms)
    private static final int SIMULATED_LATENCY_MS = 50;

    private final String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    private final String FILE_RAW = "research_result_FULL_METRICS_" + timeStamp + ".csv";

    public AutomatedScalabilityBenchmark(InvoicesRepository invoicesRepository) {
        this.invoicesRepository = invoicesRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== IST FULL BENCHMARK STARTED ===");
        System.out.println("Data file: " + FILE_RAW);

        try (PrintWriter rawWriter = new PrintWriter(new FileWriter(FILE_RAW, true))) {
            // Header full metrics
            rawWriter.println("Scenario,Type,Concurrency,Iteration,Throughput_RPS,P99_ms,Success_Rate,Heap_MB,Thread_Count");

            List<TestScenario> scenarios = List.of(
                    new TestScenario("PT_CPU_Heavy", false, TaskType.CPU_BOUND),
                    new TestScenario("VT_CPU_Heavy", true, TaskType.CPU_BOUND),
                    new TestScenario("PT_IO_Wait", false, TaskType.IO_BOUND),
                    new TestScenario("VT_IO_Wait", true, TaskType.IO_BOUND)
            );

            for (int concurrency : CONCURRENT_USERS_LIST) {
                int totalRequests = concurrency * 10;

                for (TestScenario scenario : scenarios) {
                    System.out.printf("Running %s - Users: %d... ", scenario.name, concurrency);

                    ExecutorService executor = scenario.useVirtualThreads
                            ? Executors.newVirtualThreadPerTaskExecutor()
                            : Executors.newFixedThreadPool(concurrency);

                    for (int i = 1; i <= TOTAL_ITERATIONS; i++) {
                        // Clean up
                        System.gc();
                        Thread.sleep(500);

                        RunMetrics metrics = runExperiment(executor, scenario, concurrency, totalRequests);

                        // ĐO TÀI NGUYÊN HỆ THỐNG NGAY LÚC NÀY
                        SystemMetrics sysMetrics = captureSystemMetrics();

                        if (i > WARMUP_ROUNDS) {
                            rawWriter.printf("%s,%s,%d,%d,%.2f,%.2f,%.2f,%d,%d%n",
                                    scenario.name, scenario.type, concurrency, i,
                                    metrics.throughput, metrics.p99, metrics.successRate,
                                    sysMetrics.heapUsedMb, sysMetrics.threadCount);
                            rawWriter.flush();
                        }
                    }
                    executor.shutdownNow();
                    System.out.println("DONE");
                }
            }
        }
        System.out.println("=== BENCHMARK COMPLETED ===");
    }

    private RunMetrics runExperiment(ExecutorService executor, TestScenario scenario, int concurrency, int totalRequests) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(totalRequests);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);

        long start = System.nanoTime();

        for (int k = 0; k < totalRequests; k++) {
            executor.submit(() -> {
                long t0 = System.nanoTime();
                try {
                    if (scenario.type == TaskType.CPU_BOUND) {
                        performCpuTask();
                    } else {
                        performIoTask();
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Nuốt lỗi timeout để chương trình không dừng
                    // System.err.println(e.getMessage());
                } finally {
                    latencies.add(System.nanoTime() - t0);
                    latch.countDown();
                }
            });
        }

        // Chờ tối đa 180s cho kịch bản 8000 users xếp hàng
        latch.await(180, TimeUnit.SECONDS);

        long durationNs = System.nanoTime() - start;
        double durationSec = durationNs / 1_000_000_000.0;

        double throughput = successCount.get() / durationSec;
        double successRate = (double) successCount.get() / totalRequests * 100;

        Collections.sort(latencies);
        double p99 = latencies.isEmpty() ? 0 : latencies.get((int) (latencies.size() * 0.99)) / 1_000_000.0;

        return new RunMetrics(throughput, p99, successRate);
    }

    private SystemMetrics captureSystemMetrics() {
        // Đo Heap Memory
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);

        // Đo Live Thread Count (Đây là số liệu giết chết PT)
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        int threadCount = threadBean.getThreadCount();

        return new SystemMetrics(heapUsed, threadCount);
    }

    private void performIoTask() throws InterruptedException {
        // Query DTO nhẹ (20 rows)
        Pageable limit = PageRequest.of(0, 20);
        invoicesRepository.findTop5000JPQLDTO(TEST_STORE_ID, limit);

        // Blocking giả lập
        Thread.sleep(SIMULATED_LATENCY_MS);
    }

    private void performCpuTask() {
        double result = 0;
        for (int i = 0; i < 2000; i++) {
            result += Math.sin(i) * Math.cos(i);
        }
    }

    // Records
    record TestScenario(String name, boolean useVirtualThreads, TaskType type) {
    }

    record RunMetrics(double throughput, double p99, double successRate) {
    }

    record SystemMetrics(long heapUsedMb, int threadCount) {
    }

    enum TaskType {CPU_BOUND, IO_BOUND}
}