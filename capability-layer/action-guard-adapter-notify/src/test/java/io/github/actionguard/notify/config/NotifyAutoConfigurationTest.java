package io.github.actionguard.notify.config;

import io.github.actionguard.api.runtime.ActionStepContext;
import io.github.actionguard.api.runtime.StepExecutionResult;
import io.github.actionguard.notify.handler.NotifyEmailActionStepHandler;
import io.github.actionguard.notify.handler.NotifyInAppActionStepHandler;
import io.github.actionguard.notify.handler.NotifySmsActionStepHandler;
import io.github.actionguard.notify.model.NotifySendResult;
import io.github.actionguard.notify.model.NotifySmsRequest;
import io.github.actionguard.notify.sender.NotifySmsSender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NotifyAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(NotifyAutoConfiguration.class));

    @Test
    void shouldRegisterThreeNotifyHandlers() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(NotifyInAppActionStepHandler.class);
            assertThat(context).hasSingleBean(NotifySmsActionStepHandler.class);
            assertThat(context).hasSingleBean(NotifyEmailActionStepHandler.class);
        });
    }

    @Test
    void shouldRouteSmsByTarget() {
        contextRunner.withUserConfiguration(SmsProviderConfiguration.class)
                .run(context -> {
                    NotifySmsActionStepHandler handler = context.getBean(NotifySmsActionStepHandler.class);

                    StepExecutionResult result = handler.execute(new ActionStepContext(
                            "demo-notify",
                            "biz-1",
                            "send-sms",
                            "NOTIFY_SMS_SEND",
                            "mock-sms",
                            Map.of(),
                            Map.of(
                                    "phoneNumbers", List.of("13800000000"),
                                    "templateId", "order-cancel",
                                    "variables", Map.of("orderId", "1")
                            )
                    ));

                    assertThat(result.success()).isTrue();
                    assertThat(context.getBean(CapturingSmsSender.class).requests).hasSize(1);
                    assertThat(context.getBean(CapturingSmsSender.class).requests.get(0).target()).isEqualTo("mock-sms");
                });
    }

    @Test
    void shouldFailWhenNoSmsProviderMatchesTarget() {
        contextRunner.run(context -> {
            NotifySmsActionStepHandler handler = context.getBean(NotifySmsActionStepHandler.class);

            StepExecutionResult result = handler.execute(new ActionStepContext(
                    "demo-notify",
                    "biz-1",
                    "send-sms",
                    "NOTIFY_SMS_SEND",
                    "missing-provider",
                    Map.of(),
                    Map.of(
                            "phoneNumbers", List.of("13800000000"),
                            "templateId", "order-cancel"
                    )
            ));

            assertThat(result.success()).isFalse();
            assertThat(result.errorCode()).isEqualTo("NOTIFY_REQUEST_INVALID");
            assertThat(result.errorMessage()).contains("missing-provider");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class SmsProviderConfiguration {

        @Bean
        CapturingSmsSender capturingSmsSender() {
            return new CapturingSmsSender();
        }
    }

    static class CapturingSmsSender implements NotifySmsSender {
        private final java.util.List<NotifySmsRequest> requests = new java.util.ArrayList<>();

        @Override
        public String provider() {
            return "mock-sms";
        }

        @Override
        public NotifySendResult send(NotifySmsRequest request) {
            requests.add(request);
            return NotifySendResult.succeeded();
        }
    }
}
