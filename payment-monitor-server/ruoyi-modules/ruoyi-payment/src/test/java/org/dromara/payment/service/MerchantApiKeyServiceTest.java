package org.dromara.payment.service;

import org.dromara.payment.context.MerchantAccessService;
import org.dromara.payment.domain.PmMerchantApiKey;
import org.dromara.payment.domain.vo.MerchantApiKeyVo;
import org.dromara.payment.mapper.MerchantApiCredentialMapper;
import org.dromara.payment.mapper.MerchantApiKeyMapper;
import org.dromara.payment.security.DeviceSecretCipher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class MerchantApiKeyServiceTest {

    @Test
    void listMapsEntitiesWithoutDependingOnGeneratedVoConverter() {
        MerchantApiKeyMapper keyMapper = mock(MerchantApiKeyMapper.class);
        MerchantAccessService accessService = mock(MerchantAccessService.class);
        MerchantApiKeyService service = new MerchantApiKeyService(
            keyMapper,
            mock(MerchantApiCredentialMapper.class),
            accessService,
            mock(DeviceSecretCipher.class)
        );
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        PmMerchantApiKey key = new PmMerchantApiKey();
        key.setId(21L);
        key.setMerchantId(11L);
        key.setKeyId("mk_test");
        key.setKeyName("订单系统");
        key.setStatus("0");
        key.setCurrentVersion(3);
        key.setLastUsedAt(now);
        key.setCreatedAt(now.minusDays(1));
        key.setUpdatedAt(now);
        when(keyMapper.selectList(any())).thenReturn(List.of(key));

        List<MerchantApiKeyVo> result = service.list(11L);

        verify(accessService).requireAccessible(11L);
        assertEquals(1, result.size());
        MerchantApiKeyVo vo = result.getFirst();
        assertEquals(21L, vo.getId());
        assertEquals(11L, vo.getMerchantId());
        assertEquals("mk_test", vo.getKeyId());
        assertEquals("订单系统", vo.getKeyName());
        assertEquals("0", vo.getStatus());
        assertEquals(3, vo.getCurrentVersion());
        assertEquals(now, vo.getLastUsedAt());
    }
}
