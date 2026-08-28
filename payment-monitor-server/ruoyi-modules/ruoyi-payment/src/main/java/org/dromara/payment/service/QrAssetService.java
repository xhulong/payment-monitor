package org.dromara.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.context.MerchantAccessService;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.domain.PmQrAsset;
import org.dromara.payment.domain.PmPaymentOrder;
import org.dromara.payment.domain.bo.QrAssetQueryBo;
import org.dromara.payment.domain.dto.QrAssetSaveRequest;
import org.dromara.payment.domain.vo.QrAssetVo;
import org.dromara.payment.mapper.QrAssetMapper;
import org.dromara.payment.mapper.PaymentOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QrAssetService {

    private final QrAssetMapper mapper;
    private final PaymentOrderMapper orderMapper;
    private final MerchantAccessService merchantAccessService;
    private final MerchantDisplayService merchantDisplayService;

    public PageResult<QrAssetVo> queryPage(QrAssetQueryBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PmQrAsset> wrapper = buildQuery(bo)
            .orderByDesc(PmQrAsset::getCreatedAt);
        Page<QrAssetVo> page = mapper.selectVoPage(pageQuery.build(), wrapper);
        merchantDisplayService.enrich(
            page.getRecords(),
            QrAssetVo::getMerchantId,
            QrAssetVo::setMerchantCode,
            QrAssetVo::setMerchantName);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    public List<QrAssetVo> enabledAssets(String platform, Long requestedMerchantId) {
        Long merchantId = merchantAccessService.requireTargetMerchant(requestedMerchantId, true);
        List<QrAssetVo> rows = mapper.selectVoList(new LambdaQueryWrapper<PmQrAsset>()
            .eq(PmQrAsset::getMerchantId, merchantId)
            .eq(StringUtils.isNotBlank(platform), PmQrAsset::getPlatform, platform)
            .eq(PmQrAsset::getStatus, PaymentConstants.DEVICE_STATUS_ENABLED)
            .orderByAsc(PmQrAsset::getAssetName));
        merchantDisplayService.enrich(
            rows,
            QrAssetVo::getMerchantId,
            QrAssetVo::setMerchantCode,
            QrAssetVo::setMerchantName);
        return rows;
    }

    public QrAssetVo queryById(Long id) {
        QrAssetVo vo = mapper.selectVoOne(new LambdaQueryWrapper<PmQrAsset>()
            .eq(PmQrAsset::getId, id)
            .last("limit 1"));
        if (vo == null) {
            throw new ServiceException("收款二维码不存在");
        }
        MerchantContext.requireAccessibleMerchant(vo.getMerchantId());
        merchantDisplayService.enrich(
            List.of(vo),
            QrAssetVo::getMerchantId,
            QrAssetVo::setMerchantCode,
            QrAssetVo::setMerchantName);
        return vo;
    }

    public PmQrAsset requireEnabled(Long merchantId, Long id, String platform) {
        PmQrAsset asset = mapper.selectOne(new LambdaQueryWrapper<PmQrAsset>()
            .eq(PmQrAsset::getId, id)
            .eq(PmQrAsset::getMerchantId, merchantId)
            .last("limit 1"));
        if (asset == null
            || !PaymentConstants.DEVICE_STATUS_ENABLED.equals(asset.getStatus())) {
            throw new ServiceException("收款二维码不存在或已停用");
        }
        if (!asset.getPlatform().equals(platform)) {
            throw new ServiceException("订单平台与收款二维码平台不一致");
        }
        return asset;
    }

    public PmQrAsset requireEnabled(Long id, String platform) {
        return requireEnabled(MerchantContext.requireMerchantId(), id, platform);
    }

    public PmQrAsset requireEnabledByCode(Long merchantId, String assetCode, String platform) {
        PmQrAsset asset = mapper.selectOne(new LambdaQueryWrapper<PmQrAsset>()
            .eq(PmQrAsset::getMerchantId, merchantId)
            .eq(PmQrAsset::getAssetCode, assetCode)
            .eq(PmQrAsset::getStatus, PaymentConstants.DEVICE_STATUS_ENABLED)
            .last("limit 1"));
        if (asset == null) {
            throw new org.dromara.payment.api.MerchantApiException(
                404, "QR_ASSET_NOT_FOUND", "二维码资产不存在或已停用", false);
        }
        if (!asset.getPlatform().equals(platform)) {
            throw new org.dromara.payment.api.MerchantApiException(
                400, "VALIDATION_FAILED", "订单平台与二维码平台不一致", false);
        }
        return asset;
    }

    public QrAssetVo create(QrAssetSaveRequest request) {
        OffsetDateTime now = now();
        PmQrAsset asset = new PmQrAsset();
        asset.setMerchantId(merchantAccessService.requireTargetMerchant(request.getMerchantId(), false));
        asset.setAssetCode(normalizeAssetCode(request.getAssetCode()));
        asset.setPlatform(request.getPlatform());
        asset.setAssetName(request.getAssetName());
        asset.setQrContentTemplate(request.getQrContentTemplate());
        asset.setStatus(request.getStatus());
        asset.setRemark(request.getRemark());
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);
        mapper.insert(asset);
        return queryById(asset.getId());
    }

    public QrAssetVo update(Long id, QrAssetSaveRequest request) {
        PmQrAsset asset = mapper.selectById(id);
        if (asset == null) {
            throw new ServiceException("收款二维码不存在");
        }
        MerchantContext.requireAccessibleMerchant(asset.getMerchantId());
        if (StringUtils.isNotBlank(request.getAssetCode())) {
            asset.setAssetCode(request.getAssetCode().trim());
        }
        asset.setPlatform(request.getPlatform());
        asset.setAssetName(request.getAssetName());
        asset.setQrContentTemplate(request.getQrContentTemplate());
        asset.setStatus(request.getStatus());
        asset.setRemark(request.getRemark());
        asset.setUpdatedAt(now());
        mapper.updateById(asset);
        return queryById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(List<Long> ids, String status) {
        List<Long> distinctIds = ids.stream().distinct().toList();
        List<PmQrAsset> assets = mapper.selectList(new LambdaQueryWrapper<PmQrAsset>()
            .in(PmQrAsset::getId, distinctIds));
        if (assets.size() != distinctIds.size()) {
            throw new ServiceException("部分收款二维码不存在或不属于当前商户");
        }
        MerchantContext.requireSingleAccessibleMerchant(
            assets.stream().map(PmQrAsset::getMerchantId).toList());
        OffsetDateTime timestamp = now();
        assets.forEach(asset -> {
            asset.setStatus(status);
            asset.setUpdatedAt(timestamp);
            mapper.updateById(asset);
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteUnused(List<Long> ids) {
        List<Long> distinctIds = ids.stream().distinct().toList();
        List<PmQrAsset> assets = mapper.selectList(new LambdaQueryWrapper<PmQrAsset>()
            .in(PmQrAsset::getId, distinctIds));
        if (assets.size() != distinctIds.size()) {
            throw new ServiceException("部分收款二维码不存在或不属于当前商户");
        }
        Long merchantId = MerchantContext.requireSingleAccessibleMerchant(
            assets.stream().map(PmQrAsset::getMerchantId).toList());
        long used = orderMapper.selectCount(new LambdaQueryWrapper<PmPaymentOrder>()
            .eq(PmPaymentOrder::getMerchantId, merchantId)
            .in(PmPaymentOrder::getQrAssetId, distinctIds));
        if (used > 0) {
            throw new ServiceException("选中的二维码已产生支付订单，只能停用，不能删除");
        }
        mapper.deleteBatchIds(distinctIds);
    }

    private LambdaQueryWrapper<PmQrAsset> buildQuery(QrAssetQueryBo bo) {
        Long merchantId = MerchantContext.resolveQueryMerchantId(bo.getMerchantId());
        return new LambdaQueryWrapper<PmQrAsset>()
            .eq(merchantId != null, PmQrAsset::getMerchantId, merchantId)
            .eq(StringUtils.isNotBlank(bo.getPlatform()), PmQrAsset::getPlatform, bo.getPlatform())
            .like(StringUtils.isNotBlank(bo.getAssetName()), PmQrAsset::getAssetName, bo.getAssetName())
            .eq(StringUtils.isNotBlank(bo.getStatus()), PmQrAsset::getStatus, bo.getStatus());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String normalizeAssetCode(String value) {
        return StringUtils.isBlank(value)
            ? "QR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase()
            : value.trim();
    }
}
