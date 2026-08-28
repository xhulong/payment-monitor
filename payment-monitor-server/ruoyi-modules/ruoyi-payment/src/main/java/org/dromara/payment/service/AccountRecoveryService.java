package org.dromara.payment.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.lock.annotation.Lock4j;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.constant.GlobalConstants;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.domain.dto.EmailChangeCodeRequest;
import org.dromara.payment.domain.dto.EmailChangeConfirmRequest;
import org.dromara.payment.domain.dto.MerchantEmailCodeRequest;
import org.dromara.payment.domain.dto.PasswordResetConfirmRequest;
import org.dromara.system.domain.SysUser;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.mapper.SysUserMapper;
import org.dromara.system.service.ISysUserService;
import org.dromara.system.service.RefreshSessionService;
import org.redisson.api.RateType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

import static org.dromara.payment.service.AccountRecoveryChallengeService.ChallengeType.EMAIL_CHANGE;
import static org.dromara.payment.service.AccountRecoveryChallengeService.ChallengeType.PASSWORD_RESET;

@Service
@RequiredArgsConstructor
public class AccountRecoveryService {
    private final PaymentProperties properties;
    private final MailSettingsService mailSettingsService;
    private final SysUserMapper userMapper;
    private final ISysUserService userService;
    private final AccountMfaService mfaService;
    private final RefreshSessionService refreshSessionService;
    private final AccountRecoveryChallengeService challengeService;
    private final MailOutboxService mailOutboxService;
    private final MailTemplateService mailTemplateService;
    private final MailNotificationPublisher mailNotificationPublisher;

    @Lock4j(keys = {"#request.email.trim().toLowerCase()"}, acquireTimeout = 5000)
    @Transactional(rollbackFor = Exception.class)
    public void sendPasswordResetCode(MerchantEmailCodeRequest request, String clientIp) {
        String email = normalizeEmail(request.getEmail());
        validateImageCaptcha(request.getCaptchaUuid(), request.getCaptchaCode());
        rateLimit("payment:account-reset:email:minute:" + email, 1, 60,
            "同一邮箱 60 秒内只能发送一次验证码");
        rateLimit("payment:account-reset:email:day:" + email + ":" + LocalDate.now(ZoneOffset.UTC),
            10, 86_400, "同一邮箱每天最多发送 10 次验证码");
        rateLimit("payment:account-reset:ip:hour:" + clientIp, 20, 3_600,
            "当前网络请求过于频繁");
        ensureMailEnabled();
        SysUserVo user = findByEmail(email);
        if (user == null) {
            return;
        }
        String code = RandomUtil.randomNumbers(6);
        OffsetDateTime expiresAt = recoveryCodeExpiresAt();
        var challenge = challengeService.issue(
            PASSWORD_RESET,
            user.getUserId(),
            email,
            code,
            expiresAt,
            clientIp
        );
        mailOutboxService.enqueueHtml(
            "PASSWORD_RESET_CODE",
            email,
            "[LuLuPay] 密码重置验证码",
            mailTemplateService.code(
                "重置登录密码",
                "您正在重置 LuLuPay 账号的登录密码。",
                code,
                properties.getOnboarding().getEmailCodeTtlMinutes() + " 分钟"
            ),
            "PASSWORD_RESET_CODE:" + challenge.getChallengeId(),
            expiresAt
        );
    }

    @Lock4j(keys = {"#request.email.trim().toLowerCase()"}, acquireTimeout = 5000)
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(PasswordResetConfirmRequest request) {
        String email = normalizeEmail(request.getEmail());
        SysUserVo user = findByEmail(email);
        if (user == null) {
            throw new ServiceException("验证码无效或已过期");
        }
        Long challengeId = challengeService.verify(
            PASSWORD_RESET,
            user.getUserId(),
            email,
            request.getCode()
        );
        if (BCrypt.checkpw(request.getNewPassword(), user.getPassword())) {
            throw new ServiceException("新密码不能与旧密码相同");
        }
        challengeService.consume(
            challengeId,
            PASSWORD_RESET,
            user.getUserId(),
            email,
            request.getCode()
        );
        if (userService.resetUserPwd(user.getUserId(), BCrypt.hashpw(request.getNewPassword())) != 1) {
            throw new ServiceException("密码重置失败");
        }
        refreshSessionService.revokeAll(
            user.getUserId(),
            "PASSWORD_RESET"
        );
        mfaService.revokeStepUpTokens(user.getUserId());
        StpUtil.logout(user.getUserId());
        mailNotificationPublisher.passwordReset(email, user.getUserId());
    }

    @Lock4j(keys = {"#request.newEmail.trim().toLowerCase()"}, acquireTimeout = 5000)
    @Transactional(rollbackFor = Exception.class)
    public void sendEmailChangeCode(
        EmailChangeCodeRequest request,
        String stepUpToken,
        String clientIp
    ) {
        Long userId = currentUserId();
        mfaService.requireStepUp(stepUpToken, "EMAIL_CHANGE");
        SysUserVo user = userService.selectUserById(userId);
        if (user == null || !BCrypt.checkpw(request.getPassword(), user.getPassword())) {
            throw new ServiceException("当前密码错误");
        }
        String newEmail = normalizeEmail(request.getNewEmail());
        if (findByEmail(newEmail) != null) {
            throw new ServiceException("该邮箱已被使用");
        }
        rateLimit("payment:email-change:user:minute:" + userId, 1, 60,
            "60 秒内只能发送一次验证码");
        ensureMailEnabled();
        String code = RandomUtil.randomNumbers(6);
        OffsetDateTime expiresAt = recoveryCodeExpiresAt();
        var challenge = challengeService.issue(
            EMAIL_CHANGE,
            userId,
            newEmail,
            code,
            expiresAt,
            clientIp
        );
        mailOutboxService.enqueueHtml(
            "EMAIL_CHANGE_CODE",
            newEmail,
            "[LuLuPay] 新邮箱验证码",
            mailTemplateService.code(
                "验证新的登录邮箱",
                "请使用此验证码确认新的 LuLuPay 登录邮箱。",
                code,
                properties.getOnboarding().getEmailCodeTtlMinutes() + " 分钟"
            ),
            "EMAIL_CHANGE_CODE:" + challenge.getChallengeId(),
            expiresAt
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void confirmEmailChange(EmailChangeConfirmRequest request) {
        Long userId = currentUserId();
        String newEmail = normalizeEmail(request.getNewEmail());
        Long challengeId = challengeService.verify(
            EMAIL_CHANGE,
            userId,
            newEmail,
            request.getCode()
        );
        if (findByEmail(newEmail) != null) {
            throw new ServiceException("该邮箱已被使用");
        }
        SysUserVo current = userService.selectUserById(userId);
        if (current == null) {
            throw new ServiceException("账号不存在");
        }
        challengeService.consume(
            challengeId,
            EMAIL_CHANGE,
            userId,
            newEmail,
            request.getCode()
        );
        SysUser update = new SysUser(userId);
        update.setEmail(newEmail);
        int updated = DataPermissionHelper.ignore(
            () -> userMapper.updateById(update)
        );
        if (updated != 1) {
            throw new ServiceException("邮箱修改失败");
        }
        refreshSessionService.revokeAll(userId, "EMAIL_CHANGE");
        mfaService.revokeStepUpTokens(userId);
        mailNotificationPublisher.emailChanged(
            current.getEmail(),
            newEmail,
            userId
        );
        StpUtil.logout(userId);
    }

    private void validateImageCaptcha(String uuid, String answer) {
        String key = GlobalConstants.CAPTCHA_CODE_KEY + uuid;
        String expected = RedisUtils.getCacheObject(key);
        RedisUtils.deleteObject(key);
        if (expected == null) {
            throw new ServiceException("图片验证码已过期");
        }
        if (!expected.equalsIgnoreCase(answer.trim())) {
            throw new ServiceException("图片验证码错误");
        }
    }

    private void rateLimit(String key, int count, int seconds, String message) {
        if (RedisUtils.rateLimiter(key, RateType.OVERALL, count, seconds) < 0) {
            throw new ServiceException(message);
        }
    }

    private SysUserVo findByEmail(String email) {
        return userMapper.lambda()
            .apply("lower(email) = {0}", email)
            .voOne();
    }

    private void ensureMailEnabled() {
        mailSettingsService.requireEnabled();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private OffsetDateTime recoveryCodeExpiresAt() {
        return OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(
            properties.getOnboarding().getEmailCodeTtlMinutes()
        );
    }

    private Long currentUserId() {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("登录状态无效");
        }
        return userId;
    }
}
