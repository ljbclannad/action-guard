package io.github.actionguard.demo;

import io.github.actionguard.api.ActionPublisher;
import io.github.actionguard.api.ActionRequest;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class SampleActionFlowRunner implements ApplicationRunner {

    private final ActionPublisher actionPublisher;
    private final ActionInstanceRepository actionInstanceRepository;
    private final ConfigurableApplicationContext applicationContext;

    public SampleActionFlowRunner(
            ActionPublisher actionPublisher,
            ActionInstanceRepository actionInstanceRepository,
            ConfigurableApplicationContext applicationContext
    ) {
        this.actionPublisher = actionPublisher;
        this.actionInstanceRepository = actionInstanceRepository;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String bizKey = "order:demo-" + Instant.now().toEpochMilli();
        actionPublisher.publish(new ActionRequest(
                "demo-notify-success",
                bizKey,
                Map.of("operator", "demo", "phone", "13800000000"),
                List.of()
        ));

        ActionInstance published = actionInstanceRepository.findByActionNameAndBizKey("demo-notify-success", bizKey)
                .orElseThrow(() -> new IllegalStateException("Published action instance not found"));
        ActionInstance completed = awaitSuccess(published.id(), Duration.ofSeconds(10));

        System.out.println("actionName=" + completed.actionName());
        System.out.println("bizKey=" + completed.bizKey());
        System.out.println("status=" + completed.status());

        int exitCode = SpringApplication.exit(applicationContext, () -> 0);
        System.exit(exitCode);
    }

    private ActionInstance awaitSuccess(String actionInstanceId, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            ActionInstance current = actionInstanceRepository.findById(actionInstanceId)
                    .orElseThrow(() -> new IllegalStateException("Action instance not found: " + actionInstanceId));
            if (current.status() == ActionStatus.SUCCESS) {
                return current;
            }
            Thread.sleep(200L);
        }
        ActionInstance current = actionInstanceRepository.findById(actionInstanceId)
                .orElseThrow(() -> new IllegalStateException("Action instance not found: " + actionInstanceId));
        throw new IllegalStateException("Timed out waiting for action success, currentStatus=" + current.status());
    }
}
