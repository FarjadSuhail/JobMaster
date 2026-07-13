package com.example.jobmaster;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "jobmaster.watcher.enabled=false")
@ActiveProfiles({"mock", "h2"})
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
