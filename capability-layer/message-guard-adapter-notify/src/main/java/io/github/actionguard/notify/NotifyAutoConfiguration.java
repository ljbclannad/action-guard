package io.github.actionguard.notify;

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
