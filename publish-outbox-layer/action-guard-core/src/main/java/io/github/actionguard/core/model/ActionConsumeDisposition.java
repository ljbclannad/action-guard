package io.github.actionguard.core.model;

/** 消费处理后交给消息通道的处置决策，不是持久化消费状态。 */
public enum ActionConsumeDisposition {
    /** 确认消息，无需由消息通道重新投递；不表示整个 Action 已成功。 */
    ACK,
    /** 请求消息通道重试投递，不等同于调度业务步骤重试。 */
    RETRY,
    /** 请求按消息通道的死信规则处理。 */
    DEAD_LETTER
}
