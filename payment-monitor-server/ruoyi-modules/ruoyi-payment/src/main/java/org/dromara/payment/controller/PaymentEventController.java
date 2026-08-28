package org.dromara.payment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelBuilder;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.web.core.BaseController;
import org.dromara.payment.domain.bo.PaymentEventQueryBo;
import org.dromara.payment.domain.dto.BatchEventReviewRequest;
import org.dromara.payment.domain.dto.PaymentEventReviewRequest;
import org.dromara.payment.domain.dto.DuplicateReviewRequest;
import org.dromara.payment.domain.vo.PaymentDashboardVo;
import org.dromara.payment.domain.vo.PaymentEventRawVo;
import org.dromara.payment.domain.vo.PaymentEventVo;
import org.dromara.payment.service.PaymentEventService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 支付事件管理。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/payment")
public class PaymentEventController extends BaseController {

    private final PaymentEventService eventService;

    @SaCheckPermission("payment:event:list")
    @GetMapping("/events/list")
    public R<PageResult<PaymentEventVo>> list(PaymentEventQueryBo bo, PageQuery pageQuery) {
        return R.ok(eventService.queryPage(bo, pageQuery));
    }

    @SaCheckPermission("payment:event:query")
    @GetMapping("/events/{id}")
    public R<PaymentEventVo> get(@NotNull @PathVariable Long id) {
        return R.ok(eventService.queryById(id));
    }

    @SaCheckPermission("payment:event:review")
    @Log(title = "审核支付事件", businessType = BusinessType.UPDATE)
    @PutMapping("/events/{id}/review")
    public R<PaymentEventVo> review(
        @NotNull @PathVariable Long id,
        @Valid @RequestBody PaymentEventReviewRequest request
    ) {
        return R.ok(eventService.review(id, request));
    }

    @SaCheckPermission("payment:event:review")
    @Log(title = "批量审核支付事件", businessType = BusinessType.UPDATE)
    @PutMapping("/events/batch-review")
    public R<Void> batchReview(@Valid @RequestBody BatchEventReviewRequest request) {
        eventService.review(request.getIds(), request.getAction(), request.getNote());
        return R.ok();
    }

    @SaCheckPermission("payment:event:duplicate")
    @Log(title = "审核疑似重复支付事件", businessType = BusinessType.UPDATE)
    @PutMapping("/events/{id}/duplicate-review")
    public R<PaymentEventVo> reviewDuplicate(
        @NotNull @PathVariable Long id,
        @Valid @RequestBody DuplicateReviewRequest request
    ) {
        return R.ok(eventService.reviewDuplicate(id, request));
    }

    @SaCheckPermission("payment:event:raw")
    @GetMapping("/events/{id}/raw")
    public R<PaymentEventRawVo> raw(@NotNull @PathVariable Long id) {
        return R.ok(eventService.queryRaw(id, true));
    }

    @SaCheckPermission("payment:event:raw:full")
    @GetMapping("/events/{id}/raw/full")
    public R<PaymentEventRawVo> fullRaw(@NotNull @PathVariable Long id) {
        return R.ok(eventService.queryRaw(id, false));
    }

    @SaCheckPermission("payment:event:export")
    @Log(title = "导出支付事件", businessType = BusinessType.EXPORT)
    @PostMapping("/events/export")
    public void export(PaymentEventQueryBo bo, HttpServletResponse response) {
        ExcelBuilder.of(eventService.queryExportList(bo), org.dromara.payment.domain.vo.PaymentEventExportVo.class)
            .sheetName("支付事件")
            .toResponse(response);
    }

    @SaCheckPermission("payment:dashboard:view")
    @GetMapping("/dashboard")
    public R<PaymentDashboardVo> dashboard() {
        return R.ok(eventService.dashboard());
    }
}
