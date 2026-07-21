package swp.se1889.g1.rice_store.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MockDelayController {

    // API này giả lập độ trễ mạng thực tế
    // Virtual Threads sẽ unmount khi chờ phản hồi từ API này
    // WebFlux sẽ giải phóng thread khi chờ phản hồi từ API này
    @GetMapping("/mock-latency")
    public String mockLatency(@RequestParam int ms) throws InterruptedException {
        Thread.sleep(ms); // Server side sleep là ok, quan trọng là Client side gọi thế nào
        return "Done";
    }
}