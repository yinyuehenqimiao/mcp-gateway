package com.demo.biz.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI demoOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Demo Biz API")
                .description("简单单体业务接口：商品 / 订单增删改查，供 MCP Gateway 导入学习")
                .version("1.0.0")
                .contact(new Contact().name("demo-biz")));
    }
}
