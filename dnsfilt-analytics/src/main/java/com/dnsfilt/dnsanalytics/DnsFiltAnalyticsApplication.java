package com.dnsfilt.dnsanalytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DnsFiltAnalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(DnsFiltAnalyticsApplication.class, args);
    }
}
