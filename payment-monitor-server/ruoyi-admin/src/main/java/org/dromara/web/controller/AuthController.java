package org.dromara.web.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.util.ObjectUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthRequest;
import me.zhyd.oauth.utils.AuthStateUtils;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.domain.model.LoginBody;
import org.dromara.common.core.enums.PushSourceEnum;
import org.dromara.common.core.enums.PushTypeEnum;
import org.dromara.common.core.utils.DateUtils;
import org.dromara.common.core.utils.MessageUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.core.utils.ValidatorUtils;
import org.dromara.common.encrypt.annotation.ApiCryptoV2;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.social.config.properties.SocialLoginConfigProperties;
import org.dromara.common.social.config.properties.SocialProperties;
import org.dromara.common.social.utils.SocialUtils;
import org.dromara.system.api.MessageService;
import org.dromara.system.api.domain.PushPayloadDTO;
import org.dromara.system.api.model.LoginUser;
import org.dromara.system.api.model.RegisterBody;
import org.dromara.system.api.model.SocialLoginBody;
import org.dromara.system.domain.vo.SysClientVo;
import org.dromara.system.service.ISysClientService;
import org.dromara.system.service.ISysConfigService;
import org.dromara.system.service.ISysSocialService;
import org.dromara.payment.security.TrustedClientIpResolver;
import org.dromara.payment.service.AccountMfaService;
import org.dromara.system.service.RefreshSessionService;
import org.dromara.web.domain.AuthMfaChallenge;
import org.dromara.web.domain.MfaLoginVerifyRequest;
import org.dromara.web.domain.vo.LoginVo;
import org.dromara.web.service.IAuthStrategy;
import org.dromara.web.service.SysLoginService;
import org.dromara.web.service.SysRegisterService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 认证控制器，提供登录、注册、社交绑定和退出能力。
 *
 * @author Lion Li
 */
@Slf4j
@SaIgnore
@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String MFA_LOGIN_CHALLENGE_PREFIX = "auth:mfa-login:";
    private final SocialProperties socialProperties;
    private final SysLoginService loginService;
    private final SysRegisterService registerService;
    private final ISysConfigService configService;
    private final ISysSocialService socialUserService;
    private final ISysClientService clientService;
    private final ScheduledExecutorService scheduledExecutorService;
    private final MessageService messageService;
    private final AccountMfaService accountMfaService;
    private final RefreshSessionService refreshSessionService;
    private final TrustedClientIpResolver clientIpResolver;

    @org.springframework.beans.factory.annotation.Value("${auth.refresh-cookie.name:PM_REFRESH}")
    private String refreshCookieName;

    @org.springframework.beans.factory.annotation.Value("${auth.refresh-cookie.timeout-seconds:2592000}")
    private long refreshCookieTimeoutSeconds;

    @org.springframework.beans.factory.annotation.Value("${auth.refresh-cookie.secure:false}")
    private boolean refreshCookieSecure;

    @org.springframework.beans.factory.annotation.Value("${auth.mfa-login.ttl-seconds:300}")
    private long mfaLoginTtlSeconds;


    /**
     * 登录方法
     *
     * @param body 登录信息
     * @return 结果
     */
    @PostMapping("/login")
    @ApiCryptoV2(request = true, response = true)
    public R<LoginVo> login(
        @RequestBody String body,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        LoginBody loginBody = JsonUtils.parseObject(body, LoginBody.class);
        ValidatorUtils.validate(loginBody);
        // 授权类型和客户端id
        String clientId = loginBody.getClientId();
        String grantType = loginBody.getGrantType();
        SysClientVo client = clientService.queryByClientId(clientId);
        // 查询不到 client 或 client 内不包含 grantType
        if (ObjectUtil.isNull(client) || !StringUtils.contains(client.getGrantType(), grantType)) {
            log.info("客户端id: {} 认证类型：{} 异常!.", clientId, grantType);
            return R.fail(MessageUtils.message("auth.grant.type.error"));
        } else if (!SystemConstants.NORMAL.equals(client.getStatus())) {
            return R.fail(MessageUtils.message("auth.grant.type.blocked"));
        }
        // 登录
        LoginVo loginVo = IAuthStrategy.login(body, client, grantType);
        LoginUser loginUser = LoginHelper.getLoginUser();
        boolean mfaEnabled = loginUser != null
            && accountMfaService.enabled(loginUser.getUserId());
        loginVo.setMfaRequired(mfaEnabled);
        loginVo.setMfaSetupRequired(false);
        if (mfaEnabled) {
            String challengeToken = newMfaChallenge(loginUser, client);
            StpUtil.logoutByTokenValue(loginVo.getAccessToken());
            loginVo.setAccessToken(null);
            loginVo.setExpireIn(null);
            loginVo.setMfaChallengeToken(challengeToken);
        } else {
            issueRefreshCookie(
                loginUser,
                loginVo.getAccessToken(),
                client,
                request,
                response
            );
            scheduleLoginWelcome(loginUser.getUserId());
        }
        loginVo.setRefreshToken(null);
        return R.ok(loginVo);
    }

    /**
     * 获取第三方绑定跳转地址。
     *
     * @param source 登录来源
     * @return 跳转地址
     */
    @SaIgnore
    @PostMapping("/mfa/verify")
    @ApiCryptoV2(request = true, response = true)
    public R<LoginVo> verifyMfaLogin(
        @Validated @RequestBody MfaLoginVerifyRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse response
    ) {
        String cacheKey = MFA_LOGIN_CHALLENGE_PREFIX + request.getChallengeToken();
        AuthMfaChallenge challenge = RedisUtils.getCacheObject(cacheKey);
        if (challenge == null || challenge.getLoginUser() == null) {
            return R.fail(401, "MFA challenge expired");
        }
        SysClientVo client = clientService.queryByClientId(challenge.getClientId());
        if (client == null || !SystemConstants.NORMAL.equals(client.getStatus())) {
            RedisUtils.deleteObject(cacheKey);
            return R.fail(401, "Client session expired");
        }
        accountMfaService.validateLoginCode(
            challenge.getLoginUser().getUserId(),
            request.getCode());
        RedisUtils.deleteObject(cacheKey);

        LoginHelper.login(challenge.getLoginUser(), IAuthStrategy.buildLoginParameter(client));
        String accessToken = StpUtil.getTokenValue();
        issueRefreshCookie(
            challenge.getLoginUser(),
            accessToken,
            client,
            httpRequest,
            response
        );
        scheduleLoginWelcome(challenge.getLoginUser().getUserId());

        LoginVo loginVo = new LoginVo();
        loginVo.setAccessToken(accessToken);
        loginVo.setExpireIn(StpUtil.getTokenTimeout());
        loginVo.setClientId(client.getClientId());
        loginVo.setMfaRequired(false);
        loginVo.setMfaSetupRequired(false);
        return R.ok(loginVo);
    }

    @SaIgnore
    @PostMapping("/refresh")
    public R<LoginVo> refresh(
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String refreshToken = readCookie(request, refreshCookieName);
        if (StringUtils.isBlank(refreshToken)) {
            return R.fail(401, "Refresh session missing");
        }
        RefreshSessionService.InspectionResult inspection =
            refreshSessionService.inspect(refreshToken);
        if (inspection.status()
            != RefreshSessionService.InspectionStatus.ACTIVE) {
            StpUtil.logoutByTokenValue(refreshToken);
            if (inspection.status()
                == RefreshSessionService.InspectionStatus.REUSE_DETECTED) {
                refreshSessionService.revokeAll(
                    inspection.userId(),
                    "TOKEN_REUSE_DETECTED"
                );
                if (StringUtils.isNotBlank(inspection.loginId())) {
                    StpUtil.logout(inspection.loginId());
                }
            }
            clearRefreshCookie(response);
            return R.fail(401, "Refresh session expired");
        }
        var loginUser = LoginHelper.getLoginUser(refreshToken);
        if (loginUser == null) {
            refreshSessionService.revokeByToken(
                refreshToken,
                "TOKEN_STORE_MISSING"
            );
            clearRefreshCookie(response);
            return R.fail(401, "Refresh session expired");
        }
        String clientId = String.valueOf(
            StpUtil.getExtra(refreshToken, LoginHelper.CLIENT_KEY));
        if (!loginUser.getUserId().equals(inspection.userId())
            || !clientId.equals(inspection.clientId())) {
            refreshSessionService.revokeByToken(
                refreshToken,
                "CONTEXT_MISMATCH"
            );
            StpUtil.logoutByTokenValue(refreshToken);
            clearRefreshCookie(response);
            return R.fail(401, "Refresh session expired");
        }
        SysClientVo client = clientService.queryByClientId(clientId);
        if (client == null
            || !SystemConstants.NORMAL.equals(client.getStatus())) {
            refreshSessionService.revokeByToken(
                refreshToken,
                "CLIENT_INVALID"
            );
            StpUtil.logoutByTokenValue(refreshToken);
            clearRefreshCookie(response);
            return R.fail(401, "Client session expired");
        }

        LoginHelper.login(loginUser, IAuthStrategy.buildLoginParameter(client));
        String accessToken = StpUtil.getTokenValue();
        String replacementToken = createRefreshToken(
            loginUser,
            accessToken,
            client
        );
        RefreshSessionService.RotationResult rotation;
        try {
            rotation = refreshSessionService.rotate(
                refreshToken,
                replacementToken,
                loginUser.getUserId(),
                client.getClientId(),
                refreshExpiresAt(),
                clientIpResolver.resolve(request),
                request.getHeader("User-Agent")
            );
        } catch (RuntimeException e) {
            StpUtil.logoutByTokenValue(replacementToken);
            StpUtil.logoutByTokenValue(accessToken);
            throw e;
        }
        if (rotation.status()
            != RefreshSessionService.RotationStatus.SUCCESS) {
            StpUtil.logoutByTokenValue(replacementToken);
            StpUtil.logoutByTokenValue(accessToken);
            StpUtil.logoutByTokenValue(refreshToken);
            if (rotation.status()
                == RefreshSessionService.RotationStatus.REUSE_DETECTED) {
                refreshSessionService.revokeAll(
                    loginUser.getUserId(),
                    "TOKEN_REUSE_DETECTED"
                );
                StpUtil.logout(loginUser.getLoginId());
            }
            clearRefreshCookie(response);
            return R.fail(401, "Refresh session expired");
        }
        StpUtil.logoutByTokenValue(refreshToken);
        writeRefreshCookie(response, replacementToken);

        LoginVo loginVo = new LoginVo();
        loginVo.setAccessToken(accessToken);
        loginVo.setExpireIn(StpUtil.getTokenTimeout());
        loginVo.setClientId(client.getClientId());
        return R.ok(loginVo);
    }

    @GetMapping("/binding/{source}")
    public R<String> authBinding(@PathVariable("source") String source) {
        SocialLoginConfigProperties obj = socialProperties.getType().get(source);
        if (ObjectUtil.isNull(obj)) {
            return R.fail(source + "平台账号暂不支持");
        }
        AuthRequest authRequest = SocialUtils.getAuthRequest(source, socialProperties);
        String authorizeUrl = authRequest.authorize(AuthStateUtils.createState());
        return R.data(authorizeUrl);
    }

    /**
     * 处理前端回调后的社交账号绑定。
     *
     * @param loginBody 请求体
     * @return 操作结果
     */
    @PostMapping("/social/callback")
    public R<Void> socialCallback(@RequestBody SocialLoginBody loginBody) {
        // 校验token
        StpUtil.checkLogin();
        // 获取第三方登录信息
        AuthResponse<AuthUser> response = SocialUtils.loginAuth(
            loginBody.getSource(), loginBody.getSocialCode(),
            loginBody.getSocialState(), socialProperties);
        AuthUser authUserData = response.getData();
        // 判断授权响应是否成功
        if (!response.ok()) {
            return R.fail(response.getMsg());
        }
        loginService.socialRegister(authUserData);
        return R.ok();
    }


    /**
     * 取消当前用户的社交账号授权。
     *
     * @param socialId socialId
     * @return 操作结果
     */
    @DeleteMapping(value = "/unlock/{socialId}")
    public R<Void> unlockSocial(@PathVariable Long socialId) {
        // 校验token
        StpUtil.checkLogin();
        Boolean rows = socialUserService.deleteWithValidById(socialId);
        return rows ? R.ok() : R.fail("取消授权失败");
    }


    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        revokeRefreshCookie(request, response, "LOGOUT");
        loginService.logout();
        return R.ok("退出成功");
    }

    @PostMapping("/logout-all")
    public R<Void> logoutAll(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = readCookie(request, refreshCookieName);
        LoginUser loginUser = LoginHelper.getLoginUser();
        if (loginUser == null && StringUtils.isNotBlank(refreshToken)) {
            loginUser = LoginHelper.getLoginUser(refreshToken);
        }
        if (loginUser != null) {
            refreshSessionService.revokeAll(
                loginUser.getUserId(),
                "LOGOUT_ALL"
            );
            accountMfaService.revokeStepUpTokens(loginUser.getUserId());
            StpUtil.logout(loginUser.getLoginId());
        }
        revokeRefreshCookie(request, response, "LOGOUT_ALL");
        return R.ok();
    }

    /**
     * 用户注册。
     *
     * @param user 注册信息
     * @return 操作结果
     */
    @PostMapping("/register")
    public R<Void> register(@Validated @RequestBody RegisterBody user) {
        if (!configService.selectRegisterEnabled()) {
            return R.fail("当前系统没有开启注册功能！");
        }
        registerService.register(user);
        return R.ok();
    }

    private String newMfaChallenge(LoginUser loginUser, SysClientVo client) {
        String token = UUID.randomUUID().toString().replace("-", "");
        RedisUtils.setCacheObject(
            MFA_LOGIN_CHALLENGE_PREFIX + token,
            new AuthMfaChallenge(loginUser, client.getClientId()),
            Duration.ofSeconds(mfaLoginTtlSeconds));
        return token;
    }

    private void scheduleLoginWelcome(Long userId) {
        scheduledExecutorService.schedule(() -> {
            messageService.publishMessage(
                List.of(userId),
                PushPayloadDTO.of(
                    PushTypeEnum.MESSAGE,
                    PushSourceEnum.BACKEND,
                    DateUtils.getTodayHour(new Date()) + "好，欢迎登录 LuLuPay 后台管理系统",
                    null
                )
            );
        }, 5, TimeUnit.SECONDS);
    }

    private void issueRefreshCookie(
        org.dromara.system.api.model.LoginUser loginUser,
        String accessToken,
        SysClientVo client,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        if (loginUser == null || StringUtils.isBlank(accessToken)) {
            return;
        }
        String refreshToken = createRefreshToken(loginUser, accessToken, client);
        try {
            refreshSessionService.create(
                loginUser.getUserId(),
                loginUser.getLoginId(),
                client.getClientId(),
                refreshToken,
                refreshExpiresAt(),
                clientIpResolver.resolve(request),
                request.getHeader("User-Agent")
            );
        } catch (RuntimeException e) {
            StpUtil.logoutByTokenValue(refreshToken);
            StpUtil.logoutByTokenValue(accessToken);
            throw e;
        }
        writeRefreshCookie(response, refreshToken);
    }

    private String createRefreshToken(
        LoginUser loginUser,
        String accessToken,
        SysClientVo client
    ) {
        StpUtil.setTokenValue(accessToken);
        SaLoginParameter refreshModel = new SaLoginParameter()
            .setDeviceType("refresh")
            .setTimeout(refreshCookieTimeoutSeconds)
            .setIsLastingCookie(true)
            .setIsWriteHeader(false)
            .setExtra(LoginHelper.CLIENT_KEY, client.getClientId());
        LoginHelper.login(loginUser, refreshModel);
        String refreshToken = StpUtil.getTokenValue();
        StpUtil.setTokenValue(accessToken);
        return refreshToken;
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void revokeRefreshCookie(
        HttpServletRequest request,
        HttpServletResponse response,
        String reason
    ) {
        String refreshToken = readCookie(request, refreshCookieName);
        if (StringUtils.isNotBlank(refreshToken)) {
            refreshSessionService.revokeByToken(refreshToken, reason);
            StpUtil.logoutByTokenValue(refreshToken);
        }
        clearRefreshCookie(response);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader(
            "Set-Cookie",
            refreshCookieName + "=; Max-Age=0; Path=/; HttpOnly; "
                + (refreshCookieSecure ? "Secure; " : "")
                + "SameSite=Strict");
    }

    private OffsetDateTime refreshExpiresAt() {
        return OffsetDateTime.now(ZoneOffset.UTC)
            .plusSeconds(refreshCookieTimeoutSeconds);
    }

    private void writeRefreshCookie(HttpServletResponse response, String refreshToken) {
        response.addHeader(
            "Set-Cookie",
            refreshCookieName + "=" + refreshToken
                + "; Max-Age=" + Duration.ofSeconds(refreshCookieTimeoutSeconds).toSeconds()
                + "; Path=/; HttpOnly; "
                + (refreshCookieSecure ? "Secure; " : "")
                + "SameSite=Strict");
    }

}
