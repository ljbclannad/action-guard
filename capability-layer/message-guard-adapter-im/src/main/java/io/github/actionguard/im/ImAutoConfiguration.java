package io.github.actionguard.im;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
public class ImAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ImGroupCreateActionStepHandler imGroupCreateActionStepHandler(List<ImGroupCreateSender> senders) {
        return new ImGroupCreateActionStepHandler(senders);
    }

    @Bean
    @ConditionalOnMissingBean
    ImGroupInviteActionStepHandler imGroupInviteActionStepHandler(List<ImGroupInviteSender> senders) {
        return new ImGroupInviteActionStepHandler(senders);
    }

    @Bean
    @ConditionalOnMissingBean
    ImGroupMessageSendActionStepHandler imGroupMessageSendActionStepHandler(List<ImGroupMessageSender> senders) {
        return new ImGroupMessageSendActionStepHandler(senders);
    }
}
