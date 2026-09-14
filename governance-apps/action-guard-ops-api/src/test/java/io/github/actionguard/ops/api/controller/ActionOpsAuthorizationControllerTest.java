package io.github.actionguard.ops.api.controller;

import io.github.actionguard.ops.api.security.ActionOpsAuthorizationInterceptor;
import io.github.actionguard.ops.api.security.ActionOpsPermission;
import io.github.actionguard.ops.api.security.ActionOpsPrincipal;
import io.github.actionguard.ops.api.security.ActionOpsPrincipalResolver;
import io.github.actionguard.ops.api.service.ActionAuditService;
import io.github.actionguard.ops.api.service.ActionCommandService;
import io.github.actionguard.ops.api.service.ActionQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActionOpsAuthorizationControllerTest {

    @Test
    void shouldRejectGovernanceQueryWhenResolverIsMissing() throws Exception {
        MockMvc mvc = mvc(null);

        mvc.perform(get("/api/actions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectAuditQueryWithoutReadPermission() throws Exception {
        MockMvc mvc = mvc(new ActionOpsPrincipal("operator-1", Set.of(ActionOpsPermission.RETRY)));

        mvc.perform(get("/api/audit-logs"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowActionQueryWithReadPermission() throws Exception {
        ActionQueryService actionQueryService = mock(ActionQueryService.class);
        MockMvc mvc = mvc(new ActionOpsPrincipal("operator-1", Set.of(ActionOpsPermission.READ)), actionQueryService,
                mock(ActionAuditService.class), mock(ActionCommandService.class));

        mvc.perform(get("/api/actions"))
                .andExpect(status().isOk());

        verify(actionQueryService).list(any());
    }

    @Test
    void shouldRejectAlertOutboxQueryWithoutReadPermission() throws Exception {
        MockMvc mvc = mvc(new ActionOpsPrincipal("operator-1", Set.of(ActionOpsPermission.RETRY)));

        mvc.perform(get("/api/actions/action-1/alert-outboxes"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnAlertOutboxesWithReadPermission() throws Exception {
        ActionQueryService actionQueryService = mock(ActionQueryService.class);
        when(actionQueryService.alertOutboxes("action-1")).thenReturn(List.of());
        MockMvc mvc = mvc(new ActionOpsPrincipal("operator-1", Set.of(ActionOpsPermission.READ)), actionQueryService,
                mock(ActionAuditService.class), mock(ActionCommandService.class));

        mvc.perform(get("/api/actions/action-1/alert-outboxes"))
                .andExpect(status().isOk());

        verify(actionQueryService).alertOutboxes("action-1");
    }

    @Test
    void shouldRejectCommandWithoutMatchingPermission() throws Exception {
        MockMvc mvc = mvc(new ActionOpsPrincipal("operator-1", Set.of(ActionOpsPermission.READ)));

        mvc.perform(post("/api/actions/action-1/skip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"人工确认可跳过\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldPassVerifiedOperatorAndReasonToCommandService() throws Exception {
        ActionCommandService actionCommandService = mock(ActionCommandService.class);
        MockMvc mvc = mvc(new ActionOpsPrincipal("operator-1", Set.of(ActionOpsPermission.SKIP)),
                mock(ActionQueryService.class), mock(ActionAuditService.class), actionCommandService);

        mvc.perform(post("/api/actions/action-1/skip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"人工确认可跳过\"}"))
                .andExpect(status().isOk());

        verify(actionCommandService).skip("action-1", "operator-1", "人工确认可跳过");
    }

    @Test
    void shouldRejectBlankReasonBeforeCallingCommandService() throws Exception {
        ActionCommandService actionCommandService = mock(ActionCommandService.class);
        MockMvc mvc = mvc(new ActionOpsPrincipal("operator-1", Set.of(ActionOpsPermission.RETRY)),
                mock(ActionQueryService.class), mock(ActionAuditService.class), actionCommandService);

        mvc.perform(post("/api/actions/action-1/retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verify(actionCommandService, org.mockito.Mockito.never()).retry(eq("action-1"), any(), any());
    }

    private MockMvc mvc(ActionOpsPrincipal principal) {
        return mvc(principal, mock(ActionQueryService.class), mock(ActionAuditService.class), mock(ActionCommandService.class));
    }

    private MockMvc mvc(
            ActionOpsPrincipal principal,
            ActionQueryService actionQueryService,
            ActionAuditService actionAuditService,
            ActionCommandService actionCommandService
    ) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        if (principal != null) {
            beanFactory.registerBeanDefinition("principalResolver", new RootBeanDefinition(ActionOpsPrincipalResolver.class,
                    () -> request -> Optional.of(principal)));
        }
        return MockMvcBuilders.standaloneSetup(
                        new ActionQueryController(actionQueryService),
                        new ActionAuditController(actionAuditService),
                        new ActionCommandController(actionCommandService))
                .addInterceptors(new ActionOpsAuthorizationInterceptor(
                        beanFactory.getBeanProvider(ActionOpsPrincipalResolver.class)))
                .build();
    }
}
