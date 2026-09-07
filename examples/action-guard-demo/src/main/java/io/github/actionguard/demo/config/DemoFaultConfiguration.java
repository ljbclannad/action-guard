package io.github.actionguard.demo.config;

import io.github.actionguard.ops.api.config.ActionOpsApiConfiguration;
import io.github.actionguard.ops.api.controller.ActionAuditController;
import io.github.actionguard.ops.api.controller.ActionCommandController;
import io.github.actionguard.ops.api.controller.ActionQueryController;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("fault-demo")
@Import({ActionOpsApiConfiguration.class, ActionQueryController.class,
        ActionCommandController.class, ActionAuditController.class})
public class DemoFaultConfiguration {
}
