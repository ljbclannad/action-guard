package io.github.actionguard.ops.api.controller;

import io.github.actionguard.ops.api.model.ActionListItem;
import io.github.actionguard.ops.api.model.AuditLogView;
import io.github.actionguard.ops.api.model.CompensationLogView;
import io.github.actionguard.ops.api.model.PageResult;
import io.github.actionguard.ops.api.service.ActionAuditService;
import io.github.actionguard.ops.api.service.ActionQueryService;
import io.github.actionguard.core.model.ActionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ActionQueryController.class, ActionAuditController.class})
@ContextConfiguration(classes = {
        ActionQueryController.class,
        ActionAuditController.class,
        ActionQueryControllerTest.TestConfig.class
})
class ActionQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ActionQueryService actionQueryService;

    @MockBean
    private ActionAuditService actionAuditService;

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {
    }

    @Test
    void shouldReturnPagedActionList() throws Exception {
        given(actionQueryService.list(any())).willReturn(new PageResult<>(
                List.of(new ActionListItem(
                        "act-1",
                        "order-cancel-flow",
                        "order:1",
                        ActionStatus.SUCCESS,
                        2,
                        2,
                        null,
                        null,
                        Instant.parse("2026-06-26T12:00:00Z"),
                        Instant.parse("2026-06-26T12:00:00Z")
                )),
                1,
                1,
                20
        ));

        mockMvc.perform(get("/api/actions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].actionName").value("order-cancel-flow"));
    }

    @Test
    void shouldReturnPagedAuditLogList() throws Exception {
        given(actionAuditService.query(any())).willReturn(new PageResult<>(
                List.of(new AuditLogView(
                        "audit-1",
                        "act-1",
                        "RETRY",
                        "anonymous",
                        "{\"reason\":\"manual retry\"}",
                        "SUCCESS",
                        "ok",
                        Instant.parse("2026-06-26T12:00:00Z")
                )),
                1,
                1,
                20
        ));

        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].operationType").value("RETRY"));
    }

    @Test
    void shouldReturnCompensationLogList() throws Exception {
        given(actionQueryService.compensations("act-1")).willReturn(List.of(
                new CompensationLogView(
                        "batch-1",
                        1,
                        "send-user-sms",
                        "SMS",
                        "SUCCESS",
                        "SmsCompensator",
                        "ok",
                        Instant.parse("2026-06-26T12:00:00Z")
                )
        ));

        mockMvc.perform(get("/api/actions/act-1/compensations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].compensationBatchId").value("batch-1"))
                .andExpect(jsonPath("$[0].compensationStatus").value("SUCCESS"));
    }
}
