package org.dromara.payment.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.lock.annotation.Lock4j;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.constant.GlobalConstants;
import org.dromara.common.core.enums.UserType;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.domain.dto.MerchantAccountRegisterRequest;
import org.dromara.payment.domain.dto.MerchantEmailCodeRequest;
import org.dromara.payment.domain.vo.MerchantRegistrationVo;
import org.dromara.system.domain.bo.SysUserBo;
import org.dromara.system.mapper.SysUserMapper;
import org.dromara.system.service.ISysUserService;
import org.redisson.api.RateType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MerchantAccountService {
    private static final String CODE_PREFIX = "payment:merchant-signup:code:";
    private static final String USED_PREFIX = "payment:merchant-signup:used:";

    private final PaymentProperties properties;
    private final MailSettingsService mailSettingsService;
    private final ISysUserService userService;
    private final SysUserMapper userMapper;
    private final MailOutboxService mailOutboxService;
    private final MailTemplateService mailTemplateService;
    private final MailNotificationPublisher mailNotificationPublisher;

    @Lock4j(keys = {"#request.email.trim().toLowerCase()"}, acquireTimeout = 5000)
    @Transactional(rollbackFor = Exception.class)
    public void sendEmailCode(MerchantEmailCodeRequest request, String clientIp) {
        String email = normalizeEmail(request.getEmail());
        validateImageCaptcha(request.getCaptchaUuid(), request.getCaptchaCode());
        rateLimit("payment:merchant-signup:email:minute:" + email, 1, 60,
            "同一邮箱 60 秒内只能发送一次验证码");
        rateLimit("payment:merchant-signup:email:day:" + email + ":" + LocalDate.now(ZoneOffset.UTC),
            10, 86_400, "同一邮箱每天最多发送 10 次验证码");
        rateLimit("payment:merchant-signup:ip:hour:" + clientIp, 20, 3_600,
            "当前网络发送验证码过于频繁");
        if (emailExists(email)) {
            throw new ServiceException("该邮箱已注册，请直接使用邮箱登录");
        }
        mailSettingsService.requireEnabled();
        String code = RandomUtil.randomNumbers(6);
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
            .plusMinutes(
                properties.getOnboarding().getEmailCodeTtlMinutes()
            );
        String codeKey = CODE_PREFIX + email;
        RedisUtils.setCacheObject(
            codeKey,
            code,
            Duration.ofMinutes(properties.getOnboarding().getEmailCodeTtlMinutes()));
        registerRollbackCleanup(codeKey);
        mailOutboxService.enqueueHtml(
            "MERCHANT_SIGNUP_CODE",
            email,
            "[LuLuPay] 注册验证码",
            mailTemplateService.code(
                "注册 LuLuPay 账号",
                "您正在注册 LuLuPay 账号。",
                code,
                properties.getOnboarding().getEmailCodeTtlMinutes() + " 分钟"
            ),
            null,
            expiresAt
        );
    }

    @Lock4j(keys = {"#request.email.trim().toLowerCase()"}, acquireTimeout = 5000)
    @Transactional(rollbackFor = Exception.class)
    public MerchantRegistrationVo register(MerchantAccountRegisterRequest request) {
        return DataPermissionHelper.ignore(() -> registerWithoutLogin(request));
    }

    /**
     * Public registration runs before a LoginUser exists. User creation checks
     * role assignments through data-permission-aware mappers, so this operation
     * explicitly ignores data permission while retaining the outer transaction.
     */
    private MerchantRegistrationVo registerWithoutLogin(MerchantAccountRegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        String username = normalizeUsername(request.getUsername());
        if (request.getPassword().length() < 12 || request.getPassword().length() > 64) {
            throw new ServiceException("密码长度必须为 12–64 位");
        }
        if (emailExists(email)) {
            throw new ServiceException("该邮箱已注册，请直接使用邮箱登录");
        }
        if (usernameExists(username)) {
            throw new ServiceException("用户名已被使用，请更换后重试");
        }
        String key = CODE_PREFIX + email;
        String storedCode = RedisUtils.getCacheObject(key);
        if (storedCode == null || !storedCode.equals(request.getEmailCode().trim())) {
            throw new ServiceException("邮箱验证码无效或已过期");
        }
        if (RedisUtils.getCacheObject(USED_PREFIX + email + ":" + storedCode) != null) {
            throw new ServiceException("邮箱验证码已使用");
        }
        SysUserBo user = new SysUserBo();
        user.setDeptId(PaymentConstants.DEFAULT_DEPT_ID);
        user.setUserName(username);
        user.setNickName(request.getNickname().trim());
        user.setEmail(email);
        user.setPassword(BCrypt.hashpw(request.getPassword()));
        user.setUserType(UserType.SYS_USER.getUserType());
        user.setStatus("0");
        user.setGender("0");
        user.setRoleIds(new Long[]{PaymentConstants.MERCHANT_APPLICANT_ROLE_ID});
        user.setCreateBy(0L);
        user.setUpdateBy(0L);
        if (userService.insertUser(user) != 1) {
            throw new ServiceException("创建商户申请账号失败");
        }
        RedisUtils.deleteObject(key);
        RedisUtils.setCacheObject(
            USED_PREFIX + email + ":" + storedCode,
            Boolean.TRUE,
            Duration.ofMinutes(properties.getOnboarding().getEmailCodeTtlMinutes()));
        mailNotificationPublisher.accountRegistered(
            email,
            user.getUserName(),
            user.getUserId()
        );
        return new MerchantRegistrationVo(user.getUserId(), user.getUserName(), email);
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

    private void registerRollbackCleanup(String redisKey) {
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        RedisUtils.deleteObject(redisKey);
                    }
                }
            }
        );
    }

    private boolean emailExists(String email) {
        return userMapper.lambda()
            .apply("lower(email) = {0}", email)
            .exists();
    }

    private boolean usernameExists(String username) {
        return userMapper.lambda()
            .apply("lower(user_name) = {0}", username)
            .exists();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
