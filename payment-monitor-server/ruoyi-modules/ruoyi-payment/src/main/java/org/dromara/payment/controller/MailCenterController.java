package org.dromara.payment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.encrypt.annotation.ApiCryptoV2;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.payment.domain.bo.MailOutboxQueryBo;
import org.dromara.payment.domain.dto.MailSettingsUpdateRequest;
import org.dromara.payment.domain.dto.MailTestRequest;
import org.dromara.payment.domain.vo.MailOutboxVo;
import org.dromara.payment.domain.vo.MailSettingsVo;
import org.dromara.payment.service.MailOutboxAdminService;
import org.dromara.payment.service.MailSettingsService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/system")
public class MailCenterController {
    private final MailSettingsService settingsService;
    private final MailOutboxAdminService outboxService;

    @SaCheckPermission("system:mail-settings:view")
    @GetMapping("/mail-settings")
    public R<MailSettingsVo> settings() {
        return R.ok(settingsService.view());
    }

    @SaCheckPermission("system:mail-settings:edit")
    @PutMapping("/mail-settings")
    @ApiCryptoV2(request = true, response = true)
    public R<MailSettingsVo> updateSettings(
        @Valid @RequestBody MailSettingsUpdateRequest request,
        @RequestHeader(value = "X-Step-Up-Token", required = false)
        String stepUpToken
    ) {
        return R.ok(settingsService.update(request, stepUpToken));
    }

    @SaCheckPermission("system:mail-settings:test")
    @PostMapping("/mail-settings/test")
    @ApiCryptoV2(request = true, response = true)
    public R<Void> testSettings(
        @Valid @RequestBody MailTestRequest request,
        @RequestHeader(value = "X-Step-Up-Token", required = false)
        String stepUpToken
    ) {
        settingsService.sendTest(request.recipient(), stepUpToken);
        return R.ok();
    }

    @SaCheckPermission("system:mail-outbox:list")
    @GetMapping("/mail-outbox/list")
    public R<PageResult<MailOutboxVo>> outboxList(
        MailOutboxQueryBo bo,
        PageQuery pageQuery
    ) {
        return R.ok(outboxService.queryPage(bo, pageQuery));
    }

    @SaCheckPermission("system:mail-outbox:list")
    @GetMapping("/mail-outbox/{id}")
    public R<MailOutboxVo> outboxDetail(
        @NotNull @PathVariable Long id
    ) {
        return R.ok(outboxService.queryById(id));
    }

    @SaCheckPermission("system:mail-outbox:retry")
    @PutMapping("/mail-outbox/{id}/retry")
    @ApiCryptoV2(request = true, response = true)
    public R<MailOutboxVo> retryOutbox(
        @NotNull @PathVariable Long id,
        @RequestHeader(value = "X-Step-Up-Token", required = false)
        String stepUpToken
    ) {
        return R.ok(outboxService.retry(id, stepUpToken));
    }
}
