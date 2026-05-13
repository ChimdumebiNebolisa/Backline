package dev.backline.worker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backline.executor.HttpCheckExecutor;
import dev.backline.worker.loop.WorkerLoop;
import dev.backline.worker.persistence.WorkerRunDao;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Worker wiring: JDBC transactions, HTTP client, check executor, and the polling loop.
 *
 * <p>The worker module intentionally does not expose an HTTP port. Health is inferred from the
 * process staying up and logs; see {@link WorkerLoop}.
 */
@Configuration
@EnableConfigurationProperties(WorkerProperties.class)
public class WorkerConfig {

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    HttpClient workerHttpClient(WorkerProperties props) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getHttpConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean
    HttpCheckExecutor httpCheckExecutor(WorkerProperties props, ObjectMapper mapper, HttpClient workerHttpClient) {
        return new HttpCheckExecutor(workerHttpClient, mapper, Duration.ofMillis(props.getHttpRequestTimeoutMs()));
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnProperty(name = "backline.worker.loop-enabled", havingValue = "true", matchIfMissing = true)
    WorkerLoop workerLoop(WorkerProperties props, WorkerRunDao dao, HttpCheckExecutor executor, ObjectMapper mapper) {
        return new WorkerLoop(props, dao, executor, mapper);
    }
}
