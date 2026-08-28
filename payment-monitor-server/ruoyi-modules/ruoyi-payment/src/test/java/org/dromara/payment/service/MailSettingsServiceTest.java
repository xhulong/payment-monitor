package org.dromara.payment.service;

import org.dromara.common.mail.config.properties.MailProperties;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.domain.PmMailServerConfig;
import org.dromara.payment.mapper.MailServerConfigMapper;
import org.dromara.payment.security.MailSettingsCipher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class MailSettingsServiceTest {

    @Test
    void databaseSettingsOverrideEnvironmentAndDoNotExposePassword() {
        PaymentProperties properties = properties();
        MailSettingsCipher cipher = new MailSettingsCipher(properties);
        var encrypted = cipher.encrypt("database-secret");
        PmMailServerConfig saved = new PmMailServerConfig();
        saved.setId(1L);
        saved.setEnabled(true);
        saved.setHost("smtp.database.test");
        saved.setPort(465);
        saved.setAuthEnabled(true);
        saved.setUsername("mailer");
        saved.setPasswordCiphertext(encrypted.ciphertext());
        saved.setEncryptionKeyId(encrypted.keyId());
        saved.setFromName("LuLuPay");
        saved.setFromAddress("noreply@example.test");
        saved.setSecurityMode("SSL");
        saved.setConnectionTimeoutMs(5000L);
        saved.setReadTimeoutMs(6000L);

        MailServerConfigMapper mapper = mock(MailServerConfigMapper.class);
        when(mapper.selectById(1L)).thenReturn(saved);
        MailProperties fallback = new MailProperties();
        fallback.setEnabled(false);
        fallback.setHost("smtp.environment.test");

        MailSettingsService service = new MailSettingsService(
            mapper,
            cipher,
            fallback,
            mock(AccountMfaService.class),
            new MailTemplateService()
        );

        var resolved = service.current();
        var view = service.view();
        assertEquals("DATABASE", resolved.source());
        assertEquals("smtp.database.test", resolved.host());
        assertEquals("database-secret", resolved.password());
        assertTrue(view.passwordConfigured());
        assertFalse(view.toString().contains("database-secret"));
    }

    @Test
    void environmentSettingsRemainStartupFallbackUntilDatabaseRowExists() {
        MailServerConfigMapper mapper = mock(MailServerConfigMapper.class);
        when(mapper.selectById(1L)).thenReturn(null);
        MailProperties fallback = new MailProperties();
        fallback.setEnabled(true);
        fallback.setHost("smtp.environment.test");
        fallback.setPort(587);
        fallback.setAuth(true);
        fallback.setUser("mailer@example.test");
        fallback.setPass("environment-secret");
        fallback.setFrom("LuLuPay <mailer@example.test>");
        fallback.setStarttlsEnable(true);

        MailSettingsService service = new MailSettingsService(
            mapper,
            new MailSettingsCipher(properties()),
            fallback,
            mock(AccountMfaService.class),
            new MailTemplateService()
        );

        var resolved = service.current();
        assertEquals("ENVIRONMENT", resolved.source());
        assertEquals("smtp.environment.test", resolved.host());
        assertEquals("STARTTLS", resolved.securityMode());
        assertTrue(resolved.enabled());
        assertTrue(resolved.passwordConfigured());
    }

    private PaymentProperties properties() {
        PaymentProperties properties = new PaymentProperties();
        properties.getMailSettings().setActiveKeyId("mail-settings-v1");
        properties.getMailSettings().setActiveKey(
            "0123456789abcdef0123456789abcdef"
        );
        return properties;
    }
}
