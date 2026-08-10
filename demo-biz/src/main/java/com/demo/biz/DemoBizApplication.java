package com.demo.biz;

import com.demo.biz.auth.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class DemoBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoBizApplication.class, args);
    }
}
