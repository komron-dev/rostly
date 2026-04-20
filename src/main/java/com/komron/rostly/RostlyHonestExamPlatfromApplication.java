package com.komron.rostly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RostlyHonestExamPlatfromApplication {

    public static void main(String[] args) {
        SpringApplication.run(RostlyHonestExamPlatfromApplication.class, args);
    }

}
