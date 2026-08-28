package org.dromara.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class MailNotificationEventListener {
    private final MailOutboxService outboxService;
    private final MailSettingsService settingsService;

    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT,
        fallbackExecution = true
    )
    public void handle(MailNotificationEvent event) {
        if (!settingsService.enabled()) {
            log.warn(
                "Skip informational mail because mail service is disabled, type={}",
                event.messageType()
            );
            return;
        }
        try {
            outboxService.enqueueHtml(
                event.messageType(),
                event.recipient(),
                event.subject(),
                event.html(),
                event.deduplicationKey(),
                event.expiresAt()
            );
        } catch (RuntimeException exception) {
            log.error(
                "Unable to enqueue informational mail, type={}",
                event.messageType(),
                exception
            );
        }
    }
}
