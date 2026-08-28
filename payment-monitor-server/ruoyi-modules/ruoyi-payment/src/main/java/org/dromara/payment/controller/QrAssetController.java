package org.dromara.payment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.payment.domain.bo.QrAssetQueryBo;
import org.dromara.payment.domain.dto.BatchIdsRequest;
import org.dromara.payment.domain.dto.BatchStatusRequest;
import org.dromara.payment.domain.dto.QrAssetSaveRequest;
import org.dromara.payment.domain.vo.QrAssetVo;
import org.dromara.payment.service.QrAssetService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/payment/qr-assets")
public class QrAssetController {

    private final QrAssetService service;

    @SaCheckPermission("payment:qrcode:list")
    @GetMapping("/list")
    public R<PageResult<QrAssetVo>> list(QrAssetQueryBo bo, PageQuery pageQuery) {
        return R.ok(service.queryPage(bo, pageQuery));
    }

    @SaCheckPermission("payment:qrcode:list")
    @GetMapping("/enabled")
    public R<List<QrAssetVo>> enabled(
        @RequestParam(required = false) String platform,
        @RequestParam(required = false) Long merchantId
    ) {
        return R.ok(service.enabledAssets(platform, merchantId));
    }

    @SaCheckPermission("payment:qrcode:list")
    @GetMapping("/{id}")
    public R<QrAssetVo> get(@NotNull @PathVariable Long id) {
        return R.ok(service.queryById(id));
    }

    @SaCheckPermission("payment:qrcode:add")
    @Log(title = "新增收款二维码", businessType = BusinessType.INSERT)
    @PostMapping
    public R<QrAssetVo> create(@Valid @RequestBody QrAssetSaveRequest request) {
        return R.ok(service.create(request));
    }

    @SaCheckPermission("payment:qrcode:edit")
    @Log(title = "修改收款二维码", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public R<QrAssetVo> update(
        @NotNull @PathVariable Long id,
        @Valid @RequestBody QrAssetSaveRequest request
    ) {
        return R.ok(service.update(id, request));
    }

    @SaCheckPermission("payment:qrcode:edit")
    @Log(title = "批量修改收款二维码状态", businessType = BusinessType.UPDATE)
    @PutMapping("/batch-status")
    public R<Void> batchStatus(@Valid @RequestBody BatchStatusRequest request) {
        service.updateStatus(request.getIds(), request.getStatus());
        return R.ok();
    }

    @SaCheckPermission("payment:qrcode:edit")
    @Log(title = "删除未使用收款二维码", businessType = BusinessType.DELETE)
    @DeleteMapping
    public R<Void> delete(@Valid @RequestBody BatchIdsRequest request) {
        service.deleteUnused(request.getIds());
        return R.ok();
    }
}
