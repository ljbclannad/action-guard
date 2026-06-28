package io.github.actionguard.core.runtime;

import io.github.actionguard.api.runtime.ActionStepContext;
import io.github.actionguard.api.runtime.StepExecutionResult;
import io.github.actionguard.api.spi.ActionStepHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepHandlerRegistryTest {

    @Test
    void shouldFindRegisteredHandlerByStepType() {
        ActionStepHandler smsHandler = new TestActionStepHandler("SMS");
        StepHandlerRegistry registry = new StepHandlerRegistry(List.of(smsHandler));

        assertThat(registry.find("SMS")).containsSame(smsHandler);
        assertThat(registry.getRequired("SMS")).isSameAs(smsHandler);
    }

    @Test
    void shouldRejectDuplicateStepTypeHandlers() {
        assertThatThrownBy(() -> new StepHandlerRegistry(List.of(
                new TestActionStepHandler("SMS"),
                new TestActionStepHandler("SMS")
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate ActionStepHandler");
    }

    @Test
    void shouldFailWhenRequiredHandlerMissing() {
        StepHandlerRegistry registry = new StepHandlerRegistry(List.of());

        assertThatThrownBy(() -> registry.getRequired("SMS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No ActionStepHandler registered");
    }

    private record TestActionStepHandler(String stepType) implements ActionStepHandler {

        @Override
        public StepExecutionResult execute(ActionStepContext context) {
            return StepExecutionResult.succeeded();
        }
    }
}
