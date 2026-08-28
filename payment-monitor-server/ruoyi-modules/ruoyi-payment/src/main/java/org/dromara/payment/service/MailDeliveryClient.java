package org.dromara.payment.service;

import org.dromara.payment.domain.MailOutboxPayload;

public interface MailDeliveryClient {
    void send(MailOutboxPayload payload);
}
