package dev.backline.api.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ensures ISO-8601 date/time strings in JSON. {@link com.fasterxml.jackson.datatype.jsr310.JavaTimeModule} is
 * auto-registered by Spring Boot when Jackson JSR-310 support is on the classpath, so this configuration only
 * toggles timestamp serialization off explicitly.
 */
@Configuration
public class JacksonConfig {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
