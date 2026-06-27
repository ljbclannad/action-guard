package io.github.actionguard.im;

import io.github.actionguard.api.runtime.ActionStepContext;
import io.github.actionguard.api.runtime.StepExecutionResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ImAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ImAutoConfiguration.class));

    @Test
    void shouldRegisterThreeImHandlers() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ImGroupCreateActionStepHandler.class);
            assertThat(context).hasSingleBean(ImGroupInviteActionStepHandler.class);
            assertThat(context).hasSingleBean(ImGroupMessageSendActionStepHandler.class);
        });
    }

    @Test
    void shouldRouteGroupCreateByTarget() {
        contextRunner.withUserConfiguration(CreateProviderConfiguration.class)
                .run(context -> {
                    ImGroupCreateActionStepHandler handler = context.getBean(ImGroupCreateActionStepHandler.class);

                    StepExecutionResult result = handler.execute(new ActionStepContext(
                            "demo-im",
                            "biz-1",
                            "create-group",
                            "IM_GROUP_CREATE",
                            "mock-im",
                            Map.of(),
                            Map.of(
                                    "groupName", "order-support",
                                    "owner", "u-1",
                                    "members", List.of("u-2", "u-3"),
                                    "metadata", Map.of("scene", "support")
                            )
                    ));

                    assertThat(result.success()).isTrue();
                    assertThat(context.getBean(CapturingCreateSender.class).requests).hasSize(1);
                    assertThat(context.getBean(CapturingCreateSender.class).requests.get(0).target()).isEqualTo("mock-im");
                });
    }

    @Test
    void shouldFailWhenNoMessageProviderMatchesTarget() {
        contextRunner.run(context -> {
            ImGroupMessageSendActionStepHandler handler = context.getBean(ImGroupMessageSendActionStepHandler.class);

            StepExecutionResult result = handler.execute(new ActionStepContext(
                    "demo-im",
                    "biz-1",
                    "send-message",
                    "IM_GROUP_MESSAGE_SEND",
                    "missing-provider",
                    Map.of(),
                    Map.of(
                            "groupId", "g-1",
                            "messageType", "markdown",
                            "content", "hello"
                    )
            ));

            assertThat(result.success()).isFalse();
            assertThat(result.errorCode()).isEqualTo("IM_REQUEST_INVALID");
            assertThat(result.errorMessage()).contains("missing-provider");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CreateProviderConfiguration {

        @Bean
        CapturingCreateSender capturingCreateSender() {
            return new CapturingCreateSender();
        }
    }

    static class CapturingCreateSender implements ImGroupCreateSender {
        private final java.util.List<ImGroupCreateRequest> requests = new java.util.ArrayList<>();

        @Override
        public String provider() {
            return "mock-im";
        }

        @Override
        public ImActionResult create(ImGroupCreateRequest request) {
            requests.add(request);
            return ImActionResult.succeeded();
        }
    }
}
