package org.dromara.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.context.MerchantAccessService;
import org.dromara.payment.domain.*;
import org.dromara.payment.domain.dto.MerchantApplicationSaveRequest;
import org.dromara.payment.domain.vo.MerchantOnboardingStatusVo;
import org.dromara.payment.mapper.*;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.service.ISysUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MerchantOnboardingService {
    private static final Set<String> EDITABLE = Set.of("DRAFT", "NEEDS_CHANGES");
    private static final String AUTO_APPROVAL_NOTE =
        "人工审核已关闭，系统自动通过";

    private final MerchantApplicationMapper applicationMapper;
    private final MerchantApplicationHistoryMapper historyMapper;
    private final MerchantMapper merchantMapper;
    private final MerchantUserMapper merchantUserMapper;
    private final AccountMfaMapper mfaMapper;
    private final QrAssetMapper qrAssetMapper;
    private final PaymentDeviceMapper deviceMapper;
    private final PaymentEventMapper eventMapper;
    private final MerchantAccessService merchantAccessService;
    private final ISysUserService userService;
    private final PaymentProperties properties;
    private final MailNotificationPublisher mailNotificationPublisher;
    private final MerchantOnboardingReviewSettingsService reviewSettingsService;

    public MerchantOnboardingStatusVo status() {
        Long userId = currentUserId();
        boolean reviewEnabled = reviewSettingsService.reviewEnabled();
        if (isPlatformAccount()) {
            SysUserVo platformUser = userService.selectUserById(userId);
            return new MerchantOnboardingStatusVo(
                false,
                reviewEnabled,
                platformUser == null ? null : normalizeEmail(platformUser.getEmail()),
                null,
                null,
                null,
                null,
                null,
                null,
                mfaEnabled(userId),
                List.of());
        }
        SysUserVo user = requireUser(userId);
        PmMerchantApplication application = latestForUser(userId);
        PmMerchantUser binding = merchantUserMapper.selectOne(new LambdaQueryWrapper<PmMerchantUser>()
            .eq(PmMerchantUser::getUserId, userId)
            .last("limit 1"));
        if (binding == null) {
            return new MerchantOnboardingStatusVo(
                true,
                reviewEnabled,
                normalizeEmail(user.getEmail()), application, null, null, null, null, null,
                mfaEnabled(userId), List.of());
        }
        PmMerchant merchant = merchantMapper.selectById(binding.getMerchantId());
        List<MerchantOnboardingStatusVo.ChecklistItem> checklist = checklist(merchant, userId);
        activateWhenReady(merchant, checklist);
        return new MerchantOnboardingStatusVo(
            true,
            reviewEnabled,
            normalizeEmail(user.getEmail()),
            application,
            merchant.getId(),
            merchant.getMerchantCode(),
            merchant.getName(),
            merchant.getLifecycleStatus(),
            binding.getRoleCode(),
            mfaEnabled(userId),
            checklist);
    }

    /**
     * 重新评估商户开通清单，并在全部必需项完成后自动激活商户。
     * 订单创建等入口会调用此方法，避免最后一个开通步骤完成后仍要求用户
     * 先手动访问开通向导才能把生命周期从 ONBOARDING 更新为 ACTIVE。
     *
     * @param merchantId 商户编号
     * @return 最新商户状态，商户不存在时返回 {@code null}
     */
    @Transactional(rollbackFor = Exception.class)
    public PmMerchant refreshActivation(Long merchantId) {
        PmMerchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null
            || !PaymentConstants.MERCHANT_ONBOARDING.equals(merchant.getLifecycleStatus())
            || merchant.getOwnerUserId() == null) {
            return merchant;
        }
        activateWhenReady(merchant, checklist(merchant, merchant.getOwnerUserId()));
        return merchant;
    }

    @Transactional(rollbackFor = Exception.class)
    public PmMerchantApplication create(MerchantApplicationSaveRequest request) {
        requireOnboardingAvailable();
        Long userId = currentUserId();
        PmMerchantApplication active = applicationMapper.selectOne(
            new LambdaQueryWrapper<PmMerchantApplication>()
                .eq(PmMerchantApplication::getUserId, userId)
                .in(PmMerchantApplication::getStatus,
                    "DRAFT", "SUBMITTED", "UNDER_REVIEW", "NEEDS_CHANGES")
                .last("limit 1"));
        if (active != null) {
            throw new ServiceException("当前账号已有进行中的商户申请");
        }
        PmMerchantApplication latest = latestForUser(userId);
        if (latest != null && "REJECTED".equals(latest.getStatus())
            && latest.getCooldownUntil() != null && latest.getCooldownUntil().isAfter(now())) {
            throw new ServiceException("申请被拒绝后需等待 7 天才能重新申请");
        }
        SysUserVo user = requireUser(userId);
        PmMerchantApplication application = new PmMerchantApplication();
        application.setId(IdWorker.getId());
        application.setUserId(userId);
        application.setVerifiedEmail(normalizeEmail(user.getEmail()));
        copy(request, application);
        application.setStatus("DRAFT");
        application.setVersion(0);
        application.setCreatedAt(now());
        application.setUpdatedAt(application.getCreatedAt());
        applicationMapper.insert(application);
        history(application, "CREATE", null, "DRAFT", null, JsonUtils.toJsonString(request));
        return application;
    }

    @Transactional(rollbackFor = Exception.class)
    public PmMerchantApplication update(Long id, MerchantApplicationSaveRequest request) {
        requireOnboardingAvailable();
        PmMerchantApplication application = requireOwnedForUpdate(id);
        if (!EDITABLE.contains(application.getStatus())) {
            throw new ServiceException("当前申请状态不允许修改");
        }
        copy(request, application);
        application.setUpdatedAt(now());
        application.setVersion(nextVersion(application.getVersion()));
        applicationMapper.updateById(application);
        history(application, "UPDATE", application.getStatus(), application.getStatus(), null,
            JsonUtils.toJsonString(request));
        return application;
    }

    @Transactional(rollbackFor = Exception.class)
    public PmMerchantApplication submit(Long id) {
        requireOnboardingAvailable();
        PmMerchantApplication application = requireOwnedForUpdate(id);
        if (!EDITABLE.contains(application.getStatus())) {
            if (Set.of("SUBMITTED", "UNDER_REVIEW").contains(application.getStatus())) {
                return application;
            }
            throw new ServiceException("当前申请状态不允许提交");
        }
        validateAgreement(application);
        boolean reviewEnabled = reviewSettingsService.reviewEnabled();
        String from = application.getStatus();
        application.setStatus("SUBMITTED");
        application.setSubmissionSnapshot(JsonUtils.toJsonString(application));
        application.setSubmittedAt(now());
        application.setReviewerId(null);
        application.setClaimedAt(null);
        application.setReviewNote(null);
        application.setUpdatedAt(now());
        application.setVersion(nextVersion(application.getVersion()));
        applicationMapper.updateById(application);
        history(application, "SUBMIT", from, "SUBMITTED", null, application.getSubmissionSnapshot());
        if (reviewEnabled) {
            mailNotificationPublisher.applicationSubmitted(application);
            return application;
        }
        return approveApplication(
            application,
            "AUTO_APPROVE",
            AUTO_APPROVAL_NOTE,
            null
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public PmMerchantApplication withdraw(Long id) {
        requireOnboardingAvailable();
        PmMerchantApplication application = requireOwnedForUpdate(id);
        if (!Set.of("DRAFT", "SUBMITTED", "NEEDS_CHANGES").contains(application.getStatus())) {
            throw new ServiceException("当前申请状态不允许撤回");
        }
        transition(application, "WITHDRAW", "WITHDRAWN", null);
        return application;
    }

    public PageResult<PmMerchantApplication> reviewPage(String status, PageQuery pageQuery) {
        requireReviewer();
        LambdaQueryWrapper<PmMerchantApplication> wrapper =
            new LambdaQueryWrapper<PmMerchantApplication>()
                .eq(status != null && !status.isBlank(), PmMerchantApplication::getStatus, status)
                .orderByAsc(PmMerchantApplication::getSubmittedAt)
                .orderByAsc(PmMerchantApplication::getCreatedAt);
        Page<PmMerchantApplication> page = applicationMapper.selectPage(pageQuery.build(), wrapper);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    public PmMerchantApplication reviewDetail(Long id) {
        requireReviewer();
        PmMerchantApplication application = applicationMapper.selectById(id);
        if (application == null) {
            throw new ServiceException("商户申请不存在");
        }
        return application;
    }

    @Transactional(rollbackFor = Exception.class)
    public PmMerchantApplication claim(Long id) {
        requireReviewer();
        PmMerchantApplication application = requireForReview(id);
        if ("UNDER_REVIEW".equals(application.getStatus())) {
            if (!currentUserId().equals(application.getReviewerId())) {
                throw new ServiceException("该申请已被其他审核员认领");
            }
            return application;
        }
        if (!"SUBMITTED".equals(application.getStatus())) {
            throw new ServiceException("只有已提交申请可以认领");
        }
        application.setReviewerId(currentUserId());
        application.setClaimedAt(now());
        transition(application, "CLAIM", "UNDER_REVIEW", null);
        return application;
    }

    @Transactional(rollbackFor = Exception.class)
    public PmMerchantApplication requestChanges(Long id, String note) {
        requireReviewer();
        PmMerchantApplication application = requireClaimed(id);
        transition(application, "REQUEST_CHANGES", "NEEDS_CHANGES", requiredNote(note));
        mailNotificationPublisher.applicationReviewed(application);
        return application;
    }

    @Transactional(rollbackFor = Exception.class)
    public PmMerchantApplication reject(Long id, String note) {
        requireReviewer();
        PmMerchantApplication application = requireClaimed(id);
        application.setCooldownUntil(now().plusDays(properties.getOnboarding().getRejectionCooldownDays()));
        transition(application, "REJECT", "REJECTED", requiredNote(note));
        mailNotificationPublisher.applicationReviewed(application);
        return application;
    }

    @Transactional(rollbackFor = Exception.class)
    public PmMerchantApplication approve(Long id, String note) {
        requireReviewer();
        PmMerchantApplication application = requireClaimed(id);
        return approveApplication(application, "APPROVE", note, currentUserId());
    }

    private PmMerchantApplication approveApplication(
        PmMerchantApplication application,
        String action,
        String note,
        Long operatedBy
    ) {
        if ("APPROVED".equals(application.getStatus())) {
            return application;
        }
        if (application.getMerchantId() != null) {
            throw new ServiceException("该申请已创建商户");
        }
        OffsetDateTime timestamp = now();
        PmMerchant merchant = new PmMerchant();
        merchant.setId(IdWorker.getId());
        merchant.setMerchantCode(generateMerchantCode());
        merchant.setName(application.getMerchantDisplayName());
        merchant.setStatus(PaymentConstants.DEVICE_STATUS_ENABLED);
        merchant.setLifecycleStatus(PaymentConstants.MERCHANT_ONBOARDING);
        merchant.setOwnerUserId(application.getUserId());
        merchant.setTimezone("Asia/Shanghai");
        merchant.setAgreementVersion(application.getAgreementVersion());
        merchant.setPrivacyVersion(application.getPrivacyVersion());
        merchant.setCreatedBy(operatedBy);
        merchant.setCreatedAt(timestamp);
        merchant.setUpdatedAt(timestamp);
        merchantMapper.insert(merchant);

        PmMerchantUser binding = new PmMerchantUser();
        binding.setId(IdWorker.getId());
        binding.setMerchantId(merchant.getId());
        binding.setUserId(application.getUserId());
        binding.setRoleCode(PaymentConstants.MEMBER_OWNER);
        binding.setStatus("0");
        binding.setCreatedBy(operatedBy);
        binding.setCreatedAt(timestamp);
        binding.setUpdatedAt(timestamp);
        merchantUserMapper.insert(binding);
        merchantUserMapper.revokePaymentRoles(application.getUserId());
        merchantUserMapper.grantRole(application.getUserId(), PaymentConstants.MERCHANT_OWNER_ROLE_ID);

        application.setMerchantId(merchant.getId());
        transition(application, action, "APPROVED", note, operatedBy);
        mailNotificationPublisher.applicationReviewed(application);
        return application;
    }

    private List<MerchantOnboardingStatusVo.ChecklistItem> checklist(PmMerchant merchant, Long userId) {
        boolean mfa = mfaEnabled(userId);
        boolean agreements = merchant.getAgreementVersion() != null && merchant.getPrivacyVersion() != null;
        boolean qr = qrAssetMapper.selectCount(new LambdaQueryWrapper<PmQrAsset>()
            .eq(PmQrAsset::getMerchantId, merchant.getId())) > 0;
        boolean paired = deviceMapper.selectCount(new LambdaQueryWrapper<PmDevice>()
            .eq(PmDevice::getMerchantId, merchant.getId())) > 0;
        boolean online = deviceMapper.selectCount(new LambdaQueryWrapper<PmDevice>()
            .eq(PmDevice::getMerchantId, merchant.getId())
            .eq(PmDevice::getStatus, PaymentConstants.DEVICE_STATUS_ENABLED)
            .ge(PmDevice::getLastSeenAt,
                now().minusSeconds(properties.getHeartbeat().getOnlineThresholdSeconds()))) > 0;
        boolean testSynced = eventMapper.selectCount(new LambdaQueryWrapper<PmPaymentEvent>()
            .eq(PmPaymentEvent::getMerchantId, merchant.getId())) > 0;
        List<MerchantOnboardingStatusVo.ChecklistItem> items = new ArrayList<>();
        items.add(new MerchantOnboardingStatusVo.ChecklistItem(
            "OWNER_TOTP",
            "所有者启用多因素认证（MFA，可选）",
            mfa,
            false
        ));
        items.add(item("AGREEMENTS", "接受最新服务协议与隐私政策", agreements));
        items.add(item("QR_ASSET", "添加微信或支付宝二维码", qr));
        items.add(item("DEVICE_PAIRED", "Android 真机完成配对", paired));
        items.add(item("DEVICE_ONLINE", "设备心跳在线", online));
        items.add(item("TEST_NOTIFICATION", "测试通知同步成功", testSynced));
        return items;
    }

    private void activateWhenReady(
        PmMerchant merchant,
        List<MerchantOnboardingStatusVo.ChecklistItem> checklist
    ) {
        if (!PaymentConstants.MERCHANT_ONBOARDING.equals(merchant.getLifecycleStatus())
            || checklist.stream().anyMatch(item -> item.required() && !item.completed())) {
            return;
        }
        merchant.setLifecycleStatus(PaymentConstants.MERCHANT_ACTIVE);
        merchant.setOnboardingCompletedAt(now());
        merchant.setUpdatedAt(now());
        merchantMapper.updateById(merchant);
    }

    private MerchantOnboardingStatusVo.ChecklistItem item(String code, String label, boolean completed) {
        return new MerchantOnboardingStatusVo.ChecklistItem(code, label, completed, true);
    }

    private PmMerchantApplication requireOwnedForUpdate(Long id) {
        PmMerchantApplication application = applicationMapper.selectByIdForUpdate(id);
        if (application == null || !currentUserId().equals(application.getUserId())) {
            throw new ServiceException("商户申请不存在");
        }
        return application;
    }

    private PmMerchantApplication requireForReview(Long id) {
        PmMerchantApplication application = applicationMapper.selectByIdForUpdate(id);
        if (application == null) {
            throw new ServiceException("商户申请不存在");
        }
        return application;
    }

    private PmMerchantApplication requireClaimed(Long id) {
        PmMerchantApplication application = requireForReview(id);
        if (!"UNDER_REVIEW".equals(application.getStatus())) {
            throw new ServiceException("申请尚未进入审核状态");
        }
        if (application.getReviewerId() != null
            && !application.getReviewerId().equals(currentUserId())
            && !LoginHelper.isSuperAdmin()) {
            throw new ServiceException("该申请由其他审核员负责");
        }
        return application;
    }

    private void transition(
        PmMerchantApplication application,
        String action,
        String target,
        String note
    ) {
        transition(application, action, target, note, currentUserId());
    }

    private void transition(
        PmMerchantApplication application,
        String action,
        String target,
        String note,
        Long operatedBy
    ) {
        String from = application.getStatus();
        application.setStatus(target);
        application.setReviewNote(note);
        if (Set.of("APPROVED", "REJECTED", "NEEDS_CHANGES").contains(target)) {
            application.setReviewedAt(now());
        }
        application.setUpdatedAt(now());
        application.setVersion(nextVersion(application.getVersion()));
        applicationMapper.updateById(application);
        history(
            application,
            action,
            from,
            target,
            note,
            application.getSubmissionSnapshot(),
            operatedBy
        );
    }

    private void history(
        PmMerchantApplication application,
        String action,
        String from,
        String to,
        String note,
        String snapshot
    ) {
        history(application, action, from, to, note, snapshot, currentUserId());
    }

    private void history(
        PmMerchantApplication application,
        String action,
        String from,
        String to,
        String note,
        String snapshot,
        Long operatedBy
    ) {
        PmMerchantApplicationHistory history = new PmMerchantApplicationHistory();
        history.setId(IdWorker.getId());
        history.setApplicationId(application.getId());
        history.setUserId(application.getUserId());
        history.setAction(action);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setSnapshot(snapshot);
        history.setNote(note);
        history.setOperatedBy(operatedBy);
        history.setOperatedAt(now());
        historyMapper.insert(history);
    }

    private void copy(MerchantApplicationSaveRequest request, PmMerchantApplication target) {
        target.setMerchantDisplayName(request.getMerchantDisplayName().trim());
        target.setApplicantName(request.getApplicantName().trim());
        target.setPhoneNumber(trimToNull(request.getPhoneNumber()));
        target.setCountryRegion(request.getCountryRegion().trim());
        target.setProvince(trimToNull(request.getProvince()));
        target.setCity(trimToNull(request.getCity()));
        target.setPaymentUseCase(request.getPaymentUseCase().trim());
        target.setMonthlyOrderRange(request.getMonthlyOrderRange().trim());
        target.setMonthlyAmountRange(request.getMonthlyAmountRange().trim());
        target.setPlannedPlatforms(request.getPlannedPlatforms().trim());
        target.setAgreementVersion(request.getAgreementVersion().trim());
        target.setPrivacyVersion(request.getPrivacyVersion().trim());
    }

    private void validateAgreement(PmMerchantApplication application) {
        if (!properties.getOnboarding().getAgreementVersion().equals(application.getAgreementVersion())
            || !properties.getOnboarding().getPrivacyVersion().equals(application.getPrivacyVersion())) {
            throw new ServiceException("请接受最新服务协议和隐私政策");
        }
    }

    private boolean mfaEnabled(Long userId) {
        PmAccountMfa mfa = mfaMapper.selectOne(new LambdaQueryWrapper<PmAccountMfa>()
            .eq(PmAccountMfa::getUserId, userId)
            .last("limit 1"));
        return mfa != null && Boolean.TRUE.equals(mfa.getEnabled());
    }

    private PmMerchantApplication latestForUser(Long userId) {
        return applicationMapper.selectOne(new LambdaQueryWrapper<PmMerchantApplication>()
            .eq(PmMerchantApplication::getUserId, userId)
            .orderByDesc(PmMerchantApplication::getCreatedAt)
            .last("limit 1"));
    }

    private SysUserVo requireUser(Long userId) {
        SysUserVo user = userService.selectUserById(userId);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            throw new ServiceException("当前账号未绑定已验证邮箱");
        }
        return user;
    }

    private void requireReviewer() {
        if (LoginHelper.isSuperAdmin()) {
            return;
        }
        var loginUser = LoginHelper.getLoginUser();
        if (loginUser == null || loginUser.getRolePermission() == null
            || !loginUser.getRolePermission().contains("payment_platform_reviewer")) {
            throw new ServiceException("当前账号不是平台审核员");
        }
    }

    private void requireOnboardingAvailable() {
        if (isPlatformAccount()) {
            throw new ServiceException("平台账号无需申请商户，不能操作商户入驻申请");
        }
    }

    private boolean isPlatformAccount() {
        return merchantAccessService.isCurrentAccountPlatformAccount();
    }

    private String generateMerchantCode() {
        for (int i = 0; i < 10; i++) {
            String code = "PM" + now().toEpochSecond() + String.format("%02d", i);
            if (merchantMapper.selectCount(new LambdaQueryWrapper<PmMerchant>()
                .eq(PmMerchant::getMerchantCode, code)) == 0) {
                return code;
            }
        }
        throw new ServiceException("生成商户编码失败");
    }

    private String requiredNote(String note) {
        if (note == null || note.isBlank()) {
            throw new ServiceException("审核意见不能为空");
        }
        return note.trim();
    }

    private int nextVersion(Integer version) {
        return version == null ? 1 : version + 1;
    }

    private Long currentUserId() {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("登录状态无效");
        }
        return userId;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
