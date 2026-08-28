package org.dromara.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.social.config.properties.SocialProperties;
import org.dromara.payment.security.TrustedClientIpResolver;
import org.dromara.payment.service.AccountMfaService;
import org.dromara.system.api.MessageService;
import org.dromara.system.api.model.LoginUser;
import org.dromara.system.domain.vo.SysClientVo;
import org.dromara.system.service.ISysClientService;
import org.dromara.system.service.ISysConfigService;
import org.dromara.system.service.ISysSocialService;
import org.dromara.system.service.RefreshSessionService;
import org.dromara.web.domain.AuthMfaChallenge;
import org.dromara.web.domain.vo.LoginVo;
import org.dromara.web.service.IAuthStrategy;
import org.dromara.web.service.SysLoginService;
import org.dromara.web.service.SysRegisterService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.redisson.api.RedissonClient;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class AuthControllerMfaPolicyTest {

    private static GenericApplicationContext applicationContext;

    @BeforeAll
    static void startApplicationContext() {
        applicationContext = new GenericApplicationContext();
        applicationContext.registerBean(
            JsonMapper.class,
            () -> JsonMapper.builder().build()
        );
        applicationContext.registerBean(
            Validator.class,
            () -> Validation.buildDefaultValidatorFactory().getValidator()
        );
        applicationContext.registerBean(
            RedissonClient.class,
            () -> mock(RedissonClient.class)
        );
        applicationContext.refresh();
        new SpringUtils().setApplicationContext(applicationContext);
    }

    @AfterAll
    static void closeApplicationContext() {
        applicationContext.close();
    }

    @Test
    void accountWithoutMfaCompletesLoginWithoutSetupPendingState() {
        Fixture fixture = fixture(false);

        try (MockedStatic<IAuthStrategy> auth = mockStatic(IAuthStrategy.class);
             MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
             MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            auth.when(() -> IAuthStrategy.login(
                fixture.body,
                fixture.client,
                "password"
            )).thenReturn(loginVo());
            login.when(LoginHelper::getLoginUser).thenReturn(fixture.loginUser);
            login.when(() -> LoginHelper.login(
                eq(fixture.loginUser),
                any(SaLoginParameter.class)
            )).thenAnswer(invocation -> null);
            stp.when(StpUtil::getTokenValue).thenReturn("refresh-token");

            LoginVo result = fixture.controller.login(
                fixture.body,
                fixture.request,
                fixture.response
            ).getData();

            assertNotNull(result);
            assertEquals("access-token", result.getAccessToken());
            assertFalse(result.getMfaRequired());
            assertFalse(result.getMfaSetupRequired());
            assertNull(result.getMfaChallengeToken());
        }

        verify(fixture.refreshSessionService).create(
            eq(7L),
            eq("sys_user:7"),
            eq("web"),
            eq("refresh-token"),
            any(OffsetDateTime.class),
            eq("127.0.0.1"),
            eq("test-agent")
        );
        verify(fixture.scheduledExecutorService).schedule(
            any(Runnable.class),
            eq(5L),
            eq(TimeUnit.SECONDS)
        );
    }

    @Test
    void anyAccountWithEnabledMfaReceivesLoginChallenge() {
        Fixture fixture = fixture(true);

        try (MockedStatic<IAuthStrategy> auth = mockStatic(IAuthStrategy.class);
             MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
             MockedStatic<StpUtil> stp = mockStatic(StpUtil.class);
             MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            auth.when(() -> IAuthStrategy.login(
                fixture.body,
                fixture.client,
                "password"
            )).thenReturn(loginVo());
            login.when(LoginHelper::getLoginUser).thenReturn(fixture.loginUser);

            LoginVo result = fixture.controller.login(
                fixture.body,
                fixture.request,
                fixture.response
            ).getData();

            assertNotNull(result);
            assertNull(result.getAccessToken());
            assertNull(result.getExpireIn());
            assertTrue(result.getMfaRequired());
            assertFalse(result.getMfaSetupRequired());
            assertNotNull(result.getMfaChallengeToken());
            assertFalse(result.getMfaChallengeToken().isBlank());

            stp.verify(() -> StpUtil.logoutByTokenValue("access-token"));
            redis.verify(() -> RedisUtils.setCacheObject(
                startsWith("auth:mfa-login:"),
                any(AuthMfaChallenge.class),
                any(Duration.class)
            ));
        }

        verify(fixture.refreshSessionService, never()).create(
            anyLong(),
            anyString(),
            anyString(),
            anyString(),
            any(),
            any(),
            any()
        );
        verify(fixture.scheduledExecutorService, never()).schedule(
            any(Runnable.class),
            anyLong(),
            any(TimeUnit.class)
        );
    }

    private Fixture fixture(boolean mfaEnabled) {
        ISysClientService clientService = mock(ISysClientService.class);
        ScheduledExecutorService scheduledExecutorService =
            mock(ScheduledExecutorService.class);
        AccountMfaService accountMfaService = mock(AccountMfaService.class);
        RefreshSessionService refreshSessionService =
            mock(RefreshSessionService.class);
        TrustedClientIpResolver clientIpResolver =
            mock(TrustedClientIpResolver.class);
        AuthController controller = new AuthController(
            mock(SocialProperties.class),
            mock(SysLoginService.class),
            mock(SysRegisterService.class),
            mock(ISysConfigService.class),
            mock(ISysSocialService.class),
            clientService,
            scheduledExecutorService,
            mock(MessageService.class),
            accountMfaService,
            refreshSessionService,
            clientIpResolver
        );
        ReflectionTestUtils.setField(controller, "refreshCookieName", "PM_REFRESH");
        ReflectionTestUtils.setField(controller, "refreshCookieTimeoutSeconds", 3600L);
        ReflectionTestUtils.setField(controller, "refreshCookieSecure", false);
        ReflectionTestUtils.setField(controller, "mfaLoginTtlSeconds", 300L);

        SysClientVo client = new SysClientVo();
        client.setClientId("web");
        client.setGrantType("password");
        client.setStatus(SystemConstants.NORMAL);
        client.setDeviceType("pc");
        client.setTimeout(1800L);
        client.setActiveTimeout(900L);
        when(clientService.queryByClientId("web")).thenReturn(client);

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(7L);
        loginUser.setUserType("sys_user");
        when(accountMfaService.enabled(7L)).thenReturn(mfaEnabled);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("User-Agent")).thenReturn("test-agent");
        when(clientIpResolver.resolve(request)).thenReturn("127.0.0.1");

        return new Fixture(
            controller,
            client,
            loginUser,
            request,
            mock(HttpServletResponse.class),
            scheduledExecutorService,
            refreshSessionService,
            """
                {"clientId":"web","grantType":"password"}
                """
        );
    }

    private LoginVo loginVo() {
        LoginVo loginVo = new LoginVo();
        loginVo.setAccessToken("access-token");
        loginVo.setExpireIn(1800L);
        loginVo.setClientId("web");
        return loginVo;
    }

    private record Fixture(
        AuthController controller,
        SysClientVo client,
        LoginUser loginUser,
        HttpServletRequest request,
        HttpServletResponse response,
        ScheduledExecutorService scheduledExecutorService,
        RefreshSessionService refreshSessionService,
        String body
    ) {
    }
}
