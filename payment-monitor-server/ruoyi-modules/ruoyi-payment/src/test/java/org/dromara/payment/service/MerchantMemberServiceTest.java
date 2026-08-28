package org.dromara.payment.service;

import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.context.MerchantAccessService;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.domain.PmMerchantUser;
import org.dromara.payment.domain.dto.MerchantInvitationCreateRequest;
import org.dromara.payment.mapper.AccountMfaMapper;
import org.dromara.payment.mapper.MerchantInvitationMapper;
import org.dromara.payment.mapper.MerchantMapper;
import org.dromara.payment.mapper.MerchantUserMapper;
import org.dromara.system.service.ISysUserService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class MerchantMemberServiceTest {

    @Test
    void invitationMailUsesChineseRoleLabel() {
        MerchantUserMapper memberMapper = mock(MerchantUserMapper.class);
        PmMerchantUser operator = new PmMerchantUser();
        operator.setMerchantId(1L);
        operator.setUserId(10L);
        operator.setRoleCode(PaymentConstants.MEMBER_OWNER);
        operator.setStatus("0");
        when(memberMapper.selectOne(any())).thenReturn(operator);
        when(memberMapper.selectUserIdByEmail(anyString())).thenReturn(null);

        MailOutboxService mailOutboxService = mock(MailOutboxService.class);
        MerchantAccessService merchantAccessService = mock(MerchantAccessService.class);
        when(merchantAccessService.requireTargetMerchant(null, true)).thenReturn(1L);
        MerchantMemberService service = new MerchantMemberService(
            memberMapper,
            mock(MerchantInvitationMapper.class),
            mock(MerchantMapper.class),
            mock(AccountMfaMapper.class),
            mock(ISysUserService.class),
            new PaymentProperties(),
            mailOutboxService,
            new MailTemplateService(),
            merchantAccessService,
            mock(MerchantDisplayService.class)
        );
        MerchantInvitationCreateRequest request =
            new MerchantInvitationCreateRequest();
        request.setEmail("viewer@example.com");
        request.setRoleCode(PaymentConstants.MEMBER_VIEWER);

        MerchantContext.set(1L, false);
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(10L);
            service.invite(request);
        } finally {
            MerchantContext.clear();
        }

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailOutboxService).enqueueHtml(
            anyString(),
            anyString(),
            anyString(),
            html.capture(),
            anyString(),
            any()
        );
        assertTrue(html.getValue().contains("岗位为 只读"));
        assertFalse(html.getValue().contains("岗位为 VIEWER"));
    }
}
