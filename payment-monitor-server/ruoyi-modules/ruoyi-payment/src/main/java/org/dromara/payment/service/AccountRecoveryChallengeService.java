package org.dromara.payment.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.domain.PmAccountRecoveryChallenge;
import org.dromara.payment.mapper.AccountRecoveryChallengeMapper;
import org.dromara.payment.security.AccountRecoveryCodeHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountRecoveryChallengeService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_CONSUMED = "CONSUMED";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String STATUS_LOCKED = "LOCKED";
    private static final String INVALID_MESSAGE = "验证码无效或已过期";

    private final AccountRecoveryChallengeMapper mapper;
    private final AccountRecoveryCodeHasher codeHasher;
    private final PaymentProperties properties;

    @Transactional(rollbackFor = Exception.class)
    public PmAccountRecoveryChallenge issue(
        ChallengeType type,
        Long userId,
        String targetEmail,
        String code,
        OffsetDateTime expiresAt,
        String clientIp
    ) {
        if (type == null
            || userId == null
            || targetEmail == null
            || targetEmail.isBlank()
            || code == null
            || code.isBlank()) {
            throw new ServiceException("账号恢复挑战上下文不完整");
        }
        OffsetDateTime now = now();
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            throw new ServiceException("账号恢复挑战过期时间无效");
        }
        int maxAttempts = properties.getAccountRecovery().getMaxAttempts();
        if (maxAttempts < 1 || maxAttempts > 20) {
            throw new ServiceException("账号恢复最大尝试次数配置无效");
        }
        mapper.lockIssueScope(type.name() + ":" + userId);
        mapper.cancelPending(type.name(), userId, "REPLACED", now);

        PmAccountRecoveryChallenge challenge =
            new PmAccountRecoveryChallenge();
        String challengeId = randomId();
        challenge.setChallengeId(challengeId);
        challenge.setChallengeType(type.name());
        challenge.setUserId(userId);
        challenge.setTargetEmail(normalizeEmail(targetEmail));
        challenge.setCodeHash(
            codeHasher.hash(challengeId, type.name(), code)
        );
        challenge.setStatus(STATUS_PENDING);
        challenge.setAttemptCount(0);
        challenge.setMaxAttempts(maxAttempts);
        challenge.setExpiresAt(expiresAt);
        challenge.setCreatedIp(normalize(clientIp, 64));
        challenge.setCreatedAt(now);
        challenge.setUpdatedAt(now);
        mapper.insert(challenge);
        return challenge;
    }

    @Transactional(
        propagation = Propagation.REQUIRES_NEW,
        noRollbackFor = ServiceException.class
    )
    public Long verify(
        ChallengeType type,
        Long userId,
        String targetEmail,
        String code
    ) {
        PmAccountRecoveryChallenge challenge = pending(
            type,
            userId,
            targetEmail
        );
        OffsetDateTime now = now();
        if (challenge == null) {
            throw invalid();
        }
        if (!challenge.getExpiresAt().isAfter(now)) {
            resolve(challenge, STATUS_EXPIRED, "EXPIRED", now);
            throw invalid();
        }
        if (!codeHasher.matches(
            challenge.getCodeHash(),
            challenge.getChallengeId(),
            challenge.getChallengeType(),
            code
        )) {
            rejectAttempt(challenge, now);
            throw invalid();
        }
        return challenge.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void consume(
        Long challengeId,
        ChallengeType type,
        Long userId,
        String targetEmail,
        String code
    ) {
        PmAccountRecoveryChallenge challenge =
            mapper.selectByIdForUpdate(challengeId);
        OffsetDateTime now = now();
        if (challenge == null
            || !STATUS_PENDING.equals(challenge.getStatus())
            || !type.name().equals(challenge.getChallengeType())
            || !userId.equals(challenge.getUserId())
            || !normalizeEmail(targetEmail).equals(challenge.getTargetEmail())
            || !challenge.getExpiresAt().isAfter(now)
            || !codeHasher.matches(
                challenge.getCodeHash(),
                challenge.getChallengeId(),
                challenge.getChallengeType(),
                code
            )) {
            throw invalid();
        }
        resolve(challenge, STATUS_CONSUMED, "CONFIRMED", now);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long challengeId, String reason) {
        if (challengeId == null) {
            return;
        }
        PmAccountRecoveryChallenge challenge =
            mapper.selectByIdForUpdate(challengeId);
        if (challenge == null || !STATUS_PENDING.equals(challenge.getStatus())) {
            return;
        }
        resolve(
            challenge,
            STATUS_CANCELLED,
            normalize(reason, 64),
            now()
        );
    }

    private PmAccountRecoveryChallenge pending(
        ChallengeType type,
        Long userId,
        String targetEmail
    ) {
        if (type == null || userId == null || targetEmail == null) {
            return null;
        }
        return mapper.selectPendingForUpdate(
            type.name(),
            userId,
            normalizeEmail(targetEmail)
        );
    }

    private void rejectAttempt(
        PmAccountRecoveryChallenge challenge,
        OffsetDateTime now
    ) {
        int attempts = challenge.getAttemptCount() + 1;
        challenge.setAttemptCount(attempts);
        challenge.setLastAttemptAt(now);
        challenge.setUpdatedAt(now);
        if (attempts >= challenge.getMaxAttempts()) {
            challenge.setStatus(STATUS_LOCKED);
            challenge.setResolvedAt(now);
            challenge.setResolutionReason("MAX_ATTEMPTS");
        }
        mapper.updateById(challenge);
    }

    private void resolve(
        PmAccountRecoveryChallenge challenge,
        String status,
        String reason,
        OffsetDateTime now
    ) {
        challenge.setStatus(status);
        challenge.setResolvedAt(now);
        challenge.setResolutionReason(reason);
        challenge.setUpdatedAt(now);
        mapper.updateById(challenge);
    }

    private ServiceException invalid() {
        return new ServiceException(INVALID_MESSAGE);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "CANCELLED";
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength
            ? normalized
            : normalized.substring(0, maxLength);
    }

    private String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    public enum ChallengeType {
        PASSWORD_RESET,
        EMAIL_CHANGE
    }
}
