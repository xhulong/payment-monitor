package org.dromara.payment.service;

import cn.hutool.extra.mail.MailAccount;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mail.config.properties.MailProperties;
import org.dromara.common.mail.core.MailBuilder;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.domain.PmMailServerConfig;
import org.dromara.payment.domain.dto.MailSettingsUpdateRequest;
import org.dromara.payment.domain.vo.MailSettingsVo;
import org.dromara.payment.mapper.MailServerConfigMapper;
import org.dromara.payment.security.MailSettingsCipher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MailSettingsService {
    private static final long CONFIG_ID = 1L;
    private static final Set<String> SECURITY_MODES =
        Set.of("SSL", "STARTTLS", "NONE");

    private final MailServerConfigMapper mapper;
    private final MailSettingsCipher cipher;
    private final MailProperties fallback;
    private final AccountMfaService mfaService;
    private final MailTemplateService mailTemplateService;

    public MailSettingsVo view() {
        return current().toVo();
    }

    public boolean enabled() {
        return current().enabled();
    }

    public void requireEnabled() {
        if (!enabled()) {
            throw new ServiceException("邮件服务尚未启用，请联系平台管理员");
        }
    }

    public ResolvedMailSettings current() {
        PmMailServerConfig saved = mapper.selectById(CONFIG_ID);
        if (saved != null) {
            String password = null;
            if (saved.getPasswordCiphertext() != null) {
                password = cipher.decrypt(
                    saved.getEncryptionKeyId(),
                    saved.getPasswordCiphertext()
                );
            }
            return new ResolvedMailSettings(
                Boolean.TRUE.equals(saved.getEnabled()),
                saved.getHost(),
                saved.getPort(),
                Boolean.TRUE.equals(saved.getAuthEnabled()),
                saved.getUsername(),
                password,
                saved.getFromName(),
                saved.getFromAddress(),
                saved.getSecurityMode(),
                saved.getConnectionTimeoutMs(),
                saved.getReadTimeoutMs(),
                "DATABASE",
                saved.getUpdatedAt(),
                saved.getPasswordCiphertext() != null
            );
        }
        String securityMode = Boolean.TRUE.equals(fallback.getSslEnable())
            ? "SSL"
            : Boolean.TRUE.equals(fallback.getStarttlsEnable())
                ? "STARTTLS"
                : "NONE";
        String fromAddress = extractAddress(fallback.getFrom());
        return new ResolvedMailSettings(
            Boolean.TRUE.equals(fallback.getEnabled()),
            blankDefault(fallback.getHost(), "localhost"),
            fallback.getPort() == null ? 25 : fallback.getPort(),
            Boolean.TRUE.equals(fallback.getAuth()),
            trimToNull(fallback.getUser()),
            trimToNull(fallback.getPass()),
            "LuLuPay",
            blankDefault(fromAddress, fallback.getUser()),
            securityMode,
            normalizeTimeout(fallback.getConnectionTimeout(), 10000L),
            normalizeTimeout(fallback.getTimeout(), 10000L),
            "ENVIRONMENT",
            null,
            fallback.getPass() != null && !fallback.getPass().isBlank()
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public MailSettingsVo update(
        MailSettingsUpdateRequest request,
        String stepUpToken
    ) {
        mfaService.requireStepUp(stepUpToken, "MAIL_SETTINGS_CHANGE");
        validate(request);
        PmMailServerConfig saved = mapper.selectById(CONFIG_ID);
        String ciphertext = saved == null ? null : saved.getPasswordCiphertext();
        String keyId = saved == null ? null : saved.getEncryptionKeyId();
        if (Boolean.TRUE.equals(request.getClearPassword())) {
            ciphertext = null;
            keyId = null;
        } else if (request.getPassword() != null
            && !request.getPassword().isBlank()) {
            MailSettingsCipher.EncryptedValue encrypted =
                cipher.encrypt(request.getPassword());
            ciphertext = encrypted.ciphertext();
            keyId = encrypted.keyId();
        }
        if (Boolean.TRUE.equals(request.getAuthEnabled())
            && (request.getUsername() == null
                || request.getUsername().isBlank())) {
            throw new ServiceException("启用 SMTP 认证时必须填写用户名");
        }
        if (Boolean.TRUE.equals(request.getAuthEnabled())
            && ciphertext == null) {
            throw new ServiceException("启用 SMTP 认证时必须配置密码或授权码");
        }
        PmMailServerConfig entity = saved == null
            ? new PmMailServerConfig()
            : saved;
        entity.setId(CONFIG_ID);
        entity.setEnabled(request.getEnabled());
        entity.setHost(request.getHost().trim());
        entity.setPort(request.getPort());
        entity.setAuthEnabled(request.getAuthEnabled());
        entity.setUsername(trimToNull(request.getUsername()));
        entity.setPasswordCiphertext(ciphertext);
        entity.setEncryptionKeyId(keyId);
        entity.setFromName(request.getFromName().trim());
        entity.setFromAddress(
            request.getFromAddress().trim().toLowerCase(Locale.ROOT)
        );
        entity.setSecurityMode(
            request.getSecurityMode().trim().toUpperCase(Locale.ROOT)
        );
        entity.setConnectionTimeoutMs(request.getConnectionTimeoutMs());
        entity.setReadTimeoutMs(request.getReadTimeoutMs());
        entity.setUpdatedBy(LoginHelper.getUserId());
        entity.setUpdatedAt(now());
        entity.setVersion(saved == null || saved.getVersion() == null
            ? 0
            : saved.getVersion() + 1);
        if (saved == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return current().toVo();
    }

    public void sendTest(String recipient, String stepUpToken) {
        mfaService.requireStepUp(stepUpToken, "MAIL_SETTINGS_TEST");
        ResolvedMailSettings settings = current();
        if (!settings.enabled()) {
            throw new ServiceException("请先启用并保存邮件服务");
        }
        try {
            MailBuilder.of(settings.toMailAccount())
                .to(recipient.trim().toLowerCase(Locale.ROOT))
                .subject("[LuLuPay] SMTP 配置测试")
                .html(mailTemplateService.notice(
                    "SMTP 配置测试成功",
                    "如果您收到此邮件，说明当前邮件服务器配置可以正常发送邮件。"
                ))
                .send();
        } catch (Exception exception) {
            throw new ServiceException(
                "测试邮件发送失败，请检查服务器、端口、认证信息和加密方式"
            );
        }
    }

    private void validate(MailSettingsUpdateRequest request) {
        String mode = request.getSecurityMode()
            .trim()
            .toUpperCase(Locale.ROOT);
        if (!SECURITY_MODES.contains(mode)) {
            throw new ServiceException("邮件连接加密方式无效");
        }
        if ("SSL".equals(mode) && request.getPort() == 587) {
            throw new ServiceException("587 端口通常应使用 STARTTLS");
        }
        if ("STARTTLS".equals(mode) && request.getPort() == 465) {
            throw new ServiceException("465 端口通常应使用 SSL");
        }
    }

    private String extractAddress(String from) {
        if (from == null) {
            return null;
        }
        int start = from.indexOf('<');
        int end = from.indexOf('>');
        if (start >= 0 && end > start) {
            return from.substring(start + 1, end).trim();
        }
        return from.trim();
    }

    private String blankDefault(String value, String defaultValue) {
        return value == null || value.isBlank()
            ? trimToNull(defaultValue)
            : value.trim();
    }

    private long normalizeTimeout(Long value, long defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    public record ResolvedMailSettings(
        boolean enabled,
        String host,
        int port,
        boolean authEnabled,
        String username,
        String password,
        String fromName,
        String fromAddress,
        String securityMode,
        long connectionTimeoutMs,
        long readTimeoutMs,
        String source,
        OffsetDateTime updatedAt,
        boolean passwordConfigured
    ) {
        public MailAccount toMailAccount() {
            MailAccount account = new MailAccount();
            account.setHost(host);
            account.setPort(port);
            account.setAuth(authEnabled);
            account.setUser(username);
            account.setPass(password);
            account.setFrom(
                fromName == null || fromName.isBlank()
                    ? fromAddress
                    : fromName + " <" + fromAddress + ">"
            );
            account.setSocketFactoryPort(port);
            account.setSslEnable("SSL".equals(securityMode));
            account.setStarttlsEnable("STARTTLS".equals(securityMode));
            account.setConnectionTimeout(connectionTimeoutMs);
            account.setTimeout(readTimeoutMs);
            return account;
        }

        public MailSettingsVo toVo() {
            return new MailSettingsVo(
                enabled,
                host,
                port,
                authEnabled,
                username,
                passwordConfigured,
                fromName,
                fromAddress,
                securityMode,
                connectionTimeoutMs,
                readTimeoutMs,
                source,
                updatedAt
            );
        }
    }
}
