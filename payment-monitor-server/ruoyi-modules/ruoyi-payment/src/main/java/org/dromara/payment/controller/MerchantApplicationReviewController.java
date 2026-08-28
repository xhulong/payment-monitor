package org.dromara.payment.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.payment.domain.PmMerchantApplication;
import org.dromara.payment.domain.dto.MerchantApplicationReviewRequest;
import org.dromara.payment.domain.dto.MerchantApplicationReviewSettingsUpdateRequest;
import org.dromara.payment.domain.vo.MerchantApplicationReviewSettingsVo;
import org.dromara.payment.service.AccountMfaService;
import org.dromara.payment.service.MerchantOnboardingReviewSettingsService;
import org.dromara.payment.service.MerchantOnboardingService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@SaCheckLogin
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/payment/platform/merchant-applications")
public class MerchantApplicationReviewController {
    private final MerchantOnboardingService service;
    private final AccountMfaService mfaService;
    private final MerchantOnboardingReviewSettingsService reviewSettingsService;

    @GetMapping("/review-settings")
    @SaCheckPermission("payment:merchant-application:list")
    public R<MerchantApplicationReviewSettingsVo> reviewSettings() {
        return R.ok(reviewSettingsService.view());
    }

    @PutMapping("/review-settings")
    @SaCheckPermission("payment:merchant-application:settings")
    @Log(title = "修改商户入驻审核设置", businessType = BusinessType.UPDATE)
    public R<MerchantApplicationReviewSettingsVo> updateReviewSettings(
        @Valid @RequestBody MerchantApplicationReviewSettingsUpdateRequest request,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        mfaService.requireStepUp(
            stepUpToken,
            "MERCHANT_APPLICATION_REVIEW_SETTINGS"
        );
        return R.ok(reviewSettingsService.update(request.getReviewEnabled()));
    }

    @GetMapping("/list")
    @SaCheckPermission("payment:merchant-application:list")
    public R<PageResult<PmMerchantApplication>> list(
        @RequestParam(required = false) String status,
        PageQuery pageQuery
    ) {
        return R.ok(service.reviewPage(status, pageQuery));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("payment:merchant-application:list")
    public R<PmMerchantApplication> detail(@NotNull @PathVariable Long id) {
        return R.ok(service.reviewDetail(id));
    }

    @PutMapping("/{id}/claim")
    @SaCheckPermission("payment:merchant-application:review")
    public R<PmMerchantApplication> claim(@NotNull @PathVariable Long id) {
        return R.ok(service.claim(id));
    }

    @PutMapping("/{id}/approve")
    @SaCheckPermission("payment:merchant-application:review")
    public R<PmMerchantApplication> approve(
        @NotNull @PathVariable Long id,
        @Valid @RequestBody MerchantApplicationReviewRequest request,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        mfaService.requireStepUp(stepUpToken, "MERCHANT_APPLICATION_REVIEW");
        return R.ok(service.approve(id, request.getNote()));
    }

    @PutMapping("/{id}/request-changes")
    @SaCheckPermission("payment:merchant-application:review")
    public R<PmMerchantApplication> requestChanges(
        @NotNull @PathVariable Long id,
        @Valid @RequestBody MerchantApplicationReviewRequest request,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        mfaService.requireStepUp(stepUpToken, "MERCHANT_APPLICATION_REVIEW");
        return R.ok(service.requestChanges(id, request.getNote()));
    }

    @PutMapping("/{id}/reject")
    @SaCheckPermission("payment:merchant-application:review")
    public R<PmMerchantApplication> reject(
        @NotNull @PathVariable Long id,
        @Valid @RequestBody MerchantApplicationReviewRequest request,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        mfaService.requireStepUp(stepUpToken, "MERCHANT_APPLICATION_REVIEW");
        return R.ok(service.reject(id, request.getNote()));
    }
}
