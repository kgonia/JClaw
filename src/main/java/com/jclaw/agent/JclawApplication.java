package com.jclaw.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JclawApplication {

    static void main(String[] args) {
        SpringApplication.run(JclawApplication.class, args);
    }
}
