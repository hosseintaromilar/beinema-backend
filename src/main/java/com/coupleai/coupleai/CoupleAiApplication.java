package com.coupleai.coupleai;

import com.coupleai.coupleai.beinema.governance.GovernanceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(GovernanceProperties.class)
public class CoupleAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoupleAiApplication.class, args);
    }

}
