package org.dromara.system.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.system.domain.PmRefreshSession;
import org.dromara.system.mapper.RefreshSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshSessionService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_ROTATED = "ROTATED";
    private static final String STATUS_REVOKED = "REVOKED";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final String REASON_ROTATED = "ROTATED";
    private static final String REASON_REUSE_DETECTED = "TOKEN_REUSE_DETECTED";

    private final RefreshSessionMapper mapper;

    @Transactional(rollbackFor = Exception.class)
    public PmRefreshSession create(
        Long userId,
        String loginId,
        String clientId,
        String rawToken,
        OffsetDateTime expiresAt,
        String clientIp,
        String userAgent
    ) {
        if (isBlank(loginId)) {
            throw new ServiceException("Refresh session login identity is missing");
        }
        validateIssueRequest(
            userId,
            loginId,
            clientId,
            rawToken,
            expiresAt
        );
        OffsetDateTime now = now();
        String sessionId = randomId();
        PmRefreshSession session = newSession(
            sessionId,
            sessionId,
            null,
            userId,
            loginId,
            clientId,
            rawToken,
            expiresAt,
            clientIp,
            userAgent,
            now
        );
        mapper.insert(session);
        return session;
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionResult inspect(String rawToken) {
        if (isBlank(rawToken)) {
            return InspectionResult.invalid();
        }
        OffsetDateTime now = now();
        PmRefreshSession current =
            mapper.selectByTokenHashForUpdate(sha256Hex(rawToken));
        if (current == null) {
            return InspectionResult.invalid();
        }
        if (STATUS_ROTATED.equals(current.getStatus())) {
            mapper.revokeActiveByFamilyId(
                current.getFamilyId(),
                REASON_REUSE_DETECTED,
                now
            );
            return InspectionResult.reuseDetected(current);
        }
        if (!STATUS_ACTIVE.equals(current.getStatus())) {
            return InspectionResult.invalid(current);
        }
        if (current.getExpiresAt() == null || !current.getExpiresAt().isAfter(now)) {
            expire(current, now);
            return InspectionResult.invalid(current);
        }
        return InspectionResult.active(current);
    }

    @Transactional(rollbackFor = Exception.class)
    public RotationResult rotate(
        String oldRawToken,
        String newRawToken,
        Long userId,
        String clientId,
        OffsetDateTime newExpiresAt,
        String clientIp,
        String userAgent
    ) {
        validateIssueRequest(
            userId,
            null,
            clientId,
            newRawToken,
            newExpiresAt
        );
        if (isBlank(oldRawToken)) {
            return RotationResult.invalid();
        }
        OffsetDateTime now = now();
        PmRefreshSession current =
            mapper.selectByTokenHashForUpdate(sha256Hex(oldRawToken));
        if (current == null) {
            return RotationResult.invalid();
        }
        if (STATUS_ROTATED.equals(current.getStatus())) {
            mapper.revokeActiveByFamilyId(
                current.getFamilyId(),
                REASON_REUSE_DETECTED,
                now
            );
            return RotationResult.reuseDetected();
        }
        if (!STATUS_ACTIVE.equals(current.getStatus())) {
            return RotationResult.invalid();
        }
        if (current.getExpiresAt() == null || !current.getExpiresAt().isAfter(now)) {
            expire(current, now);
            return RotationResult.invalid();
        }
        if (!userId.equals(current.getUserId())
            || !clientId.equals(current.getClientId())) {
            revoke(current, "CONTEXT_MISMATCH", now, clientIp);
            return RotationResult.invalid();
        }
        String newTokenHash = sha256Hex(newRawToken);
        if (newTokenHash.equals(current.getTokenHash())) {
            throw new ServiceException("Refresh token rotation produced a duplicate token");
        }

        current.setStatus(STATUS_ROTATED);
        current.setLastUsedAt(now);
        current.setLastUsedIp(normalize(clientIp, 64));
        current.setRevokedAt(now);
        current.setRevokeReason(REASON_ROTATED);
        current.setUpdatedAt(now);
        mapper.updateById(current);

        String newSessionId = randomId();
        PmRefreshSession replacement = newSession(
            newSessionId,
            current.getFamilyId(),
            current.getId(),
            userId,
            current.getLoginId(),
            clientId,
            newRawToken,
            newExpiresAt,
            clientIp,
            userAgent,
            now
        );
        mapper.insert(replacement);
        return RotationResult.success(replacement);
    }

    @Transactional(rollbackFor = Exception.class)
    public void revokeByToken(String rawToken, String reason) {
        if (isBlank(rawToken)) {
            return;
        }
        PmRefreshSession session =
            mapper.selectByTokenHashForUpdate(sha256Hex(rawToken));
        if (session == null || !STATUS_ACTIVE.equals(session.getStatus())) {
            return;
        }
        revoke(session, normalizeReason(reason), now(), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public int revokeAll(Long userId, String reason) {
        if (userId == null) {
            return 0;
        }
        return mapper.revokeActiveByUserId(
            userId,
            normalizeReason(reason),
            now()
        );
    }

    private PmRefreshSession newSession(
        String sessionId,
        String familyId,
        Long rotatedFromId,
        Long userId,
        String loginId,
        String clientId,
        String rawToken,
        OffsetDateTime expiresAt,
        String clientIp,
        String userAgent,
        OffsetDateTime now
    ) {
        PmRefreshSession session = new PmRefreshSession();
        session.setSessionId(sessionId);
        session.setFamilyId(familyId);
        session.setRotatedFromId(rotatedFromId);
        session.setUserId(userId);
        session.setLoginId(loginId);
        session.setClientId(clientId);
        session.setTokenHash(sha256Hex(rawToken));
        session.setStatus(STATUS_ACTIVE);
        session.setIssuedAt(now);
        session.setExpiresAt(expiresAt);
        session.setCreatedIp(normalize(clientIp, 64));
        session.setLastUsedIp(normalize(clientIp, 64));
        session.setUserAgentHash(
            isBlank(userAgent) ? null : sha256Hex(userAgent)
        );
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        return session;
    }

    private void validateIssueRequest(
        Long userId,
        String loginId,
        String clientId,
        String rawToken,
        OffsetDateTime expiresAt
    ) {
        if (userId == null
            || (loginId != null && isBlank(loginId))
            || isBlank(clientId)
            || isBlank(rawToken)) {
            throw new ServiceException("Refresh session context is incomplete");
        }
        if (expiresAt == null || !expiresAt.isAfter(now())) {
            throw new ServiceException("Refresh session expiry is invalid");
        }
    }

    private void expire(PmRefreshSession session, OffsetDateTime now) {
        session.setStatus(STATUS_EXPIRED);
        session.setRevokedAt(now);
        session.setRevokeReason("EXPIRED");
        session.setUpdatedAt(now);
        mapper.updateById(session);
    }

    private void revoke(
        PmRefreshSession session,
        String reason,
        OffsetDateTime now,
        String clientIp
    ) {
        session.setStatus(STATUS_REVOKED);
        session.setRevokedAt(now);
        session.setRevokeReason(reason);
        String normalizedIp = normalize(clientIp, 64);
        if (normalizedIp != null) {
            session.setLastUsedIp(normalizedIp);
        }
        session.setUpdatedAt(now);
        mapper.updateById(session);
    }

    private String normalizeReason(String reason) {
        return isBlank(reason) ? "REVOKED" : normalize(reason, 64);
    }

    private String normalize(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() <= maxLength
            ? normalized
            : normalized.substring(0, maxLength);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 initialization failed", e);
        }
    }

    public enum RotationStatus {
        SUCCESS,
        INVALID,
        REUSE_DETECTED
    }

    public enum InspectionStatus {
        ACTIVE,
        INVALID,
        REUSE_DETECTED
    }

    public record InspectionResult(
        InspectionStatus status,
        Long userId,
        String loginId,
        String clientId
    ) {
        public static InspectionResult active(PmRefreshSession session) {
            return from(InspectionStatus.ACTIVE, session);
        }

        public static InspectionResult invalid() {
            return new InspectionResult(
                InspectionStatus.INVALID,
                null,
                null,
                null
            );
        }

        public static InspectionResult invalid(PmRefreshSession session) {
            return from(InspectionStatus.INVALID, session);
        }

        public static InspectionResult reuseDetected(
            PmRefreshSession session
        ) {
            return from(InspectionStatus.REUSE_DETECTED, session);
        }

        private static InspectionResult from(
            InspectionStatus status,
            PmRefreshSession session
        ) {
            return new InspectionResult(
                status,
                session.getUserId(),
                session.getLoginId(),
                session.getClientId()
            );
        }
    }

    public record RotationResult(
        RotationStatus status,
        PmRefreshSession replacement
    ) {
        public static RotationResult success(PmRefreshSession replacement) {
            return new RotationResult(RotationStatus.SUCCESS, replacement);
        }

        public static RotationResult invalid() {
            return new RotationResult(RotationStatus.INVALID, null);
        }

        public static RotationResult reuseDetected() {
            return new RotationResult(RotationStatus.REUSE_DETECTED, null);
        }
    }
}
