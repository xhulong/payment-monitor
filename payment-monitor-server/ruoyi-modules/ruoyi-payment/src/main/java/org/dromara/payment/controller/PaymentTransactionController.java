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
import org.dromara.payment.domain.PmAmountSlotReservation;
import org.dromara.payment.domain.PmPaymentTransaction;
import org.dromara.payment.domain.bo.AmountSlotQueryBo;
import org.dromara.payment.domain.bo.PaymentTransactionQueryBo;
import org.dromara.payment.domain.dto.TransactionConfirmRequest;
import org.dromara.payment.domain.dto.TransactionReverseRequest;
import org.dromara.payment.security.StepUpVerificationMethod;
import org.dromara.payment.service.AccountMfaService;
import org.dromara.payment.service.AmountSlotService;
import org.dromara.payment.service.PaymentSensitiveOperationService;
import org.dromara.payment.service.PaymentTransactionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/payment")
public class PaymentTransactionController {
    private final PaymentTransactionService transactionService;
    private final PaymentSensitiveOperationService sensitiveOperationService;
    private final AmountSlotService amountSlotService;
    private final AccountMfaService mfaService;

    @SaCheckPermission("payment:transaction:list")
    @GetMapping("/transactions/list")
    public R<PageResult<PmPaymentTransaction>> list(
        PaymentTransactionQueryBo bo,
        PageQuery pageQuery
    ) {
        return R.ok(transactionService.queryPage(bo, pageQuery));
    }

    @SaCheckPermission("payment:transaction:list")
    @GetMapping("/transactions/{id}")
    public R<PmPaymentTransaction> get(@NotNull @PathVariable Long id) {
        return R.ok(transactionService.queryById(id));
    }

    @SaCheckPermission("payment:transaction:confirm")
    @Log(title = "人工确认支付交易", businessType = BusinessType.UPDATE)
    @PutMapping("/transactions/{id}/confirm")
    public R<PmPaymentTransaction> confirm(
        @NotNull @PathVariable Long id,
        @Valid @RequestBody TransactionConfirmRequest request
    ) {
        return R.ok(transactionService.confirm(id, request.getNote()));
    }

    @SaCheckPermission("payment:transaction:reverse")
    @Log(title = "申请撤销支付确认", businessType = BusinessType.UPDATE)
    @PutMapping("/transactions/{id}/reverse")
    public R<PmPaymentTransaction> reverse(
        @NotNull @PathVariable Long id,
        @Valid @RequestBody TransactionReverseRequest request,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        StepUpVerificationMethod verificationMethod = mfaService.requireStepUp(
            stepUpToken,
            "PAYMENT_CONFIRMATION_REVERSE"
        );
        return R.ok(sensitiveOperationService.reverseConfirmation(
            id,
            request.getReason(),
            verificationMethod));
    }

    @SaCheckPermission("payment:order:list")
    @GetMapping("/amount-slots/list")
    public R<PageResult<PmAmountSlotReservation>> amountSlots(
        AmountSlotQueryBo bo,
        PageQuery pageQuery
    ) {
        return R.ok(amountSlotService.queryPage(bo, pageQuery));
    }
}
