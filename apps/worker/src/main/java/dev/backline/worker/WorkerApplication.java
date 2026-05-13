package dev.backline.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the Backline worker.
 *
 * <p>The worker does not expose HTTP endpoints; it polls PostgreSQL and executes checks. There is
 * no Actuator HTTP health server in this module, so liveness is observed via the process and logs.
 */
@SpringBootApplication
public class WorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}
