package org.dromara.payment.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import org.dromara.common.mybatis.core.mapper.LambdaCrudChainWrapper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.domain.dto.EmailChangeConfirmRequest;
import org.dromara.system.domain.SysUser;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.mapper.SysUserMapper;
import org.dromara.system.service.ISysUserService;
import org.dromara.system.service.RefreshSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.dromara.payment.service.AccountRecoveryChallengeService.ChallengeType.EMAIL_CHANGE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class AccountRecoveryServiceTest {

    @Test
    void emailChangeIgnoresAdministrativeDataScopeForOwnAccountUpdate() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        @SuppressWarnings("unchecked")
        LambdaCrudChainWrapper<SysUser, SysUserVo> query =
            mock(LambdaCrudChainWrapper.class);
        doReturn(query).when(userMapper).lambda();
        doReturn(query).when(query).apply(anyString(), any());
        when(query.voOne()).thenReturn(null);

        ISysUserService userService = mock(ISysUserService.class);
        SysUserVo current = new SysUserVo();
        current.setUserId(100L);
        current.setEmail("old@example.test");
        when(userService.selectUserById(100L)).thenReturn(current);

        AccountRecoveryChallengeService challengeService =
            mock(AccountRecoveryChallengeService.class);
        when(challengeService.verify(
            EMAIL_CHANGE,
            100L,
            "new@example.test",
            "123456"
        )).thenReturn(9001L);

        when(userMapper.updateById(any(SysUser.class))).thenAnswer(invocation -> {
            assertTrue(InterceptorIgnoreHelper.willIgnoreDataPermission(
                SysUserMapper.class.getName() + ".updateById"
            ));
            return 1;
        });

        RefreshSessionService refreshSessionService =
            mock(RefreshSessionService.class);
        AccountMfaService mfaService = mock(AccountMfaService.class);
        MailNotificationPublisher mailNotificationPublisher =
            mock(MailNotificationPublisher.class);
        AccountRecoveryService service = new AccountRecoveryService(
            new PaymentProperties(),
            mock(MailSettingsService.class),
            userMapper,
            userService,
            mfaService,
            refreshSessionService,
            challengeService,
            mock(MailOutboxService.class),
            mock(MailTemplateService.class),
            mailNotificationPublisher
        );

        EmailChangeConfirmRequest request = new EmailChangeConfirmRequest();
        request.setNewEmail(" New@Example.Test ");
        request.setCode("123456");

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
             MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            login.when(LoginHelper::getUserId).thenReturn(100L);

            service.confirmEmailChange(request);

            stp.verify(() -> StpUtil.logout(100L));
        }

        assertFalse(InterceptorIgnoreHelper.willIgnoreDataPermission(
            SysUserMapper.class.getName() + ".updateById"
        ));
        verify(challengeService).consume(
            9001L,
            EMAIL_CHANGE,
            100L,
            "new@example.test",
            "123456"
        );
        verify(refreshSessionService).revokeAll(100L, "EMAIL_CHANGE");
        verify(mfaService).revokeStepUpTokens(100L);
        verify(mailNotificationPublisher).emailChanged(
            "old@example.test",
            "new@example.test",
            100L
        );
    }
}
