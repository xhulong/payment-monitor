package org.dromara.payment.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.payment.domain.dto.StepUpRequest;
import org.dromara.payment.domain.dto.TotpCodeRequest;
import org.dromara.payment.domain.vo.StepUpVo;
import org.dromara.payment.domain.vo.TotpSetupVo;
import org.dromara.payment.service.AccountMfaService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@SaCheckLogin
@Validated
@RestController
@RequiredArgsConstructor
public class AccountMfaController {
    private final AccountMfaService service;

    @GetMapping("/account/mfa/status")
    public R<Boolean> status() {
        return R.ok(service.enabledForCurrentUser());
    }

    @PostMapping("/account/mfa/totp/setup")
    public R<TotpSetupVo> setup(
        @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        return R.ok(service.setup(stepUpToken));
    }

    @PostMapping("/account/mfa/totp/confirm")
    public R<List<String>> confirm(@Valid @RequestBody TotpCodeRequest request) {
        return R.ok(service.confirm(request));
    }

    @PostMapping("/account/mfa/totp/recovery-codes")
    public R<List<String>> recoveryCodes(
        @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        service.requireStepUp(stepUpToken, "MFA_RECOVERY_CODES");
        return R.ok(service.recoveryCodes());
    }

    @DeleteMapping("/account/mfa/totp")
    public R<Void> disable(
        @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        service.disable(stepUpToken);
        return R.ok();
    }

    @PostMapping("/auth/step-up/totp")
    public R<StepUpVo> stepUp(@Valid @RequestBody StepUpRequest request) {
        return R.ok(service.stepUp(request));
    }
}
