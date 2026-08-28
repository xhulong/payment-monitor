package org.dromara.payment.service;

import cn.dev33.satoken.stp.StpUtil;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.domain.PmAccountMfa;
import org.dromara.payment.domain.vo.TotpSetupVo;
import org.dromara.payment.mapper.AccountMfaMapper;
import org.dromara.payment.security.AccountMfaCipher;
import org.dromara.payment.security.StepUpVerificationMethod;
import org.dromara.system.domain.StepUpGrant;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.service.ISysUserService;
import org.dromara.system.service.StepUpTokenStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@Tag("dev")
class AccountMfaServiceTest {

    @Test
    void setupUsesEncodedEmailAsAuthenticatorAccountLabel() {
        AccountMfaMapper mapper = mock(AccountMfaMapper.class);
        AccountMfaCipher cipher = mock(AccountMfaCipher.class);
        PaymentProperties properties = new PaymentProperties();
        properties.getAccountMfa().setIssuer("LuLuPay");
        StepUpTokenStore tokenStore = mock(StepUpTokenStore.class);
        ISysUserService userService = mock(ISysUserService.class);
        SysUserVo user = new SysUserVo();
        user.setEmail("Ops+China@Example.com");
        when(userService.selectUserById(7L)).thenReturn(user);
        when(cipher.encrypt(anyString())).thenReturn("encrypted-secret");

        AccountMfaService service = new AccountMfaService(
            mapper,
            cipher,
            properties,
            tokenStore,
            userService,
            mock(MailNotificationPublisher.class));

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            TotpSetupVo result = service.setup(null);

            assertTrue(result.otpauthUri().startsWith(
                "otpauth://totp/LuLuPay:ops%2Bchina%40example.com?secret="));
            assertTrue(result.otpauthUri().contains("&issuer=LuLuPay"));
            ArgumentCaptor<PmAccountMfa> captor = ArgumentCaptor.forClass(PmAccountMfa.class);
            verify(mapper).insert(captor.capture());
            PmAccountMfa saved = captor.getValue();
            assertEquals(7L, saved.getUserId());
            assertEquals(false, saved.getEnabled());
            assertEquals("encrypted-secret", saved.getPendingSecretCiphertext());
        }
    }

    @Test
    void setupRequiresBoundEmail() {
        AccountMfaMapper mapper = mock(AccountMfaMapper.class);
        AccountMfaCipher cipher = mock(AccountMfaCipher.class);
        PaymentProperties properties = new PaymentProperties();
        StepUpTokenStore tokenStore = mock(StepUpTokenStore.class);
        ISysUserService userService = mock(ISysUserService.class);
        SysUserVo user = new SysUserVo();
        user.setEmail(" ");
        when(userService.selectUserById(8L)).thenReturn(user);

        AccountMfaService service = new AccountMfaService(
            mapper,
            cipher,
            properties,
            tokenStore,
            userService,
            mock(MailNotificationPublisher.class));

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(8L);
            ServiceException exception = assertThrows(
                ServiceException.class,
                () -> service.setup(null));

            assertEquals("当前账号未绑定邮箱，请先绑定邮箱后再配置 MFA", exception.getMessage());
            verifyNoInteractions(mapper, cipher);
        }
    }

    @Test
    void requireStepUpReturnsSessionWhenMfaIsNotConfigured() {
        AccountMfaMapper mapper = mock(AccountMfaMapper.class);
        StepUpTokenStore tokenStore = mock(StepUpTokenStore.class);
        AccountMfaService service = service(
            mapper,
            tokenStore,
            mock(ISysUserService.class),
            mock(MailNotificationPublisher.class)
        );

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);

            assertEquals(
                StepUpVerificationMethod.SESSION,
                service.requireStepUp(null, "PAYMENT_ORDER_FORCE_MATCH")
            );
        }

        verifyNoInteractions(tokenStore);
    }

    @Test
    void requireStepUpReturnsSessionWhenMfaIsDisabled() {
        AccountMfaMapper mapper = mock(AccountMfaMapper.class);
        PmAccountMfa mfa = new PmAccountMfa();
        mfa.setUserId(9L);
        mfa.setEnabled(false);
        when(mapper.selectOne(any())).thenReturn(mfa);
        StepUpTokenStore tokenStore = mock(StepUpTokenStore.class);
        AccountMfaService service = service(
            mapper,
            tokenStore,
            mock(ISysUserService.class),
            mock(MailNotificationPublisher.class)
        );

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);

            assertEquals(
                StepUpVerificationMethod.SESSION,
                service.requireStepUp("ignored", "PAYMENT_ORDER_FORCE_MATCH")
            );
        }

        verifyNoInteractions(tokenStore);
    }

    @Test
    void enabledMfaRejectsMissingAndInvalidStepUpTokens() {
        AccountMfaMapper mapper = enabledMfaMapper(9L);
        StepUpTokenStore tokenStore = mock(StepUpTokenStore.class);
        AccountMfaService service = service(
            mapper,
            tokenStore,
            mock(ISysUserService.class),
            mock(MailNotificationPublisher.class)
        );

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
             MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            stp.when(StpUtil::getTokenValue).thenReturn("session-a");

            assertThrows(
                ServiceException.class,
                () -> service.requireStepUp(null, "PAYMENT_ORDER_FORCE_MATCH")
            );
            assertThrows(
                ServiceException.class,
                () -> service.requireStepUp(
                    "invalid-token",
                    "PAYMENT_ORDER_FORCE_MATCH"
                )
            );
        }

        verify(tokenStore).consume(
            "invalid-token",
            new StepUpGrant(
                9L,
                "session-a",
                "PAYMENT_ORDER_FORCE_MATCH"
            )
        );
    }

    @Test
    void enabledMfaConsumesBoundTokenAndReturnsMfa() {
        AccountMfaMapper mapper = enabledMfaMapper(9L);
        StepUpTokenStore tokenStore = mock(StepUpTokenStore.class);
        StepUpGrant expected = new StepUpGrant(
            9L,
            "session-a",
            "PAYMENT_CONFIRMATION_REVERSE"
        );
        when(tokenStore.consume("valid-token", expected)).thenReturn(true);
        AccountMfaService service = service(
            mapper,
            tokenStore,
            mock(ISysUserService.class),
            mock(MailNotificationPublisher.class)
        );

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
             MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            stp.when(StpUtil::getTokenValue).thenReturn("session-a");

            assertEquals(
                StepUpVerificationMethod.MFA,
                service.requireStepUp(
                    "valid-token",
                    "PAYMENT_CONFIRMATION_REVERSE"
                )
            );
        }

        verify(tokenStore).consume("valid-token", expected);
    }

    @Test
    void disableClearsMfaRevokesStepUpTokensAndSendsNotice() {
        AccountMfaMapper mapper = mock(AccountMfaMapper.class);
        PmAccountMfa mfa = new PmAccountMfa();
        mfa.setUserId(9L);
        mfa.setEnabled(true);
        when(mapper.selectByUserForUpdate(9L)).thenReturn(mfa);
        when(mapper.disableForUser(eq(9L), any(OffsetDateTime.class)))
            .thenReturn(1);
        StepUpTokenStore tokenStore = mock(StepUpTokenStore.class);
        when(tokenStore.consume(
            "disable-token",
            new StepUpGrant(9L, "session-a", "MFA_DISABLE")
        )).thenReturn(true);
        ISysUserService userService = mock(ISysUserService.class);
        SysUserVo user = new SysUserVo();
        user.setEmail("Owner@Example.com");
        when(userService.selectUserById(9L)).thenReturn(user);
        MailNotificationPublisher notifications =
            mock(MailNotificationPublisher.class);
        AccountMfaService service = service(
            mapper,
            tokenStore,
            userService,
            notifications
        );

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
             MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            stp.when(StpUtil::getTokenValue).thenReturn("session-a");

            service.disable("disable-token");
        }

        verify(mapper).disableForUser(eq(9L), any(OffsetDateTime.class));
        verify(tokenStore).revokeAll(9L);
        verify(notifications).mfaDisabled("owner@example.com", 9L);
    }

    @Test
    void disableIsIdempotentWhenMfaIsAlreadyDisabled() {
        AccountMfaMapper mapper = mock(AccountMfaMapper.class);
        PmAccountMfa mfa = new PmAccountMfa();
        mfa.setUserId(9L);
        mfa.setEnabled(false);
        when(mapper.selectByUserForUpdate(9L)).thenReturn(mfa);
        StepUpTokenStore tokenStore = mock(StepUpTokenStore.class);
        MailNotificationPublisher notifications =
            mock(MailNotificationPublisher.class);
        AccountMfaService service = service(
            mapper,
            tokenStore,
            mock(ISysUserService.class),
            notifications
        );

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            service.disable(null);
        }

        verify(mapper, never()).disableForUser(any(), any());
        verifyNoInteractions(tokenStore, notifications);
    }

    private AccountMfaMapper enabledMfaMapper(Long userId) {
        AccountMfaMapper mapper = mock(AccountMfaMapper.class);
        PmAccountMfa mfa = new PmAccountMfa();
        mfa.setUserId(userId);
        mfa.setEnabled(true);
        when(mapper.selectOne(any())).thenReturn(mfa);
        return mapper;
    }

    private AccountMfaService service(
        AccountMfaMapper mapper,
        StepUpTokenStore tokenStore,
        ISysUserService userService,
        MailNotificationPublisher notifications
    ) {
        return new AccountMfaService(
            mapper,
            mock(AccountMfaCipher.class),
            new PaymentProperties(),
            tokenStore,
            userService,
            notifications
        );
    }
}
