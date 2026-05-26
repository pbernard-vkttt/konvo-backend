package com.vulkantechtt.konvo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class KonvoCrmApplication {

    public static void main(String[] args) {
        SpringApplication.run(KonvoCrmApplication.class, args);
    }
}
