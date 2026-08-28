package org.dromara.payment.service;

import org.dromara.common.core.constant.SystemConstants;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.domain.PmMerchantApplication;
import org.dromara.system.domain.SysUser;
import org.dromara.system.mapper.SysUserMapper;
import org.dromara.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class MailNotificationPublisherTest {

    @Test
    void applicationSubmittedNotifiesUniqueEnabledReviewersWithValidEmails() {
        ApplicationEventPublisher eventPublisher =
            mock(ApplicationEventPublisher.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        when(userRoleMapper.selectUserIdsByRoleId(
            PaymentConstants.PLATFORM_REVIEWER_ROLE_ID
        )).thenReturn(List.of(2L, 3L, 4L, 5L, 6L, 7L));
        when(userMapper.selectByIds(any())).thenReturn(List.of(
            user(SystemConstants.SUPER_ADMIN_USER_ID, " ADMIN@example.com ", "0", "0"),
            user(2L, "admin@example.com", "0", "0"),
            user(3L, "reviewer@example.com", "0", "0"),
            user(4L, "disabled@example.com", "1", "0"),
            user(5L, "deleted@example.com", "0", "1"),
            user(6L, "not-an-email", "0", "0"),
            user(7L, null, "0", "0")
        ));

        MailNotificationPublisher publisher = publisher(
            eventPublisher,
            userMapper,
            userRoleMapper
        );
        publisher.applicationSubmitted(application());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.times(3))
            .publishEvent(captor.capture());
        List<MailNotificationEvent> events = captor.getAllValues().stream()
            .map(MailNotificationEvent.class::cast)
            .toList();
        List<MailNotificationEvent> reviewEvents = events.stream()
            .filter(event -> "MERCHANT_APPLICATION_REVIEW_NOTICE".equals(
                event.messageType()
            ))
            .toList();

        assertEquals(
            Set.of("admin@example.com", "reviewer@example.com"),
            reviewEvents.stream().map(MailNotificationEvent::recipient).collect(
                java.util.stream.Collectors.toSet()
            )
        );
        assertTrue(reviewEvents.stream().allMatch(
            event -> event.subject().contains("新商户申请待审核")
                && event.html().contains("测试商户")
                && event.html().contains("张三")
                && event.html().contains("applicant@example.com")
                && event.html().contains(
                    "/payment/platform-developer/merchant-application"
                )
        ));
        assertTrue(reviewEvents.stream().anyMatch(
            event -> event.deduplicationKey().endsWith(
                ":" + SystemConstants.SUPER_ADMIN_USER_ID
            )
        ));
        assertTrue(reviewEvents.stream().anyMatch(
            event -> event.deduplicationKey().endsWith(":3")
        ));
    }

    @Test
    void reviewerLookupFailureDoesNotPreventApplicationSubmission() {
        ApplicationEventPublisher eventPublisher =
            mock(ApplicationEventPublisher.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        when(userRoleMapper.selectUserIdsByRoleId(
            PaymentConstants.PLATFORM_REVIEWER_ROLE_ID
        )).thenThrow(new IllegalStateException("fixture unavailable"));

        MailNotificationPublisher publisher = publisher(
            eventPublisher,
            userMapper,
            userRoleMapper
        );

        assertDoesNotThrow(() -> publisher.applicationSubmitted(application()));
        verify(eventPublisher).publishEvent(any(MailNotificationEvent.class));
    }

    @Test
    void applicantMailPublisherFailureDoesNotEscapeSubmissionFlow() {
        ApplicationEventPublisher eventPublisher =
            mock(ApplicationEventPublisher.class);
        doThrow(new IllegalStateException("fixture unavailable"))
            .when(eventPublisher)
            .publishEvent(any(MailNotificationEvent.class));
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);

        MailNotificationPublisher publisher = publisher(
            eventPublisher,
            userMapper,
            userRoleMapper
        );

        assertDoesNotThrow(() -> publisher.applicationSubmitted(application()));
    }

    private MailNotificationPublisher publisher(
        ApplicationEventPublisher eventPublisher,
        SysUserMapper userMapper,
        SysUserRoleMapper userRoleMapper
    ) {
        PaymentProperties properties = new PaymentProperties();
        properties.setPublicBaseUrl("https://pay.example.com/");
        return new MailNotificationPublisher(
            eventPublisher,
            new MailTemplateService(),
            properties,
            userMapper,
            userRoleMapper
        );
    }

    private PmMerchantApplication application() {
        PmMerchantApplication application = new PmMerchantApplication();
        application.setId(100L);
        application.setVersion(2);
        application.setMerchantDisplayName("测试商户");
        application.setApplicantName("张三");
        application.setVerifiedEmail("applicant@example.com");
        return application;
    }

    private SysUser user(
        Long id,
        String email,
        String status,
        String delFlag
    ) {
        SysUser user = new SysUser();
        user.setUserId(id);
        user.setEmail(email);
        user.setStatus(status);
        user.setDelFlag(delFlag);
        return user;
    }
}
