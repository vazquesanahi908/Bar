package com.barclub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BarClubApplication {
    public static void main(String[] args) {
        SpringApplication.run(BarClubApplication.class, args);
    }
}
