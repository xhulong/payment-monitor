package org.dromara.payment.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.mail.core.MailBuilder;
import org.dromara.payment.domain.MailOutboxPayload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmtpMailDeliveryClient implements MailDeliveryClient {
    private final MailSettingsService settingsService;

    @Override
    public void send(MailOutboxPayload payload) {
        MailSettingsService.ResolvedMailSettings settings =
            settingsService.current();
        if (!settings.enabled()) {
            throw new IllegalStateException("mail service disabled");
        }
        MailBuilder.of(settings.toMailAccount())
            .to(payload.recipient())
            .subject(payload.subject())
            .content(payload.content(), payload.html())
            .send();
    }
}
