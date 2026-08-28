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
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.domain.bo.PaymentOrderQueryBo;
import org.dromara.payment.domain.dto.BatchOrderCancelRequest;
import org.dromara.payment.domain.dto.ManualOrderMatchRequest;
import org.dromara.payment.domain.dto.PaymentOrderCreateRequest;
import org.dromara.payment.domain.vo.OrderMatchCandidateVo;
import org.dromara.payment.domain.vo.PaymentOrderVo;
import org.dromara.payment.security.StepUpVerificationMethod;
import org.dromara.payment.service.AccountMfaService;
import org.dromara.payment.service.PaymentOrderService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/payment/orders")
public class PaymentOrderController {

    private final PaymentOrderService service;
    private final AccountMfaService mfaService;

    @SaCheckPermission("payment:order:list")
    @GetMapping("/list")
    public R<PageResult<PaymentOrderVo>> list(PaymentOrderQueryBo bo, PageQuery pageQuery) {
        return R.ok(service.queryPage(bo, pageQuery));
    }

    @SaCheckPermission("payment:order:list")
    @GetMapping("/{id}")
    public R<PaymentOrderVo> get(@NotNull @PathVariable Long id) {
        return R.ok(service.queryById(id));
    }

    @SaCheckPermission("payment:order:match")
    @GetMapping("/{id}/match-candidates")
    public R<List<OrderMatchCandidateVo>> matchCandidates(
        @NotNull @PathVariable Long id
    ) {
        return R.ok(service.matchCandidates(id));
    }

    @SaCheckPermission("payment:order:add")
    @Log(title = "创建支付订单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<PaymentOrderVo> create(@Valid @RequestBody PaymentOrderCreateRequest request) {
        return R.ok(service.create(request));
    }

    @SaCheckPermission("payment:order:cancel")
    @Log(title = "取消支付订单", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/cancel")
    public R<PaymentOrderVo> cancel(
        @NotNull @PathVariable Long id,
        @RequestParam(required = false) String note
    ) {
        return R.ok(service.cancel(id, note));
    }

    @SaCheckPermission("payment:order:cancel")
    @Log(title = "批量取消支付订单", businessType = BusinessType.UPDATE)
    @PutMapping("/batch-cancel")
    public R<Void> batchCancel(@Valid @RequestBody BatchOrderCancelRequest request) {
        service.cancel(request.getIds(), request.getNote());
        return R.ok();
    }

    @SaCheckPermission("payment:order:match")
    @Log(title = "人工匹配支付订单", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/match")
    public R<PaymentOrderVo> manualMatch(
        @NotNull @PathVariable Long id,
        @Valid @RequestBody ManualOrderMatchRequest request,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        StepUpVerificationMethod verificationMethod = request.isForce()
            ? mfaService.requireStepUp(stepUpToken, "PAYMENT_ORDER_FORCE_MATCH")
            : StepUpVerificationMethod.SESSION;
        return R.ok(service.manualMatch(id, request, verificationMethod));
    }
}
