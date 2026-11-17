package com.lingani;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan 
@MapperScan("com.lingani.mapper")
@EnableScheduling
public class LingAniApplication {

    public static void main(String[] args) {
        SpringApplication.run(LingAniApplication.class, args);
    }
}
