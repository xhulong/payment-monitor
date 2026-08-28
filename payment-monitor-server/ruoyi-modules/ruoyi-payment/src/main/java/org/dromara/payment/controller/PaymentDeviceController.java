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
import org.dromara.common.web.core.BaseController;
import org.dromara.payment.domain.bo.PaymentDeviceQueryBo;
import org.dromara.payment.domain.dto.BatchStatusRequest;
import org.dromara.payment.domain.dto.DeviceStatusRequest;
import org.dromara.payment.domain.dto.DeviceAssignmentSaveRequest;
import org.dromara.payment.domain.vo.DeviceAssignmentVo;
import org.dromara.payment.domain.vo.PairingCodeVo;
import org.dromara.payment.domain.vo.PairingStatusVo;
import org.dromara.payment.domain.vo.PaymentDeviceVo;
import org.dromara.payment.service.PaymentDeviceService;
import org.dromara.payment.service.DeviceAssignmentService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 支付监控设备管理。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/payment")
public class PaymentDeviceController extends BaseController {

    private final PaymentDeviceService deviceService;
    private final DeviceAssignmentService assignmentService;

    @SaCheckPermission("payment:device:pair")
    @Log(title = "生成设备配对码", businessType = BusinessType.INSERT)
    @PostMapping("/pairing-codes")
    public R<PairingCodeVo> createPairingCode(@RequestParam(required = false) Long merchantId) {
        return R.ok(deviceService.createPairingCode(merchantId));
    }

    @SaCheckPermission("payment:device:pair")
    @GetMapping("/pairing-codes/{pairingSessionId}/status")
    public R<PairingStatusVo> pairingStatus(@NotNull @PathVariable Long pairingSessionId) {
        return R.ok(deviceService.queryPairingStatus(pairingSessionId));
    }

    @SaCheckPermission("payment:device:list")
    @GetMapping("/devices/list")
    public R<PageResult<PaymentDeviceVo>> list(PaymentDeviceQueryBo bo, PageQuery pageQuery) {
        return R.ok(deviceService.queryPage(bo, pageQuery));
    }

    @SaCheckPermission("payment:device:list")
    @GetMapping("/devices/{id}")
    public R<PaymentDeviceVo> get(@NotNull @PathVariable Long id) {
        return R.ok(deviceService.queryById(id));
    }

    @SaCheckPermission("payment:device:edit")
    @Log(title = "修改设备状态", businessType = BusinessType.UPDATE)
    @PutMapping("/devices/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody DeviceStatusRequest request) {
        deviceService.updateStatus(id, request);
        return R.ok();
    }

    @SaCheckPermission("payment:device:edit")
    @Log(title = "批量修改设备状态", businessType = BusinessType.UPDATE)
    @PutMapping("/devices/batch-status")
    public R<Void> batchStatus(@Valid @RequestBody BatchStatusRequest request) {
        deviceService.updateStatus(request.getIds(), request.getStatus());
        return R.ok();
    }

    @SaCheckPermission("payment:device:list")
    @GetMapping("/device-assignments")
    public R<java.util.List<DeviceAssignmentVo>> assignments(
        @RequestParam(required = false) Long merchantId
    ) {
        return R.ok(assignmentService.query(merchantId));
    }

    @SaCheckPermission("payment:device:assignment")
    @Log(title = "配置支付主备设备", businessType = BusinessType.UPDATE)
    @PutMapping("/device-assignments")
    public R<java.util.List<DeviceAssignmentVo>> saveAssignments(
        @Valid @RequestBody DeviceAssignmentSaveRequest request
    ) {
        return R.ok(assignmentService.save(request));
    }
}
