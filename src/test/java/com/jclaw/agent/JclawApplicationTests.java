package com.jclaw.agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "jclaw.tui.enabled=false")
class JclawApplicationTests {

    @Test
    void contextLoads() {
    }

}
