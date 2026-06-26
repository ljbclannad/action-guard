package io.github.actionguard.core.runtime;

import io.github.actionguard.api.definition.ActionDefinition;
import io.github.actionguard.api.definition.ActionStepDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionDefinitionValidatorTest {

    private final ActionDefinitionValidator validator = new ActionDefinitionValidator();

    @Test
    void shouldAcceptValidDefinition() {
        ActionDefinition definition = new ActionDefinition(
                "order-cancel-flow",
                "demo",
                false,
                List.of(
                        new ActionStepDefinition("send-cancel-event", "MQ_MESSAGE", "order.cancel.exchange")
                )
        );

        assertThatCode(() -> validator.validate(definition)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectEmptySteps() {
        ActionDefinition definition = new ActionDefinition("order-cancel-flow", "demo", false, List.of());

        assertThatThrownBy(() -> validator.validate(definition))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("steps must not be empty");
    }

    @Test
    void shouldRejectDuplicateStepNames() {
        ActionDefinition definition = new ActionDefinition(
                "order-cancel-flow",
                "demo",
                false,
                List.of(
                        new ActionStepDefinition("notify", "SMS", "notify.user"),
                        new ActionStepDefinition("notify", "EMAIL", "notify.email")
                )
        );

        assertThatThrownBy(() -> validator.validate(definition))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate action step name");
    }
}
