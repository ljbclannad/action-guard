package io.github.actionguard.api.spi;

import io.github.actionguard.api.runtime.ActionAlertEvent;

/**
 * 告警外部通道的一次发送 SPI。
 *
 * <p>实现只负责一次通道调用；调用失败必须抛出异常，由可靠告警 Outbox 统一完成退避、重试和 DEAD 状态处理。
 * 实现不得自行重试、重新入队或回调告警观测入口。</p>
 */
public interface ActionAlertSender {

    void send(ActionAlertEvent event);
}
