package io.github.actionguard.ops.web;

import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.core.repository.ActionStepInstanceRepository;
import io.github.actionguard.core.repository.ActionTransitionLogRepository;
import io.github.actionguard.core.repository.InMemoryActionInstanceRepository;
import io.github.actionguard.core.repository.InMemoryActionOutboxRepository;
import io.github.actionguard.core.repository.InMemoryActionStepInstanceRepository;
import io.github.actionguard.core.repository.InMemoryActionTransitionLogRepository;
import io.github.actionguard.core.runtime.compensation.ActionCompensationExecutor;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.util.Optional;

@SpringBootTest(classes = {ActionGuardOpsApplication.class, ActionGuardOpsApplicationTest.TestConfig.class})
class ActionGuardOpsApplicationTest {

    @Test
    void contextLoads() {
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        ActionInstanceRepository actionInstanceRepository() {
            return new InMemoryActionInstanceRepository();
        }

        @Bean
        ActionOutboxRepository actionOutboxRepository() {
            return new InMemoryActionOutboxRepository();
        }

        @Bean
        ActionStepInstanceRepository actionStepInstanceRepository() {
            return new InMemoryActionStepInstanceRepository();
        }

        @Bean
        ActionTransitionLogRepository actionTransitionLogRepository() {
            return new InMemoryActionTransitionLogRepository();
        }

        @Bean
        ActionCompensationExecutor actionCompensationExecutor() {
            return actionInstanceId -> { };
        }

        @Bean
        ActionObservabilityService actionObservabilityService() {
            return new ActionObservabilityService(Optional.empty(), Optional.empty(), Clock.systemUTC());
        }
    }
}
