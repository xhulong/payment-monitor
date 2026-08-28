package org.dromara.payment.service;

import cn.hutool.crypto.digest.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.domain.PmAccountMfa;
import org.dromara.payment.domain.dto.StepUpRequest;
import org.dromara.payment.domain.dto.TotpCodeRequest;
import org.dromara.payment.domain.vo.StepUpVo;
import org.dromara.payment.domain.vo.TotpSetupVo;
import org.dromara.payment.mapper.AccountMfaMapper;
import org.dromara.payment.security.AccountMfaCipher;
import org.dromara.payment.security.StepUpVerificationMethod;
import org.dromara.payment.security.TotpSupport;
import org.dromara.system.domain.StepUpGrant;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.service.ISysUserService;
import org.dromara.system.service.StepUpTokenStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountMfaService {
    private final AccountMfaMapper mfaMapper;
    private final AccountMfaCipher cipher;
    private final PaymentProperties properties;
    private final StepUpTokenStore stepUpTokenStore;
    private final ISysUserService userService;
    private final MailNotificationPublisher mailNotificationPublisher;

    @Transactional(rollbackFor = Exception.class)
    public TotpSetupVo setup(String stepUpToken) {
        Long userId = currentUserId();
        String accountEmail = requireAccountEmail(userId);
        PmAccountMfa mfa = mfaMapper.selectByUserForUpdate(userId);
        if (mfa != null && Boolean.TRUE.equals(mfa.getEnabled())) {
            requireStepUp(stepUpToken, "MFA_REPLACE");
        }
        String secret = TotpSupport.newSecret();
        boolean created = mfa == null;
        OffsetDateTime now = now();
        if (mfa == null) {
            mfa = new PmAccountMfa();
            mfa.setId(IdWorker.getId());
            mfa.setUserId(userId);
            mfa.setCreatedAt(now);
        }
        mfa.setPendingSecretCiphertext(cipher.encrypt(secret));
        mfa.setPendingExpiresAt(now.plusSeconds(
            properties.getAccountMfa().getSetupTtlSeconds()));
        if (created) {
            mfa.setEnabled(false);
            mfa.setRecoveryCodeHashes("[]");
        }
        mfa.setUpdatedAt(now);
        if (created) {
            mfaMapper.insert(mfa);
        } else {
            mfaMapper.updateById(mfa);
        }
        String uri = buildOtpAuthUri(
            properties.getAccountMfa().getIssuer(),
            accountEmail,
            secret);
        return new TotpSetupVo(secret, uri);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<String> confirm(TotpCodeRequest request) {
        PmAccountMfa mfa = requireMfa();
        boolean replaced = Boolean.TRUE.equals(mfa.getEnabled());
        String encryptedSecret = mfa.getPendingSecretCiphertext();
        if (encryptedSecret == null && !Boolean.TRUE.equals(mfa.getEnabled())) {
            // Compatibility with Phase K setup records created before V15.
            encryptedSecret = mfa.getTotpSecretCiphertext();
        }
        if (encryptedSecret == null
            || (mfa.getPendingExpiresAt() != null && mfa.getPendingExpiresAt().isBefore(now()))) {
            throw new ServiceException("MFA 配置已过期，请重新开始");
        }
        String secret = cipher.decrypt(encryptedSecret);
        long step = TotpSupport.currentStep();
        Long matchedStep = TotpSupport.matchingStep(secret, request.getCode(), step);
        if (matchedStep == null) {
            throw new ServiceException("身份验证器验证码错误");
        }
        List<String> plainCodes = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String recovery = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
            plainCodes.add(recovery);
            hashes.add(BCrypt.hashpw(recovery));
        }
        mfa.setEnabled(true);
        mfa.setTotpSecretCiphertext(encryptedSecret);
        mfa.setPendingSecretCiphertext(null);
        mfa.setPendingExpiresAt(null);
        mfa.setEnabledAt(now());
        mfa.setLastUsedTimeStep(matchedStep);
        mfa.setLastUsedAt(now());
        mfa.setRecoveryCodeHashes(JsonUtils.toJsonString(hashes));
        mfa.setUpdatedAt(now());
        mfaMapper.updateById(mfa);
        mailNotificationPublisher.mfaChanged(
            requireAccountEmail(mfa.getUserId()),
            mfa.getUserId(),
            replaced
        );
        return plainCodes;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<String> recoveryCodes() {
        PmAccountMfa mfa = requireMfa();
        if (!Boolean.TRUE.equals(mfa.getEnabled())) {
            throw new ServiceException("请先启用多因素认证（MFA）");
        }
        List<String> plainCodes = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String recovery = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
            plainCodes.add(recovery);
            hashes.add(BCrypt.hashpw(recovery));
        }
        mfa.setRecoveryCodeHashes(JsonUtils.toJsonString(hashes));
        mfa.setUpdatedAt(now());
        mfaMapper.updateById(mfa);
        mailNotificationPublisher.recoveryCodesRegenerated(
            requireAccountEmail(mfa.getUserId()),
            mfa.getUserId()
        );
        return plainCodes;
    }

    @Transactional(rollbackFor = Exception.class)
    public StepUpVo stepUp(StepUpRequest request) {
        PmAccountMfa mfa = requireMfa();
        if (!Boolean.TRUE.equals(mfa.getEnabled())) {
            throw new ServiceException("当前账号未启用多因素认证（MFA）");
        }
        validateCode(mfa, request.getCode());
        String token = UUID.randomUUID().toString().replace("-", "");
        OffsetDateTime expiresAt = now().plusSeconds(properties.getAccountMfa().getStepUpTtlSeconds());
        String session = StpUtil.getTokenValue();
        stepUpTokenStore.issue(
            token,
            new StepUpGrant(
                currentUserId(),
                session,
                request.getOperation()
            ),
            java.time.Duration.ofSeconds(
                properties.getAccountMfa().getStepUpTtlSeconds()
            )
        );
        return new StepUpVo(token, request.getOperation(), expiresAt);
    }

    public StepUpVerificationMethod requireStepUp(String token, String operation) {
        Long userId = currentUserId();
        if (!enabled(userId)) {
            return StepUpVerificationMethod.SESSION;
        }
        consumeStepUp(token, operation, userId);
        return StepUpVerificationMethod.MFA;
    }

    @Transactional(rollbackFor = Exception.class)
    public void disable(String stepUpToken) {
        Long userId = currentUserId();
        PmAccountMfa mfa = mfaMapper.selectByUserForUpdate(userId);
        if (mfa == null || !Boolean.TRUE.equals(mfa.getEnabled())) {
            return;
        }
        consumeStepUp(stepUpToken, "MFA_DISABLE", userId);
        if (mfaMapper.disableForUser(userId, now()) <= 0) {
            throw new ServiceException("关闭 MFA 失败");
        }
        stepUpTokenStore.revokeAll(userId);
        mailNotificationPublisher.mfaDisabled(
            requireAccountEmail(userId),
            userId
        );
    }

    private void consumeStepUp(String token, String operation, Long userId) {
        if (token == null || token.isBlank()) {
            throw new ServiceException("敏感操作需要 MFA 二次验证");
        }
        StepUpGrant expected = new StepUpGrant(
            userId,
            StpUtil.getTokenValue(),
            operation
        );
        if (!stepUpTokenStore.consume(token, expected)) {
            throw new ServiceException("MFA 二次验证已失效");
        }
    }

    public int revokeStepUpTokens(Long userId) {
        return stepUpTokenStore.revokeAll(userId);
    }

    public boolean enabled(Long userId) {
        PmAccountMfa mfa = mfaMapper.selectOne(new LambdaQueryWrapper<PmAccountMfa>()
            .eq(PmAccountMfa::getUserId, userId)
            .last("limit 1"));
        return mfa != null && Boolean.TRUE.equals(mfa.getEnabled());
    }

    public boolean enabledForCurrentUser() {
        return enabled(currentUserId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void validateLoginCode(Long userId, String code) {
        PmAccountMfa mfa = mfaMapper.selectByUserForUpdate(userId);
        if (mfa == null || !Boolean.TRUE.equals(mfa.getEnabled())) {
            throw new ServiceException("当前账号尚未启用多因素认证（MFA）");
        }
        validateCode(mfa, code);
    }

    private void validateCode(PmAccountMfa mfa, String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase();
        if (!normalized.matches("\\d{6}")) {
            consumeRecoveryCode(mfa, normalized);
            return;
        }
        String secret = cipher.decrypt(mfa.getTotpSecretCiphertext());
        long step = TotpSupport.currentStep();
        Long matchedStep = TotpSupport.matchingStep(secret, normalized, step);
        if (matchedStep == null) {
            throw new ServiceException("身份验证器验证码错误");
        }
        if (mfa.getLastUsedTimeStep() != null && matchedStep <= mfa.getLastUsedTimeStep()) {
            throw new ServiceException("身份验证器验证码已使用，请等待下一组验证码");
        }
        mfa.setLastUsedTimeStep(matchedStep);
        mfa.setLastUsedAt(now());
        mfa.setUpdatedAt(now());
        mfaMapper.updateById(mfa);
    }

    private void consumeRecoveryCode(PmAccountMfa mfa, String code) {
        List<String> hashes = JsonUtils.parseArray(mfa.getRecoveryCodeHashes(), String.class);
        int matchedIndex = -1;
        for (int i = 0; i < hashes.size(); i++) {
            if (BCrypt.checkpw(code, hashes.get(i))) {
                matchedIndex = i;
                break;
            }
        }
        if (matchedIndex < 0) {
            throw new ServiceException("身份验证器验证码或恢复码错误");
        }
        hashes.remove(matchedIndex);
        mfa.setRecoveryCodeHashes(JsonUtils.toJsonString(hashes));
        mfa.setLastUsedAt(now());
        mfa.setUpdatedAt(now());
        mfaMapper.updateById(mfa);
    }

    private PmAccountMfa requireMfa() {
        PmAccountMfa mfa = mfaMapper.selectByUserForUpdate(currentUserId());
        if (mfa == null) {
            throw new ServiceException("请先初始化多因素认证（MFA）");
        }
        return mfa;
    }

    private String requireAccountEmail(Long userId) {
        SysUserVo user = userService.selectUserById(userId);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            throw new ServiceException("当前账号未绑定邮箱，请先绑定邮箱后再配置 MFA");
        }
        return user.getEmail().trim().toLowerCase(Locale.ROOT);
    }

    static String buildOtpAuthUri(String issuer, String accountEmail, String secret) {
        String normalizedIssuer = issuer == null || issuer.isBlank()
            ? "LuLuPay"
            : issuer.trim();
        return "otpauth://totp/" + encodeUriComponent(normalizedIssuer)
            + ":" + encodeUriComponent(accountEmail)
            + "?secret=" + secret
            + "&issuer=" + encodeUriComponent(normalizedIssuer)
            + "&algorithm=SHA1&digits=6&period=30";
    }

    private static String encodeUriComponent(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
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
}
