package org.dromara.payment.service;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.domain.PmAccountRecoveryChallenge;
import org.dromara.payment.mapper.AccountRecoveryChallengeMapper;
import org.dromara.payment.security.AccountRecoveryCodeHasher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.dromara.payment.service.AccountRecoveryChallengeService.ChallengeType.EMAIL_CHANGE;
import static org.dromara.payment.service.AccountRecoveryChallengeService.ChallengeType.PASSWORD_RESET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class AccountRecoveryChallengeServiceTest {

    @Test
    void issueCancelsPreviousChallengeAndStoresOnlyCodeHash() {
        Fixture fixture = fixture(5);
        when(fixture.hasher.hash(any(), eq("PASSWORD_RESET"), eq("123456")))
            .thenReturn("a".repeat(64));
        ArgumentCaptor<PmAccountRecoveryChallenge> inserted =
            ArgumentCaptor.forClass(PmAccountRecoveryChallenge.class);

        fixture.service.issue(
            PASSWORD_RESET,
            100L,
            "User@Example.com",
            "123456",
            future(),
            "203.0.113.20"
        );

        verify(fixture.mapper).lockIssueScope("PASSWORD_RESET:100");
        verify(fixture.mapper).cancelPending(
            eq("PASSWORD_RESET"),
            eq(100L),
            eq("REPLACED"),
            any()
        );
        verify(fixture.mapper).insert(inserted.capture());
        PmAccountRecoveryChallenge challenge = inserted.getValue();
        assertEquals("user@example.com", challenge.getTargetEmail());
        assertEquals("a".repeat(64), challenge.getCodeHash());
        assertNotEquals("123456", challenge.getCodeHash());
        assertEquals("PENDING", challenge.getStatus());
        assertEquals(0, challenge.getAttemptCount());
        assertEquals(5, challenge.getMaxAttempts());
        assertNotNull(challenge.getChallengeId());
    }

    @Test
    void invalidCodeIncrementsAttemptAndLocksAtLimit() {
        Fixture fixture = fixture(2);
        PmAccountRecoveryChallenge challenge = challenge(
            PASSWORD_RESET.name(),
            100L,
            "user@example.com"
        );
        challenge.setAttemptCount(1);
        challenge.setMaxAttempts(2);
        when(fixture.mapper.selectPendingForUpdate(
            "PASSWORD_RESET",
            100L,
            "user@example.com"
        )).thenReturn(challenge);
        when(fixture.hasher.matches(any(), any(), any(), any()))
            .thenReturn(false);

        assertThrows(
            ServiceException.class,
            () -> fixture.service.verify(
                PASSWORD_RESET,
                100L,
                "user@example.com",
                "000000"
            )
        );

        assertEquals(2, challenge.getAttemptCount());
        assertEquals("LOCKED", challenge.getStatus());
        assertEquals("MAX_ATTEMPTS", challenge.getResolutionReason());
        assertNotNull(challenge.getResolvedAt());
        verify(fixture.mapper).updateById(challenge);
    }

    @Test
    void expiredChallengeIsClosed() {
        Fixture fixture = fixture(5);
        PmAccountRecoveryChallenge challenge = challenge(
            PASSWORD_RESET.name(),
            100L,
            "user@example.com"
        );
        challenge.setExpiresAt(
            OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1)
        );
        when(fixture.mapper.selectPendingForUpdate(
            "PASSWORD_RESET",
            100L,
            "user@example.com"
        )).thenReturn(challenge);

        assertThrows(
            ServiceException.class,
            () -> fixture.service.verify(
                PASSWORD_RESET,
                100L,
                "user@example.com",
                "123456"
            )
        );

        assertEquals("EXPIRED", challenge.getStatus());
        assertEquals("EXPIRED", challenge.getResolutionReason());
        verify(fixture.mapper).updateById(challenge);
        verify(fixture.hasher, never()).matches(any(), any(), any(), any());
    }

    @Test
    void validVerificationReturnsChallengeWithoutConsumingIt() {
        Fixture fixture = fixture(5);
        PmAccountRecoveryChallenge challenge = challenge(
            EMAIL_CHANGE.name(),
            100L,
            "new@example.com"
        );
        when(fixture.mapper.selectPendingForUpdate(
            "EMAIL_CHANGE",
            100L,
            "new@example.com"
        )).thenReturn(challenge);
        when(fixture.hasher.matches(any(), any(), any(), eq("123456")))
            .thenReturn(true);

        Long challengeId = fixture.service.verify(
            EMAIL_CHANGE,
            100L,
            "new@example.com",
            "123456"
        );

        assertEquals(challenge.getId(), challengeId);
        assertEquals("PENDING", challenge.getStatus());
        verify(fixture.mapper, never()).updateById(challenge);
    }

    @Test
    void consumeAtomicallyClosesVerifiedChallenge() {
        Fixture fixture = fixture(5);
        PmAccountRecoveryChallenge challenge = challenge(
            EMAIL_CHANGE.name(),
            100L,
            "new@example.com"
        );
        when(fixture.mapper.selectByIdForUpdate(challenge.getId()))
            .thenReturn(challenge);
        when(fixture.hasher.matches(any(), any(), any(), eq("123456")))
            .thenReturn(true);

        fixture.service.consume(
            challenge.getId(),
            EMAIL_CHANGE,
            100L,
            "new@example.com",
            "123456"
        );

        assertEquals("CONSUMED", challenge.getStatus());
        assertEquals("CONFIRMED", challenge.getResolutionReason());
        assertNotNull(challenge.getResolvedAt());
        verify(fixture.mapper).updateById(challenge);
    }

    @Test
    void verificationCommitsFailedAttemptCountersInRequiresNewTransaction()
        throws Exception {
        Method method = AccountRecoveryChallengeService.class.getMethod(
            "verify",
            AccountRecoveryChallengeService.ChallengeType.class,
            Long.class,
            String.class,
            String.class
        );
        Transactional transactional =
            method.getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
        assertTrue(
            java.util.Arrays.asList(transactional.noRollbackFor())
                .contains(ServiceException.class)
        );
    }

    private Fixture fixture(int maxAttempts) {
        AccountRecoveryChallengeMapper mapper =
            mock(AccountRecoveryChallengeMapper.class);
        AccountRecoveryCodeHasher hasher =
            mock(AccountRecoveryCodeHasher.class);
        PaymentProperties properties = new PaymentProperties();
        properties.getAccountRecovery().setMaxAttempts(maxAttempts);
        return new Fixture(
            mapper,
            hasher,
            new AccountRecoveryChallengeService(
                mapper,
                hasher,
                properties
            )
        );
    }

    private PmAccountRecoveryChallenge challenge(
        String type,
        Long userId,
        String email
    ) {
        PmAccountRecoveryChallenge challenge =
            new PmAccountRecoveryChallenge();
        challenge.setId(10L);
        challenge.setChallengeId("challenge-1");
        challenge.setChallengeType(type);
        challenge.setUserId(userId);
        challenge.setTargetEmail(email);
        challenge.setCodeHash("a".repeat(64));
        challenge.setStatus("PENDING");
        challenge.setAttemptCount(0);
        challenge.setMaxAttempts(5);
        challenge.setExpiresAt(future());
        challenge.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        challenge.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return challenge;
    }

    private OffsetDateTime future() {
        return OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5);
    }

    private record Fixture(
        AccountRecoveryChallengeMapper mapper,
        AccountRecoveryCodeHasher hasher,
        AccountRecoveryChallengeService service
    ) {
    }
}
