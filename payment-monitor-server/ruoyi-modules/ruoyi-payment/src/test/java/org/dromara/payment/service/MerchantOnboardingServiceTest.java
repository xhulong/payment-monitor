package org.dromara.payment.service;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.context.MerchantAccessService;
import org.dromara.payment.domain.PmMerchant;
import org.dromara.payment.domain.PmMerchantApplication;
import org.dromara.payment.domain.PmMerchantApplicationHistory;
import org.dromara.payment.domain.PmMerchantUser;
import org.dromara.payment.domain.dto.MerchantApplicationSaveRequest;
import org.dromara.payment.mapper.AccountMfaMapper;
import org.dromara.payment.mapper.MerchantApplicationHistoryMapper;
import org.dromara.payment.mapper.MerchantApplicationMapper;
import org.dromara.payment.mapper.MerchantMapper;
import org.dromara.payment.mapper.MerchantUserMapper;
import org.dromara.payment.mapper.PaymentDeviceMapper;
import org.dromara.payment.mapper.PaymentEventMapper;
import org.dromara.payment.mapper.QrAssetMapper;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.service.ISysUserService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.support.GenericApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class MerchantOnboardingServiceTest {

    @Test
    void platformAccountCannotApplyForMerchantOnboarding() {
        MerchantAccessService accessService = mock(MerchantAccessService.class);
        when(accessService.isCurrentAccountPlatformAccount()).thenReturn(true);
        ISysUserService userService = mock(ISysUserService.class);
        SysUserVo user = new SysUserVo();
        user.setEmail("platform@example.test");
        when(userService.selectUserById(9L)).thenReturn(user);
        MerchantOnboardingService service = service(accessService, userService);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);

            assertFalse(service.status().onboardingAvailable());
            assertThrows(
                ServiceException.class,
                () -> service.create(new MerchantApplicationSaveRequest()));
        }
    }

    @Test
    void autoApprovesSubmittedApplicationWhenReviewIsDisabled() {
        MerchantApplicationMapper applicationMapper =
            mock(MerchantApplicationMapper.class);
        MerchantApplicationHistoryMapper historyMapper =
            mock(MerchantApplicationHistoryMapper.class);
        MerchantMapper merchantMapper = mock(MerchantMapper.class);
        MerchantUserMapper merchantUserMapper = mock(MerchantUserMapper.class);
        MerchantAccessService accessService = mock(MerchantAccessService.class);
        ISysUserService userService = mock(ISysUserService.class);
        MailNotificationPublisher notifications =
            mock(MailNotificationPublisher.class);
        MerchantOnboardingReviewSettingsService reviewSettingsService =
            mock(MerchantOnboardingReviewSettingsService.class);
        when(reviewSettingsService.reviewEnabled()).thenReturn(false);
        when(merchantMapper.selectCount(any())).thenReturn(0L);

        PmMerchantApplication application = application(100L);
        application.setStatus("NEEDS_CHANGES");
        application.setReviewerId(200L);
        application.setClaimedAt(OffsetDateTime.parse("2026-08-03T10:00:00+08:00"));
        application.setReviewNote("请补充材料");
        when(applicationMapper.selectByIdForUpdate(1L)).thenReturn(application);

        MerchantOnboardingService service = new MerchantOnboardingService(
            applicationMapper,
            historyMapper,
            merchantMapper,
            merchantUserMapper,
            mock(AccountMfaMapper.class),
            mock(QrAssetMapper.class),
            mock(PaymentDeviceMapper.class),
            mock(PaymentEventMapper.class),
            accessService,
            userService,
            new PaymentProperties(),
            notifications,
            reviewSettingsService);

        try (GenericApplicationContext ignored = jsonSpringContext();
             MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(100L);

            PmMerchantApplication approved = service.submit(1L);

            assertEquals("APPROVED", approved.getStatus());
            assertNotNull(approved.getMerchantId());
            assertEquals("人工审核已关闭，系统自动通过", approved.getReviewNote());
            assertNull(approved.getReviewerId());
            assertNull(approved.getClaimedAt());
        }

        ArgumentCaptor<PmMerchant> merchantCaptor =
            ArgumentCaptor.forClass(PmMerchant.class);
        verify(merchantMapper).insert(merchantCaptor.capture());
        assertEquals("ONBOARDING", merchantCaptor.getValue().getLifecycleStatus());
        assertNull(merchantCaptor.getValue().getCreatedBy());

        ArgumentCaptor<PmMerchantUser> bindingCaptor =
            ArgumentCaptor.forClass(PmMerchantUser.class);
        verify(merchantUserMapper).insert(bindingCaptor.capture());
        assertEquals("OWNER", bindingCaptor.getValue().getRoleCode());
        assertNull(bindingCaptor.getValue().getCreatedBy());
        verify(merchantUserMapper).revokePaymentRoles(100L);
        verify(merchantUserMapper).grantRole(100L, 1900200000000000004L);

        ArgumentCaptor<PmMerchantApplicationHistory> historyCaptor =
            ArgumentCaptor.forClass(PmMerchantApplicationHistory.class);
        verify(historyMapper, times(2)).insert(historyCaptor.capture());
        List<PmMerchantApplicationHistory> history = historyCaptor.getAllValues();
        assertEquals("SUBMIT", history.get(0).getAction());
        assertEquals(100L, history.get(0).getOperatedBy());
        assertEquals("AUTO_APPROVE", history.get(1).getAction());
        assertNull(history.get(1).getOperatedBy());

        verify(notifications, never()).applicationSubmitted(application);
        verify(notifications).applicationReviewed(application);
    }

    @Test
    void optionalMfaDoesNotBlockMerchantActivation() {
        MerchantApplicationMapper applicationMapper =
            mock(MerchantApplicationMapper.class);
        MerchantMapper merchantMapper = mock(MerchantMapper.class);
        MerchantUserMapper merchantUserMapper = mock(MerchantUserMapper.class);
        AccountMfaMapper mfaMapper = mock(AccountMfaMapper.class);
        QrAssetMapper qrAssetMapper = mock(QrAssetMapper.class);
        PaymentDeviceMapper deviceMapper = mock(PaymentDeviceMapper.class);
        PaymentEventMapper eventMapper = mock(PaymentEventMapper.class);
        MerchantAccessService accessService = mock(MerchantAccessService.class);
        ISysUserService userService = mock(ISysUserService.class);
        MerchantOnboardingReviewSettingsService reviewSettingsService =
            mock(MerchantOnboardingReviewSettingsService.class);
        when(reviewSettingsService.reviewEnabled()).thenReturn(true);

        SysUserVo user = new SysUserVo();
        user.setEmail("owner@example.test");
        when(userService.selectUserById(100L)).thenReturn(user);
        when(applicationMapper.selectOne(any())).thenReturn(application(100L));

        PmMerchantUser binding = new PmMerchantUser();
        binding.setMerchantId(10L);
        binding.setUserId(100L);
        binding.setRoleCode("OWNER");
        when(merchantUserMapper.selectOne(any())).thenReturn(binding);

        PmMerchant merchant = new PmMerchant();
        merchant.setId(10L);
        merchant.setOwnerUserId(100L);
        merchant.setLifecycleStatus("ONBOARDING");
        merchant.setAgreementVersion("2026-07");
        merchant.setPrivacyVersion("2026-07");
        when(merchantMapper.selectById(10L)).thenReturn(merchant);
        when(qrAssetMapper.selectCount(any())).thenReturn(1L);
        when(deviceMapper.selectCount(any())).thenReturn(1L);
        when(eventMapper.selectCount(any())).thenReturn(1L);

        MerchantOnboardingService service = new MerchantOnboardingService(
            applicationMapper,
            mock(MerchantApplicationHistoryMapper.class),
            merchantMapper,
            merchantUserMapper,
            mfaMapper,
            qrAssetMapper,
            deviceMapper,
            eventMapper,
            accessService,
            userService,
            new PaymentProperties(),
            mock(MailNotificationPublisher.class),
            reviewSettingsService
        );

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(100L);
            var status = service.status();

            var mfaItem = status.checklist().stream()
                .filter(item -> "OWNER_TOTP".equals(item.code()))
                .findFirst()
                .orElseThrow();
            assertFalse(mfaItem.completed());
            assertFalse(mfaItem.required());
            assertEquals("ACTIVE", status.merchantLifecycle());
        }

        verify(merchantMapper).updateById(merchant);
    }

    private MerchantOnboardingService service(
        MerchantAccessService accessService,
        ISysUserService userService
    ) {
        MerchantOnboardingReviewSettingsService reviewSettingsService =
            mock(MerchantOnboardingReviewSettingsService.class);
        when(reviewSettingsService.reviewEnabled()).thenReturn(true);
        return new MerchantOnboardingService(
            mock(MerchantApplicationMapper.class),
            mock(MerchantApplicationHistoryMapper.class),
            mock(MerchantMapper.class),
            mock(MerchantUserMapper.class),
            mock(AccountMfaMapper.class),
            mock(QrAssetMapper.class),
            mock(PaymentDeviceMapper.class),
            mock(PaymentEventMapper.class),
            accessService,
            userService,
            new PaymentProperties(),
            mock(MailNotificationPublisher.class),
            reviewSettingsService);
    }

    private PmMerchantApplication application(Long userId) {
        PmMerchantApplication application = new PmMerchantApplication();
        application.setId(1L);
        application.setUserId(userId);
        application.setVerifiedEmail("owner@example.test");
        application.setMerchantDisplayName("Fixture Merchant");
        application.setApplicantName("Fixture Owner");
        application.setCountryRegion("CN");
        application.setPaymentUseCase("Payment notification acceptance");
        application.setMonthlyOrderRange("1-100");
        application.setMonthlyAmountRange("0-10000");
        application.setPlannedPlatforms("WECHAT,ALIPAY");
        application.setAgreementVersion("2026-07");
        application.setPrivacyVersion("2026-07");
        application.setStatus("DRAFT");
        application.setVersion(0);
        return application;
    }

    private GenericApplicationContext jsonSpringContext() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(JsonMapper.class, () -> JsonMapper.builder().build());
        context.refresh();
        new SpringUtils().setApplicationContext(context);
        return context;
    }
}
