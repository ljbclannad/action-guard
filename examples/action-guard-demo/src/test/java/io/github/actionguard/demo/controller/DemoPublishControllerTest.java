package io.github.actionguard.demo.controller;

import io.github.actionguard.demo.ActionGuardDemoApplication;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ActionGuardDemoApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "demo.runner.enabled=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.sql.init.mode=always",
        "spring.datasource.url=jdbc:h2:mem:demo_publish_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false",
        "action.guard.recovery.enabled=false"
})
class DemoPublishControllerTest {

    @MockBean
    private ActionExecutionMessageProducer producer;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldKeepFaultAndGovernanceEndpointsDisabledByDefault() throws Exception {
        mockMvc.perform(post("/api/demo/scenarios/auto-retry")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/audit-logs")).andExpect(status().isNotFound());
    }

    @Test
    void shouldPublishActionThroughHttpEndpoint() throws Exception {
        MvcResult publishResult = mockMvc.perform(post("/api/publish")
                        .contentType("application/json")
                        .content("""
                                {
                                  "actionName": "demo-notify-success",
                                  "bizKey": "order:curl-test",
                                  "phoneNumber": "13800000000"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionName").value("demo-notify-success"))
                .andExpect(jsonPath("$.bizKey").value("order:curl-test"))
                .andReturn();

        String responseBody = publishResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("\"actionInstanceId\"\\s*:\\s*\"([^\"]+)\"").matcher(responseBody);
        assertThat(matcher.find()).isTrue();
        String actionInstanceId = matcher.group(1);

        assertThat(actionInstanceId).isNotBlank();
        mockMvc.perform(get("/api/actions/{id}", actionInstanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionInstanceId").value(actionInstanceId));
    }
}
