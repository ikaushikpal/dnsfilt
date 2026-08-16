package com.dnsfilt.dnsadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DnsFiltAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(DnsFiltAdminApplication.class, args);
    }
}
