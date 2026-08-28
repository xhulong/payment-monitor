package org.dromara.payment.event;

import java.time.OffsetDateTime;

/**
 * 首次持久化收款通知后发布的内部事件。
 *
 * <p>后续订单匹配和 Webhook 可直接订阅该事件，无需修改设备上传协议。</p>
 */
public record PaymentIncomeReceivedEvent(
    Long merchantId,
    Long deviceId,
    String clientEventId,
    String platform,
    Long amountMinor,
    Long eventTimeMs,
    Long clientReceivedAtMs,
    Long clientSentAtMs,
    OffsetDateTime receivedAt
) {
}
