package com.coupleai.coupleai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CoupleAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoupleAiApplication.class, args);
    }

}
