package io.github.actionguard.demo.config;

import io.github.actionguard.ops.api.config.ActionOpsApiConfiguration;
import io.github.actionguard.ops.api.controller.ActionAuditController;
import io.github.actionguard.ops.api.controller.ActionCommandController;
import io.github.actionguard.ops.api.controller.ActionQueryController;
import io.github.actionguard.ops.api.security.ActionOpsPermission;
import io.github.actionguard.ops.api.security.ActionOpsPrincipal;
import io.github.actionguard.ops.api.security.ActionOpsPrincipalResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

import java.util.Optional;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@Profile("fault-demo")
@Import({ActionOpsApiConfiguration.class, ActionQueryController.class,
        ActionCommandController.class, ActionAuditController.class})
public class DemoFaultConfiguration {

    @Bean
    ActionOpsPrincipalResolver faultDemoActionOpsPrincipalResolver() {
        ActionOpsPrincipal principal = new ActionOpsPrincipal(
                "fault-demo-operator", Set.of(ActionOpsPermission.values()));
        return request -> Optional.of(principal);
    }
}
