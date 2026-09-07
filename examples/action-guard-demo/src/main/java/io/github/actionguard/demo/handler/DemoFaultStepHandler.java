package io.github.actionguard.demo.handler;

import io.github.actionguard.api.runtime.ActionStepContext;
import io.github.actionguard.api.runtime.StepExecutionResult;
import io.github.actionguard.api.spi.ActionStepHandler;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.core.repository.ActionStepInstanceRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 仅用于故障演示，读取已落库尝试次数，使短暂失败场景不依赖进程内计数。 */
@Component
@Profile("fault-demo")
public class DemoFaultStepHandler implements ActionStepHandler {
    private final ActionInstanceRepository actions;
    private final ActionStepInstanceRepository steps;

    public DemoFaultStepHandler(ActionInstanceRepository actions, ActionStepInstanceRepository steps) {
        this.actions = actions;
        this.steps = steps;
    }

    @Override
    public String stepType() {
        return "DEMO_FAULT";
    }

    @Override
    public StepExecutionResult execute(ActionStepContext context) {
        return switch (context.target()) {
            case "success" -> StepExecutionResult.succeeded();
            case "always-fail" -> StepExecutionResult.failed("DEMO_DOWNSTREAM_UNAVAILABLE", "模拟下游持续不可用");
            case "fail-once" -> {
                var action = actions.findByActionNameAndBizKey(context.actionName(), context.bizKey()).orElseThrow();
                var step = steps.findByActionInstanceId(action.id()).stream()
                        .filter(item -> item.stepName().equals(context.stepName())).findFirst().orElseThrow();
                yield step.attemptCount() == 0
                        ? StepExecutionResult.failed("DEMO_TRANSIENT_FAILURE", "模拟下游首次调用失败")
                        : StepExecutionResult.succeeded();
            }
            default -> throw new IllegalArgumentException("未知的故障演示目标：" + context.target());
        };
    }
}
