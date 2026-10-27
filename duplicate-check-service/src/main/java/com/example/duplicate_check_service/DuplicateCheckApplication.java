// DuplicateCheckApplication.java
package com.example.duplicate_check_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DuplicateCheckApplication {
    public static void main(String[] args) {
        SpringApplication.run(DuplicateCheckApplication.class, args);
    }
}