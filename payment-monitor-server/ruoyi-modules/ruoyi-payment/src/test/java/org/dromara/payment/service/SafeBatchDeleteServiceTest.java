package org.dromara.payment.service;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.context.MerchantAccessService;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.domain.PmAppRelease;
import org.dromara.payment.domain.PmQrAsset;
import org.dromara.payment.domain.PmWebhookEndpoint;
import org.dromara.payment.mapper.AppReleaseMapper;
import org.dromara.payment.mapper.PaymentOrderMapper;
import org.dromara.payment.mapper.QrAssetMapper;
import org.dromara.payment.mapper.WebhookEndpointMapper;
import org.dromara.payment.mapper.WebhookOutboxMapper;
import org.dromara.payment.security.DeviceSecretCipher;
import org.dromara.payment.security.WebhookUrlValidator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class SafeBatchDeleteServiceTest {

    @Test
    void qrAssetWithOrderHistoryCannotBeDeleted() {
        QrAssetMapper mapper = mock(QrAssetMapper.class);
        PaymentOrderMapper orderMapper = mock(PaymentOrderMapper.class);
        PmQrAsset asset = new PmQrAsset();
        asset.setId(11L);
        asset.setMerchantId(7L);
        when(mapper.selectList(any())).thenReturn(List.of(asset));
        when(orderMapper.selectCount(any())).thenReturn(1L);
        QrAssetService service = new QrAssetService(
            mapper,
            orderMapper,
            mock(MerchantAccessService.class),
            mock(MerchantDisplayService.class));

        MerchantContext.set(7L, false);
        try {
            ServiceException exception = assertThrows(
                ServiceException.class,
                () -> service.deleteUnused(List.of(11L))
            );
            assertEquals("选中的二维码已产生支付订单，只能停用，不能删除", exception.getMessage());
            verify(mapper, never()).deleteBatchIds(any());
        } finally {
            MerchantContext.clear();
        }
    }

    @Test
    void webhookEndpointWithDeliveryHistoryCannotBeDeleted() {
        WebhookEndpointMapper mapper = mock(WebhookEndpointMapper.class);
        WebhookOutboxMapper outboxMapper = mock(WebhookOutboxMapper.class);
        PmWebhookEndpoint endpoint = new PmWebhookEndpoint();
        endpoint.setId(21L);
        endpoint.setMerchantId(7L);
        when(mapper.selectList(any())).thenReturn(List.of(endpoint));
        when(outboxMapper.selectCount(any())).thenReturn(1L);
        WebhookEndpointService service = new WebhookEndpointService(
            mapper,
            outboxMapper,
            mock(DeviceSecretCipher.class),
            mock(WebhookUrlValidator.class),
            mock(MerchantAccessService.class),
            mock(MerchantDisplayService.class)
        );

        MerchantContext.set(7L, false);
        try {
            ServiceException exception = assertThrows(
                ServiceException.class,
                () -> service.deleteUnused(List.of(21L))
            );
            assertEquals("选中的 Webhook 已产生投递记录，只能停用，不能删除", exception.getMessage());
            verify(mapper, never()).deleteBatchIds(any());
        } finally {
            MerchantContext.clear();
        }
    }

    @Test
    void latestPublishedAppReleaseCannotBeDeleted() {
        AppReleaseMapper mapper = mock(AppReleaseMapper.class);
        PmAppRelease release = new PmAppRelease();
        release.setId(31L);
        release.setPlatform("ANDROID");
        release.setVersionCode(13);
        release.setStatus("PUBLISHED");
        when(mapper.selectList(any())).thenReturn(List.of(release));
        when(mapper.selectOne(any())).thenReturn(release);
        S3Client s3Client = mock(S3Client.class);
        AppReleaseService service =
            new AppReleaseService(
                mapper,
                new PaymentProperties(),
                s3Client,
                mock(ApkInspectionService.class));

        ServiceException exception = assertThrows(
            ServiceException.class,
            () -> service.deleteReleases(List.of(31L))
        );
        assertEquals("当前线上最新版本不能删除，请先发布更高版本", exception.getMessage());
        verify(s3Client, never()).deleteObject(any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class));
        verify(mapper, never()).deleteBatchIds(any());
    }

    @Test
    void historicalPublishedAppReleaseCanBeDeleted() {
        AppReleaseMapper mapper = mock(AppReleaseMapper.class);
        PmAppRelease historical = new PmAppRelease();
        historical.setId(31L);
        historical.setPlatform("ANDROID");
        historical.setVersionCode(12);
        historical.setStatus("PUBLISHED");
        historical.setObjectKey("android/12-1.0.0.apk");
        PmAppRelease latest = new PmAppRelease();
        latest.setId(32L);
        latest.setPlatform("ANDROID");
        latest.setVersionCode(13);
        latest.setStatus("PUBLISHED");
        when(mapper.selectList(any())).thenReturn(List.of(historical));
        when(mapper.selectOne(any())).thenReturn(latest);
        S3Client s3Client = mock(S3Client.class);
        AppReleaseService service =
            new AppReleaseService(
                mapper,
                new PaymentProperties(),
                s3Client,
                mock(ApkInspectionService.class));

        service.deleteReleases(List.of(31L));

        verify(s3Client).deleteObject(any(
            software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class));
        verify(mapper).deleteBatchIds(List.of(31L));
    }
}
