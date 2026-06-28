package io.github.actionguard.alert.webhook;

import io.github.actionguard.api.spi.ActionAlertPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookActionAlertAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebhookActionAlertAutoConfiguration.class));

    @Test
    void shouldNotCreatePublisherWhenWebhookDisabled() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(ActionAlertPublisher.class);
        });
    }

    @Test
    void shouldCreatePublisherWhenWebhookEnabled() {
        contextRunner
                .withPropertyValues(
                        "action.guard.alert.webhook.enabled=true",
                        "action.guard.alert.webhook.url=https://example.com/hook"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ActionAlertPublisher.class);
                    assertThat(context).hasSingleBean(ActionGuardWebhookAlertProperties.class);
                });
    }
}
