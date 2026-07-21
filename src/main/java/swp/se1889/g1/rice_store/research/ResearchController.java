package swp.se1889.g1.rice_store.research;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ResearchController — exposes the /research/delay endpoint used by HttpSleepBenchmark.
 *
 * DESIGN NOTE
 *   The endpoint intentionally uses Thread.sleep() (blocking call) rather than reactive/async.
 *   This ensures the HTTP connection is genuinely HELD for the full delay duration on a Tomcat
 *   platform thread, making connection hold-time the quantity directly modeled by M/M/c — the
 *   same invariant enforced by WAITFOR DELAY in Study 1 (ArrivalSweepBenchmark).
 *
 *   When the CLIENT uses Virtual Threads (HttpSleepBenchmark VT mode), the VT is blocked
 *   waiting for the HTTP response — the carrier thread is released. The server-side blocking
 *   is irrelevant to the client-side currency transformation being studied.
 *
 * USAGE
 *   GET /research/delay?ms=50  -> blocks 50 ms, returns 200 OK with body "ok"
 *   GET /research/delay?ms=100 -> blocks 100 ms, returns 200 OK with body "ok"
 *   Max delay capped at 10,000 ms for safety.
 */
@RestController
@RequestMapping("/research")
public class ResearchController {

    private static final int MAX_DELAY_MS = 10_000;

    /**
     * Simulates a blocking I/O operation of the specified duration.
     * Used as the blocking substrate in HttpSleepBenchmark (Study 3).
     *
     * @param ms delay in milliseconds (default 50, max 10000)
     * @return 200 OK with body "ok" after the delay
     */
    @GetMapping("/delay")
    public ResponseEntity<String> delay(
            @RequestParam(name = "ms", defaultValue = "50") int ms) throws InterruptedException {
        int clampedMs = Math.min(Math.max(ms, 0), MAX_DELAY_MS);
        Thread.sleep(clampedMs);
        return ResponseEntity.ok("ok");
    }

    /**
     * Health check endpoint — returns immediately.
     * Used by HttpSleepBenchmark.estimateMu() to verify the server is reachable
     * before starting the experiment.
     */
    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }
}
