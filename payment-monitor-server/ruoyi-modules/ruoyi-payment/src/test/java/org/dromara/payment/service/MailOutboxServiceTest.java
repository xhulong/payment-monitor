package org.dromara.payment.service;

import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.domain.MailOutboxPayload;
import org.dromara.payment.domain.PmMailOutbox;
import org.dromara.payment.mapper.MailOutboxMapper;
import org.dromara.payment.security.MailOutboxCipher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class MailOutboxServiceTest {

    @Test
    void cipherEncryptsPayloadAndSupportsPreviousKey() {
        PaymentProperties properties = properties();
        MailOutboxCipher cipher = new MailOutboxCipher(properties);
        var encrypted = cipher.encrypt("message-1", "secret-content");

        assertNotEquals("secret-content", encrypted.ciphertext());
        assertEquals(
            "secret-content",
            cipher.decrypt(
                "message-1",
                encrypted.keyId(),
                encrypted.ciphertext()
            )
        );

        properties.getMailOutbox().setPreviousKeyId(encrypted.keyId());
        properties.getMailOutbox().setPreviousKey(
            properties.getMailOutbox().getActiveKey()
        );
        properties.getMailOutbox().setActiveKeyId("mail-v2");
        properties.getMailOutbox().setActiveKey(
            "abcdef0123456789abcdef0123456789"
        );
        assertEquals(
            "secret-content",
            cipher.decrypt(
                "message-1",
                encrypted.keyId(),
                encrypted.ciphertext()
            )
        );
        assertThrows(
            RuntimeException.class,
            () -> cipher.decrypt(
                "wrong-message",
                encrypted.keyId(),
                encrypted.ciphertext()
            )
        );
    }

    @Test
    void enqueueStoresOnlyEncryptedPayload() {
        PaymentProperties properties = properties();
        MailOutboxMapper mapper = mock(MailOutboxMapper.class);
        MailOutboxService service = new MailOutboxService(
            mapper,
            new MailOutboxCipher(properties),
            properties,
            JsonMapper.builder().build()
        );
        ArgumentCaptor<PmMailOutbox> inserted =
            ArgumentCaptor.forClass(PmMailOutbox.class);

        service.enqueueText(
            "PASSWORD_RESET_CODE",
            "user@example.com",
            "Subject",
            "Code 123456",
            "dedupe-1",
            OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5)
        );

        verify(mapper).insert(inserted.capture());
        PmMailOutbox outbox = inserted.getValue();
        assertEquals("PENDING", outbox.getStatus());
        assertEquals(0, outbox.getAttemptCount());
        assertNotEquals("Code 123456", outbox.getPayloadCiphertext());
        assertNotNull(outbox.getEncryptionKeyId());
    }

    @Test
    void workerSendsAndMarksJobSent() {
        Fixture fixture = fixture();
        PmMailOutbox outbox = outbox(fixture);

        fixture.worker.deliver(outbox);

        verify(fixture.client).send(any(MailOutboxPayload.class));
        assertEquals("SENT", outbox.getStatus());
        assertEquals(1, outbox.getAttemptCount());
        assertNotNull(outbox.getSentAt());
        verify(fixture.mapper).updateById(outbox);
    }

    @Test
    void workerRetriesTransientFailureAndStopsAtLimit() {
        Fixture fixture = fixture();
        PmMailOutbox retry = outbox(fixture);
        doThrow(new IllegalStateException("smtp unavailable"))
            .when(fixture.clientFailure)
            .send(any());
        fixture.failureWorker.deliver(retry);
        assertEquals("RETRYING", retry.getStatus());
        assertEquals(1, retry.getAttemptCount());

        PmMailOutbox dead = outbox(fixture);
        dead.setAttemptCount(dead.getMaxAttempts() - 1);
        fixture.failureWorker.deliver(dead);
        assertEquals("DEAD", dead.getStatus());
        assertEquals(dead.getMaxAttempts(), dead.getAttemptCount());
    }

    @Test
    void workerCancelsExpiredJobWithoutSending() {
        Fixture fixture = fixture();
        PmMailOutbox outbox = outbox(fixture);
        outbox.setExpiresAt(
            OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1)
        );

        fixture.worker.deliver(outbox);

        assertEquals("CANCELLED", outbox.getStatus());
        assertEquals("EXPIRED_BEFORE_DELIVERY", outbox.getLastError());
    }

    private Fixture fixture() {
        PaymentProperties properties = properties();
        MailOutboxCipher cipher = new MailOutboxCipher(properties);
        MailOutboxMapper mapper = mock(MailOutboxMapper.class);
        MailDeliveryClient client = mock(MailDeliveryClient.class);
        MailDeliveryClient clientFailure = mock(MailDeliveryClient.class);
        MailSettingsService mailSettingsService =
            mock(MailSettingsService.class);
        when(mailSettingsService.enabled()).thenReturn(true);
        return new Fixture(
            mapper,
            client,
            clientFailure,
            cipher,
            properties,
            new MailOutboxWorker(
                mapper,
                cipher,
                client,
                properties,
                mailSettingsService,
                JsonMapper.builder().build()
            ),
            new MailOutboxWorker(
                mapper,
                cipher,
                clientFailure,
                properties,
                mailSettingsService,
                JsonMapper.builder().build()
            )
        );
    }

    private PmMailOutbox outbox(Fixture fixture) {
        String messageId = "message-" + System.nanoTime();
        MailOutboxPayload payload = new MailOutboxPayload(
            "user@example.com",
            "Subject",
            "Body",
            false
        );
        var encrypted = fixture.cipher.encrypt(
            messageId,
            JsonMapper.builder().build().writeValueAsString(payload)
        );
        PmMailOutbox outbox = new PmMailOutbox();
        outbox.setId(1L);
        outbox.setMessageId(messageId);
        outbox.setMessageType("TEST");
        outbox.setPayloadCiphertext(encrypted.ciphertext());
        outbox.setEncryptionKeyId(encrypted.keyId());
        outbox.setStatus("SENDING");
        outbox.setAttemptCount(0);
        outbox.setMaxAttempts(3);
        outbox.setNextAttemptAt(OffsetDateTime.now(ZoneOffset.UTC));
        outbox.setExpiresAt(
            OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5)
        );
        outbox.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        outbox.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return outbox;
    }

    private PaymentProperties properties() {
        PaymentProperties properties = new PaymentProperties();
        properties.getMailOutbox().setActiveKeyId("mail-v1");
        properties.getMailOutbox().setActiveKey(
            "0123456789abcdef0123456789abcdef"
        );
        properties.getMailOutbox().setInitialRetrySeconds(1);
        properties.getMailOutbox().setMaxRetrySeconds(10);
        return properties;
    }

    private record Fixture(
        MailOutboxMapper mapper,
        MailDeliveryClient client,
        MailDeliveryClient clientFailure,
        MailOutboxCipher cipher,
        PaymentProperties properties,
        MailOutboxWorker worker,
        MailOutboxWorker failureWorker
    ) {
    }
}
