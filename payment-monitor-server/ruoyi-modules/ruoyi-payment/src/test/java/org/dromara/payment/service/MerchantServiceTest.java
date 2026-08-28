package org.dromara.payment.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.domain.PmMerchant;
import org.dromara.payment.domain.bo.MerchantQueryBo;
import org.dromara.payment.domain.vo.MerchantAdminBindingVo;
import org.dromara.payment.domain.vo.MerchantVo;
import org.dromara.payment.mapper.MerchantMapper;
import org.dromara.payment.mapper.MerchantUserMapper;
import org.dromara.payment.context.MerchantAccessService;
import org.dromara.system.service.ISysUserService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class MerchantServiceTest {

    @Test
    void merchantPageEnrichesAdministratorsWithOneBatchQuery() {
        MerchantMapper merchantMapper = mock(MerchantMapper.class);
        MerchantUserMapper merchantUserMapper = mock(MerchantUserMapper.class);
        PmMerchant first = merchant(1L, "M001");
        PmMerchant second = merchant(2L, "M002");
        Page<PmMerchant> page = new Page<>(1, 10, 2);
        page.setRecords(List.of(first, second));
        when(merchantMapper.selectPage(any(), any())).thenReturn(page);

        MerchantAdminBindingVo firstAdmin = binding(1L, 101L, "admin-one");
        MerchantAdminBindingVo secondAdmin = binding(2L, 202L, "admin-two");
        when(merchantUserMapper.selectAdminBindings(any()))
            .thenReturn(List.of(firstAdmin, secondAdmin));

        MerchantService service = new MerchantService(
            merchantMapper,
            merchantUserMapper,
            mock(MerchantAccessService.class),
            mock(ISysUserService.class));

        MerchantContext.set(
            MerchantContext.PLATFORM_ACCOUNT,
            MerchantContext.ALL_SCOPE,
            null,
            true,
            MerchantContext.PLATFORM_TIMEZONE);
        try {
            Collection<MerchantVo> rows = service.queryPage(
                new MerchantQueryBo(),
                new PageQuery()).getRows();

            assertEquals(2, rows.size());
            List<MerchantVo> rowList = List.copyOf(rows);
            assertEquals(101L, rowList.getFirst().getAdminUserId());
            assertEquals("admin-one", rowList.getFirst().getAdminUserName());
            assertEquals(202L, rowList.get(1).getAdminUserId());
            assertEquals("admin-two", rowList.get(1).getAdminUserName());
            verify(merchantUserMapper).selectAdminBindings(any());
            verify(merchantUserMapper, never()).selectUserName(any());
        } finally {
            MerchantContext.clear();
        }
    }

    private PmMerchant merchant(Long id, String code) {
        PmMerchant merchant = new PmMerchant();
        merchant.setId(id);
        merchant.setMerchantCode(code);
        merchant.setName(code);
        merchant.setStatus("0");
        merchant.setLifecycleStatus("ACTIVE");
        merchant.setTimezone("Asia/Shanghai");
        return merchant;
    }

    private MerchantAdminBindingVo binding(
        Long merchantId,
        Long userId,
        String userName
    ) {
        MerchantAdminBindingVo binding = new MerchantAdminBindingVo();
        binding.setMerchantId(merchantId);
        binding.setUserId(userId);
        binding.setUserName(userName);
        return binding;
    }
}
