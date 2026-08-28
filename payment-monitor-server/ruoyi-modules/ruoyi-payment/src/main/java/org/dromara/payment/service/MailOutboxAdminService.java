package org.dromara.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.payment.domain.MailOutboxPayload;
import org.dromara.payment.domain.PmMailOutbox;
import org.dromara.payment.domain.bo.MailOutboxQueryBo;
import org.dromara.payment.domain.vo.MailOutboxVo;
import org.dromara.payment.mapper.MailOutboxMapper;
import org.dromara.payment.security.MailOutboxCipher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class MailOutboxAdminService {
    private final MailOutboxMapper mapper;
    private final MailOutboxCipher cipher;
    private final ObjectMapper objectMapper;
    private final AccountMfaService mfaService;

    public PageResult<MailOutboxVo> queryPage(
        MailOutboxQueryBo bo,
        PageQuery pageQuery
    ) {
        LambdaQueryWrapper<PmMailOutbox> wrapper =
            new LambdaQueryWrapper<PmMailOutbox>()
                .eq(StringUtils.isNotBlank(bo.getStatus()),
                    PmMailOutbox::getStatus,
                    bo.getStatus())
                .eq(StringUtils.isNotBlank(bo.getMessageType()),
                    PmMailOutbox::getMessageType,
                    bo.getMessageType())
                .ge(bo.getStartTime() != null,
                    PmMailOutbox::getCreatedAt,
                    bo.getStartTime())
                .le(bo.getEndTime() != null,
                    PmMailOutbox::getCreatedAt,
                    bo.getEndTime())
                .orderByDesc(PmMailOutbox::getCreatedAt);
        Page<PmMailOutbox> page = mapper.selectPage(pageQuery.build(), wrapper);
        return PageResult.build(
            page.getRecords().stream().map(this::toVo).toList(),
            page.getTotal()
        );
    }

    public MailOutboxVo queryById(Long id) {
        PmMailOutbox outbox = mapper.selectById(id);
        if (outbox == null) {
            throw new ServiceException("邮件发送记录不存在");
        }
        return toVo(outbox);
    }

    @Transactional(rollbackFor = Exception.class)
    public MailOutboxVo retry(Long id, String stepUpToken) {
        mfaService.requireStepUp(stepUpToken, "MAIL_OUTBOX_RETRY");
        PmMailOutbox outbox = mapper.selectById(id);
        if (outbox == null) {
            throw new ServiceException("邮件发送记录不存在");
        }
        if (!"DEAD".equals(outbox.getStatus())) {
            throw new ServiceException("只有发送失败的邮件可以重试");
        }
        if (outbox.getExpiresAt() != null
            && !outbox.getExpiresAt().isAfter(now())) {
            throw new ServiceException("该邮件内容已经过期，请重新发起原业务流程");
        }
        OffsetDateTime timestamp = now();
        outbox.setStatus("RETRYING");
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(timestamp);
        outbox.setLockedAt(null);
        outbox.setLastError(null);
        outbox.setUpdatedAt(timestamp);
        mapper.updateById(outbox);
        return toVo(outbox);
    }

    private MailOutboxVo toVo(PmMailOutbox outbox) {
        MailOutboxPayload payload = payload(outbox);
        return new MailOutboxVo(
            outbox.getId(),
            outbox.getMessageId(),
            outbox.getMessageType(),
            maskEmail(payload == null ? null : payload.recipient()),
            payload == null ? outbox.getMessageType() : payload.subject(),
            outbox.getStatus(),
            outbox.getAttemptCount(),
            outbox.getMaxAttempts(),
            outbox.getNextAttemptAt(),
            outbox.getExpiresAt(),
            outbox.getSentAt(),
            sanitizeError(outbox.getLastError()),
            outbox.getCreatedAt(),
            outbox.getUpdatedAt(),
            retryable(outbox)
        );
    }

    private MailOutboxPayload payload(PmMailOutbox outbox) {
        try {
            String plaintext = cipher.decrypt(
                outbox.getMessageId(),
                outbox.getEncryptionKeyId(),
                outbox.getPayloadCiphertext()
            );
            return objectMapper.readValue(plaintext, MailOutboxPayload.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String masked = local.length() <= 1
            ? "*"
            : local.substring(0, 1) + "***";
        return masked + "@" + parts[1];
    }

    private String sanitizeError(String error) {
        if (error == null || error.isBlank()) {
            return null;
        }
        return switch (error) {
            case "DELIVERY_WINDOW_EXPIRED", "EXPIRED_BEFORE_DELIVERY" ->
                "邮件发送窗口已过期";
            default -> "邮件服务器连接或发送失败";
        };
    }

    private boolean retryable(PmMailOutbox outbox) {
        return "DEAD".equals(outbox.getStatus())
            && (outbox.getExpiresAt() == null
                || outbox.getExpiresAt().isAfter(now()));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
