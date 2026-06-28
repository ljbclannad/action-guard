package io.github.actionguard.ops.api.controller;

import io.github.actionguard.ops.api.service.ActionCommandService;
import io.github.actionguard.ops.api.support.OperatorResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActionCommandController.class)
@ContextConfiguration(classes = {
        ActionCommandController.class,
        ActionCommandControllerTest.TestConfig.class
})
class ActionCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ActionCommandService actionCommandService;

    @MockBean
    private OperatorResolver operatorResolver;

    @Test
    void shouldDelegateRetryCommand() throws Exception {
        mockMvc.perform(post("/api/actions/act-1/retry"))
                .andExpect(status().isOk());

        then(actionCommandService).should().retry("act-1", null);
    }

    @Test
    void shouldDelegateCancelCommand() throws Exception {
        mockMvc.perform(post("/api/actions/act-1/cancel"))
                .andExpect(status().isOk());

        then(actionCommandService).should().cancel("act-1", null);
    }

    @Test
    void shouldDelegateSkipCommand() throws Exception {
        mockMvc.perform(post("/api/actions/act-1/skip"))
                .andExpect(status().isOk());

        then(actionCommandService).should().skip("act-1", null);
    }

    @Test
    void shouldDelegateCompensateCommand() throws Exception {
        mockMvc.perform(post("/api/actions/act-1/compensate"))
                .andExpect(status().isOk());

        then(actionCommandService).should().compensate("act-1", null);
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {
    }
}
