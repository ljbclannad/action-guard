package io.github.actionguard.core.runtime;

import io.github.actionguard.api.runtime.ActionCompensationContext;
import io.github.actionguard.api.runtime.ActionCompensationResult;
import io.github.actionguard.api.spi.ActionCompensator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActionCompensatorRegistryTest {

    @Test
    void shouldRegisterCompensatorByStepType() {
        ActionCompensatorRegistry registry = new ActionCompensatorRegistry(List.of(new TestCompensator("SMS")));

        assertThat(registry.find("SMS")).isPresent();
    }

    private record TestCompensator(String stepType) implements ActionCompensator {
        @Override
        public ActionCompensationResult compensate(ActionCompensationContext context) {
            return ActionCompensationResult.success("ok");
        }
    }
}
