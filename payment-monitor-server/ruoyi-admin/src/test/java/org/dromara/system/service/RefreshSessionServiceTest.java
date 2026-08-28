package org.dromara.system.service;

import org.dromara.system.domain.PmRefreshSession;
import org.dromara.system.mapper.RefreshSessionMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class RefreshSessionServiceTest {

    @Test
    void createStoresOnlyTokenAndUserAgentHashes() {
        RefreshSessionMapper mapper = mock(RefreshSessionMapper.class);
        RefreshSessionService service = new RefreshSessionService(mapper);
        ArgumentCaptor<PmRefreshSession> inserted =
            ArgumentCaptor.forClass(PmRefreshSession.class);

        service.create(
            100L,
            "sys_user:100",
            "admin-web",
            "raw-refresh-token",
            future(),
            "203.0.113.8",
            "Browser/1.0"
        );

        verify(mapper).insert(inserted.capture());
        PmRefreshSession session = inserted.getValue();
        assertEquals("ACTIVE", session.getStatus());
        assertEquals("sys_user:100", session.getLoginId());
        assertEquals(session.getSessionId(), session.getFamilyId());
        assertEquals(64, session.getTokenHash().length());
        assertNotEquals("raw-refresh-token", session.getTokenHash());
        assertEquals(64, session.getUserAgentHash().length());
        assertEquals("203.0.113.8", session.getCreatedIp());
        assertNull(session.getRotatedFromId());
    }

    @Test
    void rotateConsumesParentAndCreatesChildInSameFamily() {
        RefreshSessionMapper mapper = mock(RefreshSessionMapper.class);
        RefreshSessionService service = new RefreshSessionService(mapper);
        PmRefreshSession current = activeSession();
        when(mapper.selectByTokenHashForUpdate(any())).thenReturn(current);
        ArgumentCaptor<PmRefreshSession> inserted =
            ArgumentCaptor.forClass(PmRefreshSession.class);

        RefreshSessionService.RotationResult result = service.rotate(
            "old-refresh-token",
            "new-refresh-token",
            current.getUserId(),
            current.getClientId(),
            future(),
            "203.0.113.9",
            "Browser/2.0"
        );

        assertEquals(
            RefreshSessionService.RotationStatus.SUCCESS,
            result.status()
        );
        assertEquals("ROTATED", current.getStatus());
        assertEquals("ROTATED", current.getRevokeReason());
        assertNotNull(current.getRevokedAt());
        verify(mapper).updateById(current);
        verify(mapper).insert(inserted.capture());
        PmRefreshSession replacement = inserted.getValue();
        assertEquals(current.getId(), replacement.getRotatedFromId());
        assertEquals(current.getFamilyId(), replacement.getFamilyId());
        assertEquals("ACTIVE", replacement.getStatus());
        assertNotEquals(current.getTokenHash(), replacement.getTokenHash());
    }

    @Test
    void rotatedTokenReuseRevokesRemainingFamilySessions() {
        RefreshSessionMapper mapper = mock(RefreshSessionMapper.class);
        RefreshSessionService service = new RefreshSessionService(mapper);
        PmRefreshSession current = activeSession();
        current.setStatus("ROTATED");
        current.setRevokedAt(OffsetDateTime.now(ZoneOffset.UTC));
        current.setRevokeReason("ROTATED");
        when(mapper.selectByTokenHashForUpdate(any())).thenReturn(current);

        RefreshSessionService.RotationResult result = service.rotate(
            "old-refresh-token",
            "attacker-refresh-token",
            current.getUserId(),
            current.getClientId(),
            future(),
            "203.0.113.10",
            "Browser/3.0"
        );

        assertEquals(
            RefreshSessionService.RotationStatus.REUSE_DETECTED,
            result.status()
        );
        assertNull(result.replacement());
        verify(mapper).revokeActiveByFamilyId(
            eq(current.getFamilyId()),
            eq("TOKEN_REUSE_DETECTED"),
            any()
        );
        verify(mapper, never()).insert(any(PmRefreshSession.class));
    }

    @Test
    void inspectDetectsRotatedTokenBeforeTokenStoreLookup() {
        RefreshSessionMapper mapper = mock(RefreshSessionMapper.class);
        RefreshSessionService service = new RefreshSessionService(mapper);
        PmRefreshSession current = activeSession();
        current.setStatus("ROTATED");
        current.setRevokedAt(OffsetDateTime.now(ZoneOffset.UTC));
        current.setRevokeReason("ROTATED");
        when(mapper.selectByTokenHashForUpdate(any())).thenReturn(current);

        RefreshSessionService.InspectionResult result =
            service.inspect("old-refresh-token");

        assertEquals(
            RefreshSessionService.InspectionStatus.REUSE_DETECTED,
            result.status()
        );
        assertEquals(current.getUserId(), result.userId());
        assertEquals(current.getLoginId(), result.loginId());
        verify(mapper).revokeActiveByFamilyId(
            eq(current.getFamilyId()),
            eq("TOKEN_REUSE_DETECTED"),
            any()
        );
    }

    @Test
    void expiredSessionIsPersistentlyMarkedExpired() {
        RefreshSessionMapper mapper = mock(RefreshSessionMapper.class);
        RefreshSessionService service = new RefreshSessionService(mapper);
        PmRefreshSession current = activeSession();
        current.setExpiresAt(
            OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1)
        );
        when(mapper.selectByTokenHashForUpdate(any())).thenReturn(current);

        RefreshSessionService.RotationResult result = service.rotate(
            "old-refresh-token",
            "new-refresh-token",
            current.getUserId(),
            current.getClientId(),
            future(),
            "203.0.113.11",
            "Browser/4.0"
        );

        assertEquals(
            RefreshSessionService.RotationStatus.INVALID,
            result.status()
        );
        assertEquals("EXPIRED", current.getStatus());
        assertEquals("EXPIRED", current.getRevokeReason());
        assertNotNull(current.getRevokedAt());
        verify(mapper).updateById(current);
        verify(mapper, never()).insert(any(PmRefreshSession.class));
    }

    @Test
    void revokeAllUsesPersistentUserScope() {
        RefreshSessionMapper mapper = mock(RefreshSessionMapper.class);
        RefreshSessionService service = new RefreshSessionService(mapper);
        when(mapper.revokeActiveByUserId(eq(100L), eq("PASSWORD_RESET"), any()))
            .thenReturn(3);

        int revoked = service.revokeAll(100L, "PASSWORD_RESET");

        assertEquals(3, revoked);
    }

    private PmRefreshSession activeSession() {
        PmRefreshSession session = new PmRefreshSession();
        session.setId(10L);
        session.setSessionId("session-1");
        session.setFamilyId("family-1");
        session.setUserId(100L);
        session.setLoginId("sys_user:100");
        session.setClientId("admin-web");
        session.setTokenHash("a".repeat(64));
        session.setStatus("ACTIVE");
        session.setIssuedAt(OffsetDateTime.now(ZoneOffset.UTC));
        session.setExpiresAt(future());
        session.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        session.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return session;
    }

    private OffsetDateTime future() {
        return OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
    }
}
