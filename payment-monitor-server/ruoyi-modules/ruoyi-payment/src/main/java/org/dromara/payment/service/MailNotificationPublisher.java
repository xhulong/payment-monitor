package org.dromara.payment.service;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.utils.regex.RegexValidator;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.domain.PmMerchantApplication;
import org.dromara.system.domain.SysUser;
import org.dromara.system.mapper.SysUserMapper;
import org.dromara.system.mapper.SysUserRoleMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailNotificationPublisher {
    private final ApplicationEventPublisher eventPublisher;
    private final MailTemplateService templates;
    private final PaymentProperties properties;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;

    public void accountRegistered(String email, String username, Long userId) {
        publish(
            "ACCOUNT_REGISTERED",
            email,
            "[LuLuPay] 账号注册成功",
            templates.noticeWithAction(
                "账号注册成功",
                "您的 LuLuPay 账号 " + username + " 已创建成功，请登录后继续完成商户入驻。",
                "登录 LuLuPay",
                url("/login")
            ),
            "ACCOUNT_REGISTERED:" + userId
        );
    }

    public void passwordReset(String email, Long userId) {
        publish(
            "PASSWORD_RESET_SUCCESS",
            email,
            "[LuLuPay] 登录密码已重置",
            templates.notice(
                "登录密码已重置",
                "您的登录密码已通过邮箱验证流程重置，其他登录会话已经撤销。"
            ),
            "PASSWORD_RESET_SUCCESS:" + userId + ":" + System.currentTimeMillis()
        );
    }

    public void passwordChanged(String email, Long userId) {
        publish(
            "PASSWORD_CHANGED_NOTICE",
            email,
            "[LuLuPay] 登录密码已修改",
            templates.notice(
                "登录密码已修改",
                "您的登录密码已修改，其他登录会话已经撤销。如非本人操作，请立即联系平台管理员。"
            ),
            "PASSWORD_CHANGED_NOTICE:" + userId + ":" + System.currentTimeMillis()
        );
    }

    public void mfaChanged(String email, Long userId, boolean replaced) {
        String type = replaced ? "MFA_REPLACED_NOTICE" : "MFA_ENABLED_NOTICE";
        String title = replaced ? "MFA 已重新配置" : "MFA 已启用";
        publish(
            type,
            email,
            "[LuLuPay] " + title,
            templates.notice(
                title,
                replaced
                    ? "您的身份验证器配置已更新，旧配置已经失效。"
                    : "您的账号已经启用多因素认证（MFA）。"
            ),
            type + ":" + userId + ":" + System.currentTimeMillis()
        );
    }

    public void mfaDisabled(String email, Long userId) {
        publish(
            "MFA_DISABLED_NOTICE",
            email,
            "[LuLuPay] MFA 已关闭",
            templates.notice(
                "MFA 已关闭",
                "您的账号已经关闭多因素认证（MFA），后续登录和敏感操作将仅使用当前登录会话及权限校验。"
            ),
            "MFA_DISABLED_NOTICE:" + userId + ":" + System.currentTimeMillis()
        );
    }

    public void recoveryCodesRegenerated(String email, Long userId) {
        publish(
            "MFA_RECOVERY_CODES_REGENERATED_NOTICE",
            email,
            "[LuLuPay] MFA 恢复码已重新生成",
            templates.notice(
                "MFA 恢复码已重新生成",
                "您的 MFA 恢复码已经重新生成，旧恢复码已全部失效。邮件中不会包含恢复码内容。"
            ),
            "MFA_RECOVERY_CODES_REGENERATED:" + userId + ":" + System.currentTimeMillis()
        );
    }

    public void emailChanged(String oldEmail, String newEmail, Long userId) {
        publish(
            "EMAIL_CHANGED_NOTICE",
            oldEmail,
            "[LuLuPay] 登录邮箱已修改",
            templates.notice(
                "登录邮箱已修改",
                "您的登录邮箱已修改为 " + newEmail + "。如非本人操作，请立即联系平台管理员。"
            ),
            "EMAIL_CHANGED_OLD:" + userId + ":" + newEmail
        );
        publish(
            "EMAIL_CHANGED_CONFIRMATION",
            newEmail,
            "[LuLuPay] 新登录邮箱已生效",
            templates.notice(
                "新登录邮箱已生效",
                "当前邮箱已经成为您的 LuLuPay 登录邮箱，其他登录会话已经撤销。"
            ),
            "EMAIL_CHANGED_NEW:" + userId + ":" + newEmail
        );
    }

    public void applicationSubmitted(PmMerchantApplication application) {
        try {
            publish(
                "MERCHANT_APPLICATION_SUBMITTED",
                application.getVerifiedEmail(),
                "[LuLuPay] 商户申请已提交",
                templates.noticeWithAction(
                    "商户申请已提交",
                    "我们已收到“" + application.getMerchantDisplayName()
                        + "”的商户申请，审核结果会继续发送到此邮箱。",
                    "查看入驻进度",
                    url("/payment/merchant-center/onboarding")
                ),
                "MERCHANT_APPLICATION_SUBMITTED:" + application.getId()
            );
        } catch (RuntimeException exception) {
            log.error(
                "Unable to publish merchant application receipt, applicationId={}",
                application.getId(),
                exception
            );
        }
        try {
            publishApplicationReviewNotices(application);
        } catch (RuntimeException exception) {
            log.error(
                "Unable to publish merchant application review notices, applicationId={}",
                application.getId(),
                exception
            );
        }
    }

    public void applicationReviewed(PmMerchantApplication application) {
        String status = application.getStatus();
        String type;
        String title;
        String message;
        if ("NEEDS_CHANGES".equals(status)) {
            type = "MERCHANT_APPLICATION_NEEDS_CHANGES";
            title = "商户申请需要补充资料";
            message = "审核意见：" + safeNote(application.getReviewNote());
        } else if ("APPROVED".equals(status)) {
            type = "MERCHANT_APPLICATION_APPROVED";
            title = "商户申请审核通过";
            message = "“" + application.getMerchantDisplayName()
                + "”已通过审核，请登录后完成二维码和设备配置；MFA 可按需启用。";
        } else if ("REJECTED".equals(status)) {
            type = "MERCHANT_APPLICATION_REJECTED";
            title = "商户申请未通过审核";
            String cooldown = application.getCooldownUntil() == null
                ? ""
                : " 可重新申请时间："
                    + application.getCooldownUntil().format(
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME
                    ) + "。";
            message = "审核意见：" + safeNote(application.getReviewNote()) + cooldown;
        } else {
            return;
        }
        publish(
            type,
            application.getVerifiedEmail(),
            "[LuLuPay] " + title,
            templates.noticeWithAction(
                title,
                message,
                "查看入驻进度",
                url("/payment/merchant-center/onboarding")
            ),
            type + ":" + application.getId() + ":" + application.getVersion()
        );
    }

    private void publish(
        String type,
        String recipient,
        String subject,
        String html,
        String deduplicationKey
    ) {
        if (recipient == null || recipient.isBlank()) {
            return;
        }
        eventPublisher.publishEvent(
            new MailNotificationEvent(
                type,
                recipient,
                subject,
                html,
                deduplicationKey,
                null
            )
        );
    }

    private void publishApplicationReviewNotices(PmMerchantApplication application) {
        Set<Long> recipientUserIds = new LinkedHashSet<>();
        recipientUserIds.add(SystemConstants.SUPER_ADMIN_USER_ID);
        List<Long> reviewerIds =
            userRoleMapper.selectUserIdsByRoleId(PaymentConstants.PLATFORM_REVIEWER_ROLE_ID);
        if (reviewerIds != null) {
            recipientUserIds.addAll(reviewerIds);
        }
        if (recipientUserIds.isEmpty()) {
            return;
        }
        List<SysUser> users = userMapper.selectByIds(recipientUserIds);
        if (users == null || users.isEmpty()) {
            return;
        }
        Map<String, SysUser> recipientsByEmail = new LinkedHashMap<>();
        for (SysUser user : users) {
            String email = normalizeEmail(user.getEmail());
            if (!recipientUserIds.contains(user.getUserId())
                || !SystemConstants.NORMAL.equals(user.getStatus())
                || !SystemConstants.NORMAL.equals(user.getDelFlag())
                || email == null
                || !RegexValidator.isEmail(email)) {
                continue;
            }
            recipientsByEmail.putIfAbsent(email, user);
        }
        String message = "收到新的商户入驻申请。商户名称："
            + application.getMerchantDisplayName()
            + "；申请人：" + application.getApplicantName()
            + "；申请邮箱：" + application.getVerifiedEmail()
            + "。请进入审核工作台处理。";
        for (Map.Entry<String, SysUser> recipient : recipientsByEmail.entrySet()) {
            publish(
                "MERCHANT_APPLICATION_REVIEW_NOTICE",
                recipient.getKey(),
                "[LuLuPay] 新商户申请待审核",
                templates.noticeWithAction(
                    "新商户申请待审核",
                    message,
                    "进入审核工作台",
                    url("/payment/platform-developer/merchant-application")
                ),
                "MERCHANT_APPLICATION_REVIEW_NOTICE:"
                    + application.getId() + ":"
                    + application.getVersion() + ":"
                    + recipient.getValue().getUserId()
            );
        }
    }

    private String url(String path) {
        String base = properties.getPublicBaseUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    private String safeNote(String note) {
        return note == null || note.isBlank() ? "请登录平台查看详情。" : note.trim();
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank()
            ? null
            : email.trim().toLowerCase(Locale.ROOT);
    }
}
