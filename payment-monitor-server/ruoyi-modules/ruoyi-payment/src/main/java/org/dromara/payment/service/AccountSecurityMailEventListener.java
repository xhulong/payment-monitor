package org.dromara.payment.service;

import lombok.RequiredArgsConstructor;
import org.dromara.system.event.AccountSecurityEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountSecurityMailEventListener {
    private final MailNotificationPublisher publisher;

    @EventListener
    public void handle(AccountSecurityEvent event) {
        if (AccountSecurityEvent.PASSWORD_CHANGED.equals(event.type())) {
            publisher.passwordChanged(event.email(), event.userId());
        }
    }
}
