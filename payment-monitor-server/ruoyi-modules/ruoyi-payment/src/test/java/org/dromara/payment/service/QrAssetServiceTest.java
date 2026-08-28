package org.dromara.payment.service;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.dromara.payment.context.MerchantAccessService;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.domain.PmQrAsset;
import org.dromara.payment.domain.dto.QrAssetSaveRequest;
import org.dromara.payment.domain.vo.QrAssetVo;
import org.dromara.payment.mapper.PaymentOrderMapper;
import org.dromara.payment.mapper.QrAssetMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class QrAssetServiceTest {

    private final Validator validator =
        Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void blankAssetCodeIsValidBecauseCreateGeneratesOne() {
        QrAssetSaveRequest request = request("");

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void invalidManualAssetCodeIsRejected() {
        QrAssetSaveRequest request = request("测试 编码");

        assertEquals(1, validator.validate(request).size());
    }

    @Test
    void createGeneratesAssetCodeWhenRequestUsesBlankValue() {
        QrAssetMapper mapper = mock(QrAssetMapper.class);
        PaymentOrderMapper orderMapper = mock(PaymentOrderMapper.class);
        AtomicReference<PmQrAsset> inserted = new AtomicReference<>();
        when(mapper.insert(any(PmQrAsset.class))).thenAnswer(invocation -> {
            PmQrAsset asset = invocation.getArgument(0);
            asset.setId(11L);
            inserted.set(asset);
            return 1;
        });
        when(mapper.selectVoOne(any())).thenAnswer(invocation -> toVo(inserted.get()));
        MerchantAccessService merchantAccessService = mock(MerchantAccessService.class);
        when(merchantAccessService.requireTargetMerchant(null, false)).thenReturn(7L);
        QrAssetService service = new QrAssetService(
            mapper,
            orderMapper,
            merchantAccessService,
            mock(MerchantDisplayService.class));

        MerchantContext.set(7L, false);
        try {
            QrAssetVo result = service.create(request("   "));

            assertTrue(result.getAssetCode().matches("QR-[A-F0-9]{16}"));
            assertEquals(7L, result.getMerchantId());
        } finally {
            MerchantContext.clear();
        }
    }

    private QrAssetSaveRequest request(String assetCode) {
        QrAssetSaveRequest request = new QrAssetSaveRequest();
        request.setAssetCode(assetCode);
        request.setPlatform("WECHAT");
        request.setAssetName("测试收款码");
        request.setQrContentTemplate("wxp://example");
        request.setStatus("0");
        return request;
    }

    private QrAssetVo toVo(PmQrAsset asset) {
        QrAssetVo vo = new QrAssetVo();
        vo.setId(asset.getId());
        vo.setMerchantId(asset.getMerchantId());
        vo.setAssetCode(asset.getAssetCode());
        vo.setPlatform(asset.getPlatform());
        vo.setAssetName(asset.getAssetName());
        vo.setQrContentTemplate(asset.getQrContentTemplate());
        vo.setStatus(asset.getStatus());
        vo.setRemark(asset.getRemark());
        vo.setCreatedAt(asset.getCreatedAt());
        vo.setUpdatedAt(asset.getUpdatedAt());
        return vo;
    }
}
