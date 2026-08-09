package com.example.likelionhackathon.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LIKE HACKATHON API")
                        .description("멋쟁이사자처럼14기 중앙해커톤 북부대공예티팀 API 명세서")
                        .version("v1.0.0"));
    }
}