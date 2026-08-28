package org.dromara.payment.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.payment.domain.dto.AppReleaseSaveRequest;
import org.dromara.payment.domain.dto.AppReleaseUpdateRequest;
import org.dromara.payment.domain.dto.BatchIdsRequest;
import org.dromara.payment.domain.vo.AppReleaseVo;
import org.dromara.payment.service.AppReleaseService;
import org.dromara.payment.service.AccountMfaService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@SaCheckLogin
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/payment/app-releases")
public class AppReleaseController {
    private final AppReleaseService service;
    private final AccountMfaService mfaService;

    @GetMapping("/list")
    @SaCheckPermission("payment:app-release:list")
    public R<PageResult<AppReleaseVo>> list(PageQuery pageQuery) {
        return R.ok(service.list(pageQuery));
    }

    @PostMapping(consumes = "multipart/form-data")
    @SaCheckPermission("payment:app-release:edit")
    public R<AppReleaseVo> upload(
        @Valid @ModelAttribute AppReleaseSaveRequest request,
        @RequestPart("apk") MultipartFile apk,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        mfaService.requireStepUp(stepUpToken, "APP_RELEASE_WRITE");
        return R.ok(service.upload(request, apk));
    }

    @PutMapping("/{id}/publish")
    @SaCheckPermission("payment:app-release:edit")
    public R<AppReleaseVo> publish(
        @NotNull @PathVariable Long id,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        mfaService.requireStepUp(stepUpToken, "APP_RELEASE_WRITE");
        return R.ok(service.publish(id));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("payment:app-release:edit")
    public R<AppReleaseVo> update(
        @NotNull @PathVariable Long id,
        @Valid @RequestBody AppReleaseUpdateRequest request,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        mfaService.requireStepUp(stepUpToken, "APP_RELEASE_WRITE");
        return R.ok(service.update(id, request));
    }

    @DeleteMapping
    @SaCheckPermission("payment:app-release:edit")
    public R<Void> delete(
        @Valid @RequestBody BatchIdsRequest request,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        mfaService.requireStepUp(stepUpToken, "APP_RELEASE_WRITE");
        service.deleteReleases(request.getIds());
        return R.ok();
    }
}
