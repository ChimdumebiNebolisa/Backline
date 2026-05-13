package dev.backline.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Backline API")
                        .version("1.0.0")
                        .description(
                                "Terminal-first API regression ledger that stores every API check run in PostgreSQL "
                                        + "and exposes REST endpoints for projects, checks, runs, history, and diffs "
                                        + "so developers can inspect how API behavior changes over time."));
    }
}
