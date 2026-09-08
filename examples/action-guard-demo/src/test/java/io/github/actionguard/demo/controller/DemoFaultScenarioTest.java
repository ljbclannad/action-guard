package io.github.actionguard.demo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.core.runtime.execution.ActionExecutionCallback;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageFactory;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageProducer;
import io.github.actionguard.core.runtime.execution.ActionOutboxDispatcher;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import io.github.actionguard.demo.ActionGuardDemoApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 使用真实 H2 仓储及执行、治理链路，消息传输由测试显式驱动。 */
@SpringBootTest(classes = ActionGuardDemoApplication.class, properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.sql.init.mode=always",
        "spring.datasource.url=jdbc:h2:mem:demo_fault_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false",
        "action.guard.recovery.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("fault-demo")
class DemoFaultScenarioTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ActionExecutionCallback callback;
    @Autowired ActionOutboxRepository outboxes;
    @Autowired ActionObservabilityService observability;
    @MockBean ActionExecutionMessageProducer producer;
    @MockBean Clock clock;
    private ActionOutboxDispatcher dispatcher;

    private final Instant start = Instant.parse("2026-09-07T00:00:00Z");

    @BeforeEach
    void resetClock() {
        when(clock.instant()).thenReturn(start);
        dispatcher = new ActionOutboxDispatcher(outboxes, Optional.of(producer), observability, clock);
    }

    @Test
    void shouldRetrySecondStepWithoutRepeatingFirstStep() throws Exception {
        String id = publish("auto-retry");
        execute(id);
        execute(id);
        assertSteps(id, "FAILED", 1);
        var retry = outboxes.findByActionInstanceId(id).orElseThrow();
        assertThat(dispatcher.dispatch(retry, 1)).isFalse();
        when(clock.instant()).thenReturn(start.plusSeconds(6));
        assertThat(dispatcher.dispatch(retry, 1)).isTrue();
        execute(id);
        assertSteps(id, "SUCCESS", 2);
        mvc.perform(get("/api/actions/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void shouldSkipWhileRetryingAndPersistOperatorAudit() throws Exception {
        String id = publish("manual-skip");
        execute(id);
        execute(id);
        assertSteps(id, "FAILED", 1);
        mvc.perform(get("/api/actions/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETRYING"));
        mvc.perform(post("/api/actions/{id}/skip", id).header("X-Action-Guard-Operator", "demo-operator"))
                .andExpect(status().isOk());
        execute(id);
        assertSteps(id, "SUCCESS", 1);
        mvc.perform(get("/api/actions/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        mvc.perform(get("/api/audit-logs").param("actionInstanceId", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].operationType").value("SKIP"))
                .andExpect(jsonPath("$.items[0].operator").value("demo-operator"));
    }

    @Test
    void shouldRejectUnknownScenario() throws Exception {
        mvc.perform(post("/api/demo/scenarios/unknown")).andExpect(status().isBadRequest());
    }

    private String publish(String scenario) throws Exception {
        String body = mvc.perform(post("/api/demo/scenarios/{scenario}", scenario))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("actionInstanceId").asText();
    }

    private void execute(String id) {
        callback.execute(new ActionExecutionMessageFactory().create(outboxes.findByActionInstanceId(id).orElseThrow()));
    }

    private void assertSteps(String id, String secondStatus, int secondAttempts) throws Exception {
        mvc.perform(get("/api/actions/{id}/steps", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$[0].attemptCount").value(1))
                .andExpect(jsonPath("$[1].status").value(secondStatus))
                .andExpect(jsonPath("$[1].attemptCount").value(secondAttempts));
    }
}
