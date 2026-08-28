package org.dromara.payment.context;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.domain.PmMerchant;
import org.dromara.payment.domain.PmMerchantUser;
import org.dromara.payment.mapper.MerchantMapper;
import org.dromara.payment.mapper.MerchantUserMapper;
import org.dromara.system.api.model.LoginUser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("dev")
class MerchantAccessServiceTest {

    @Test
    void superAdminDefaultsToAllMerchantsAndMaySelectMerchantFilter() {
        MerchantMapper merchantMapper = mock(MerchantMapper.class);
        MerchantUserMapper userMapper = mock(MerchantUserMapper.class);
        PmMerchant selectedMerchant = merchant(2L, "MERCHANT_B");
        when(merchantMapper.selectById(2L)).thenReturn(selectedMerchant);
        MerchantAccessService service = new MerchantAccessService(merchantMapper, userMapper);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(1L);
            login.when(() -> LoginHelper.isSuperAdmin(1L)).thenReturn(true);

            MerchantAccessService.Resolution all = service.resolve(null);
            MerchantAccessService.Resolution selected = service.resolve("2");

            assertNull(all.merchant());
            assertEquals(MerchantContext.PLATFORM_ACCOUNT, all.accountType());
            assertEquals(MerchantContext.ALL_SCOPE, all.scopeMode());
            assertTrue(all.canAccessAllMerchants());
            assertEquals(MerchantContext.PLATFORM_TIMEZONE, all.displayTimezone());
            assertEquals(2L, selected.merchant().getId());
            assertEquals(MerchantContext.MERCHANT_SCOPE, selected.scopeMode());
            assertTrue(selected.canAccessAllMerchants());
        }
    }

    @Test
    void platformPermissionAllowsAllMerchantsWithoutSuperAdminRole() {
        MerchantMapper merchantMapper = mock(MerchantMapper.class);
        MerchantUserMapper userMapper = mock(MerchantUserMapper.class);
        MerchantAccessService service = new MerchantAccessService(merchantMapper, userMapper);
        LoginUser loginUser = new LoginUser();
        loginUser.setMenuPermission(Set.of(MerchantAccessService.ALL_MERCHANTS_PERMISSION));

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(8L);
            login.when(() -> LoginHelper.isSuperAdmin(8L)).thenReturn(false);
            login.when(LoginHelper::getLoginUser).thenReturn(loginUser);

            MerchantAccessService.Resolution resolution = service.resolve(null);

            assertEquals(MerchantContext.PLATFORM_ACCOUNT, resolution.accountType());
            assertEquals(MerchantContext.ALL_SCOPE, resolution.scopeMode());
            assertTrue(resolution.canAccessAllMerchants());
        }
    }

    @Test
    void platformReviewerDoesNotNeedMerchantBindingOrReceiveDataScope() {
        MerchantMapper merchantMapper = mock(MerchantMapper.class);
        MerchantUserMapper userMapper = mock(MerchantUserMapper.class);
        MerchantAccessService service = new MerchantAccessService(merchantMapper, userMapper);
        LoginUser loginUser = new LoginUser();
        loginUser.setRolePermission(Set.of(MerchantAccessService.PLATFORM_REVIEWER_ROLE));

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            login.when(() -> LoginHelper.isSuperAdmin(9L)).thenReturn(false);
            login.when(LoginHelper::getLoginUser).thenReturn(loginUser);

            MerchantAccessService.Resolution resolution = service.resolve(null);

            assertNull(resolution.merchant());
            assertEquals(MerchantContext.PLATFORM_ACCOUNT, resolution.accountType());
            assertEquals(MerchantContext.ALL_SCOPE, resolution.scopeMode());
            assertFalse(resolution.canAccessAllMerchants());
            assertThrows(ServiceException.class, () -> service.resolve("2"));
        }
    }

    @Test
    void merchantAdminIsPinnedToBindingAndCannotOverrideHeader() {
        MerchantMapper merchantMapper = mock(MerchantMapper.class);
        MerchantUserMapper userMapper = mock(MerchantUserMapper.class);
        PmMerchant merchant = merchant(11L, "MERCHANT_A");
        PmMerchantUser binding = new PmMerchantUser();
        binding.setUserId(100L);
        binding.setMerchantId(11L);
        when(userMapper.selectOne(any())).thenReturn(binding);
        when(merchantMapper.selectById(11L)).thenReturn(merchant);
        MerchantAccessService service = new MerchantAccessService(merchantMapper, userMapper);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(100L);
            login.when(() -> LoginHelper.isSuperAdmin(100L)).thenReturn(false);

            MerchantAccessService.Resolution resolved = service.resolve(null);
            assertEquals(11L, resolved.merchant().getId());
            assertEquals(MerchantContext.MERCHANT_ACCOUNT, resolved.accountType());
            assertEquals(MerchantContext.MERCHANT_SCOPE, resolved.scopeMode());
            assertFalse(resolved.canAccessAllMerchants());
            assertThrows(ServiceException.class, () -> service.resolve("12"));
        }
    }

    private PmMerchant merchant(Long id, String code) {
        PmMerchant merchant = new PmMerchant();
        merchant.setId(id);
        merchant.setMerchantCode(code);
        merchant.setName(code);
        merchant.setStatus(PaymentConstants.DEVICE_STATUS_ENABLED);
        merchant.setTimezone("Asia/Shanghai");
        return merchant;
    }
}
