package io.github.actionguard.notify.config;

import io.github.actionguard.notify.handler.NotifyEmailActionStepHandler;
import io.github.actionguard.notify.handler.NotifyInAppActionStepHandler;
import io.github.actionguard.notify.handler.NotifySmsActionStepHandler;
import io.github.actionguard.notify.sender.NotifyEmailSender;
import io.github.actionguard.notify.sender.NotifyInAppSender;
import io.github.actionguard.notify.sender.NotifySmsSender;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
public class NotifyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    NotifyInAppActionStepHandler notifyInAppActionStepHandler(List<NotifyInAppSender> senders) {
        return new NotifyInAppActionStepHandler(senders);
    }

    @Bean
    @ConditionalOnMissingBean
    NotifySmsActionStepHandler notifySmsActionStepHandler(List<NotifySmsSender> senders) {
        return new NotifySmsActionStepHandler(senders);
    }

    @Bean
    @ConditionalOnMissingBean
    NotifyEmailActionStepHandler notifyEmailActionStepHandler(List<NotifyEmailSender> senders) {
        return new NotifyEmailActionStepHandler(senders);
    }
}
