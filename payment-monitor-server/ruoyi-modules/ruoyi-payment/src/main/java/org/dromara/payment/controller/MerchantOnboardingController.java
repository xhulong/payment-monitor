package org.dromara.payment.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.payment.domain.PmMerchantApplication;
import org.dromara.payment.domain.dto.MerchantApplicationSaveRequest;
import org.dromara.payment.domain.vo.MerchantOnboardingStatusVo;
import org.dromara.payment.service.MerchantOnboardingService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@SaCheckLogin
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/merchant-onboarding")
public class MerchantOnboardingController {
    private final MerchantOnboardingService service;

    @GetMapping("/status")
    public R<MerchantOnboardingStatusVo> status() {
        return R.ok(service.status());
    }

    @PostMapping("/applications")
    public R<PmMerchantApplication> create(@Valid @RequestBody MerchantApplicationSaveRequest request) {
        return R.ok(service.create(request));
    }

    @PutMapping("/applications/{id}")
    public R<PmMerchantApplication> update(
        @NotNull @PathVariable Long id,
        @Valid @RequestBody MerchantApplicationSaveRequest request
    ) {
        return R.ok(service.update(id, request));
    }

    @PostMapping("/applications/{id}/submit")
    public R<PmMerchantApplication> submit(@NotNull @PathVariable Long id) {
        return R.ok(service.submit(id));
    }

    @PostMapping("/applications/{id}/withdraw")
    public R<PmMerchantApplication> withdraw(@NotNull @PathVariable Long id) {
        return R.ok(service.withdraw(id));
    }
}
