package org.dromara.payment.integration;

import org.apache.ibatis.session.SqlSession;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.mybatis.core.mapper.LambdaCrudChainWrapper;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.domain.PmMerchant;
import org.dromara.payment.domain.PmMerchantApplication;
import org.dromara.payment.domain.dto.MerchantAccountRegisterRequest;
import org.dromara.payment.domain.dto.MerchantApplicationSaveRequest;
import org.dromara.payment.mapper.*;
import org.dromara.payment.service.MailOutboxService;
import org.dromara.payment.service.MerchantAccountService;
import org.dromara.payment.service.MerchantOnboardingReviewSettingsService;
import org.dromara.payment.service.MerchantOnboardingService;
import org.dromara.system.domain.bo.SysUserBo;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.mapper.SysUserMapper;
import org.dromara.system.service.ISysUserService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.redisson.api.RedissonClient;
import org.springframework.context.support.GenericApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class MerchantRegistrationReviewContainerTest {

    private static final long APPLICANT_ID = 5101L;
    private static final long REVIEWER_ID = 5201L;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("payment_monitor")
            .withUsername("payment_monitor")
            .withPassword("payment_monitor_test");

    @Test
    void registersSubmitsAndApprovesMerchantAgainstPostgres() throws Exception {
        DataSource dataSource =
            PaymentPostgresTestSupport.migrateLatest(POSTGRES, "registration_review");
        var sessionFactory = PaymentPostgresTestSupport.sqlSessionFactory(dataSource);
        assertReviewSettingsDefaults(dataSource);

        try (GenericApplicationContext ignored = redisSpringContext();
             SqlSession session = sessionFactory.openSession(true)) {
            SysUserMapper userMapper = mockAvailableUserMapper();
            ISysUserService userService = mock(ISysUserService.class);
            when(userService.insertUser(any(SysUserBo.class))).thenAnswer(invocation -> {
                SysUserBo user = invocation.getArgument(0);
                user.setUserId(APPLICANT_ID);
                insertRegisteredUser(dataSource, user);
                return 1;
            });

            PaymentProperties properties = new PaymentProperties();
            MerchantAccountService accountService = new MerchantAccountService(
                properties,
                mock(org.dromara.payment.service.MailSettingsService.class),
                userService,
                userMapper,
                mock(MailOutboxService.class),
                mock(org.dromara.payment.service.MailTemplateService.class),
                mock(org.dromara.payment.service.MailNotificationPublisher.class)
            );
            MerchantAccountRegisterRequest registration = registrationRequest();
            String codeKey = "payment:merchant-signup:code:owner@example.test";
            String usedKey = "payment:merchant-signup:used:owner@example.test:654321";

            try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
                redis.when(() -> RedisUtils.getCacheObject(codeKey)).thenReturn("654321");
                redis.when(() -> RedisUtils.getCacheObject(usedKey)).thenReturn(null);

                var registered = accountService.register(registration);

                assertEquals(APPLICANT_ID, registered.userId());
                assertEquals("merchant_owner", registered.username());
                assertEquals("owner@example.test", registered.email());
            }

            SysUserVo applicant = new SysUserVo();
            applicant.setUserId(APPLICANT_ID);
            applicant.setUserName("merchant_owner");
            applicant.setEmail("owner@example.test");
            when(userService.selectUserById(APPLICANT_ID)).thenReturn(applicant);
            MerchantOnboardingReviewSettingsService reviewSettingsService =
                mock(MerchantOnboardingReviewSettingsService.class);
            when(reviewSettingsService.reviewEnabled()).thenReturn(true);

            MerchantOnboardingService onboardingService = new MerchantOnboardingService(
                session.getMapper(MerchantApplicationMapper.class),
                session.getMapper(MerchantApplicationHistoryMapper.class),
                session.getMapper(MerchantMapper.class),
                session.getMapper(MerchantUserMapper.class),
                session.getMapper(AccountMfaMapper.class),
                session.getMapper(QrAssetMapper.class),
                session.getMapper(PaymentDeviceMapper.class),
                session.getMapper(PaymentEventMapper.class),
                mock(org.dromara.payment.context.MerchantAccessService.class),
                userService,
                properties,
                mock(org.dromara.payment.service.MailNotificationPublisher.class),
                reviewSettingsService
            );

            PmMerchantApplication approved;
            try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
                login.when(LoginHelper::getUserId).thenReturn(APPLICANT_ID);
                PmMerchantApplication draft = onboardingService.create(applicationRequest());
                assertEquals("DRAFT", draft.getStatus());
                assertEquals("SUBMITTED", onboardingService.submit(draft.getId()).getStatus());

                login.when(LoginHelper::getUserId).thenReturn(REVIEWER_ID);
                login.when(LoginHelper::isSuperAdmin).thenReturn(true);
                assertEquals("UNDER_REVIEW", onboardingService.claim(draft.getId()).getStatus());
                approved = onboardingService.approve(draft.getId(), "fixture approved");
            }

            assertEquals("APPROVED", approved.getStatus());
            assertNotNull(approved.getMerchantId());
            assertDatabaseState(dataSource, approved.getId(), approved.getMerchantId());
            var adminBindings = session.getMapper(MerchantUserMapper.class)
                .selectAdminBindings(java.util.List.of(approved.getMerchantId()));
            assertEquals(1, adminBindings.size());
            assertEquals(APPLICANT_ID, adminBindings.getFirst().getUserId());
            assertEquals("merchant_owner", adminBindings.getFirst().getUserName());
            assertMerchantJsonbSurvivesEntityUpdate(
                session.getMapper(MerchantMapper.class),
                approved.getMerchantId()
            );
            assertNormalizedRegistrationIndexes(dataSource);
        }
    }

    private void assertMerchantJsonbSurvivesEntityUpdate(
        MerchantMapper merchantMapper,
        Long merchantId
    ) {
        PmMerchant merchant = merchantMapper.selectById(merchantId);
        assertEquals("{}", merchant.getQuotaConfig());

        merchant.setLifecycleStatus(PaymentConstants.MERCHANT_ACTIVE);
        merchant.setQuotaConfig("{\"maxDevices\":5}");
        assertEquals(1, merchantMapper.updateById(merchant));

        PmMerchant updated = merchantMapper.selectById(merchantId);
        assertEquals(PaymentConstants.MERCHANT_ACTIVE, updated.getLifecycleStatus());
        assertEquals("{\"maxDevices\": 5}", updated.getQuotaConfig());
    }

    private void assertReviewSettingsDefaults(DataSource dataSource)
        throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 select config_value
                 from sys_config
                 where config_key =
                     'payment.merchant.onboarding.reviewEnabled'
                 """);
             var result = statement.executeQuery()) {
            assertEquals(true, result.next());
            assertEquals("true", result.getString(1));
        }
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 select count(1)
                 from sys_role_menu
                 where role_id = 1900200000000000003
                   and menu_id = 1900100000000000112
                 """);
             var result = statement.executeQuery()) {
            assertEquals(true, result.next());
            assertEquals(0, result.getInt(1));
        }
    }

    private GenericApplicationContext redisSpringContext() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(RedissonClient.class, () -> mock(RedissonClient.class));
        context.registerBean(JsonMapper.class, () -> JsonMapper.builder().build());
        context.refresh();
        new SpringUtils().setApplicationContext(context);
        return context;
    }

    @SuppressWarnings("unchecked")
    private SysUserMapper mockAvailableUserMapper() {
        SysUserMapper mapper = mock(SysUserMapper.class);
        LambdaCrudChainWrapper<?, ?> chain = mock(LambdaCrudChainWrapper.class);
        when(mapper.lambda()).thenReturn((LambdaCrudChainWrapper) chain);
        when(chain.apply(any(String.class), any())).thenReturn((LambdaCrudChainWrapper) chain);
        when(chain.exists()).thenReturn(false);
        return mapper;
    }

    private void insertRegisteredUser(DataSource dataSource, SysUserBo user) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 insert into sys_user (
                     user_id, dept_id, user_name, nick_name, user_type,
                     email, password, status, del_flag, gender,
                     create_by, update_by, create_time
                 ) values (?, ?, ?, ?, ?, ?, ?, ?, '0', ?, ?, ?, now())
                 """)) {
            statement.setLong(1, user.getUserId());
            statement.setLong(2, user.getDeptId());
            statement.setString(3, user.getUserName());
            statement.setString(4, user.getNickName());
            statement.setString(5, user.getUserType());
            statement.setString(6, user.getEmail());
            statement.setString(7, user.getPassword());
            statement.setString(8, user.getStatus());
            statement.setString(9, user.getGender());
            statement.setLong(10, user.getCreateBy());
            statement.setLong(11, user.getUpdateBy());
            statement.executeUpdate();
        }
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 insert into sys_user_role (user_id, role_id) values (?, ?)
                 """)) {
            statement.setLong(1, user.getUserId());
            statement.setLong(2, PaymentConstants.MERCHANT_APPLICANT_ROLE_ID);
            statement.executeUpdate();
        }
    }

    private void assertDatabaseState(
        DataSource dataSource,
        Long applicationId,
        Long merchantId
    ) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 select a.status,
                        m.lifecycle_status,
                        m.owner_user_id,
                        mu.role_code,
                        (
                            select count(*)
                            from pm_merchant_application_history h
                            where h.application_id = a.id
                        ) as history_count,
                        (
                            select count(*)
                            from sys_user_role ur
                            where ur.user_id = a.user_id
                              and ur.role_id = ?
                        ) as owner_role_count,
                        (
                            select count(*)
                            from sys_user_role ur
                            where ur.user_id = a.user_id
                              and ur.role_id = ?
                        ) as applicant_role_count
                 from pm_merchant_application a
                 join pm_merchant m on m.id = a.merchant_id
                 join pm_merchant_user mu
                   on mu.merchant_id = m.id and mu.user_id = a.user_id
                 where a.id = ? and m.id = ?
                 """)) {
            statement.setLong(1, PaymentConstants.MERCHANT_OWNER_ROLE_ID);
            statement.setLong(2, PaymentConstants.MERCHANT_APPLICANT_ROLE_ID);
            statement.setLong(3, applicationId);
            statement.setLong(4, merchantId);
            try (var result = statement.executeQuery()) {
                result.next();
                assertEquals("APPROVED", result.getString("status"));
                assertEquals("ONBOARDING", result.getString("lifecycle_status"));
                assertEquals(APPLICANT_ID, result.getLong("owner_user_id"));
                assertEquals("OWNER", result.getString("role_code"));
                assertEquals(4, result.getInt("history_count"));
                assertEquals(1, result.getInt("owner_role_count"));
                assertEquals(0, result.getInt("applicant_role_count"));
            }
        }
    }

    private void assertNormalizedRegistrationIndexes(DataSource dataSource) throws Exception {
        assertThrows(SQLException.class, () -> {
            try (var connection = dataSource.getConnection();
                 var statement = connection.createStatement()) {
                statement.execute("""
                    insert into sys_user (
                        user_id, user_name, nick_name, email, status, del_flag
                    ) values (
                        5199, 'MERCHANT_OWNER_2', 'duplicate email',
                        'OWNER@EXAMPLE.TEST', '0', '0'
                    )
                    """);
            }
        });
        assertThrows(SQLException.class, () -> {
            try (var connection = dataSource.getConnection();
                 var statement = connection.createStatement()) {
                statement.execute("""
                    insert into sys_user (
                        user_id, user_name, nick_name, email, status, del_flag
                    ) values (
                        5198, 'MERCHANT_OWNER', 'duplicate username',
                        'other@example.test', '0', '0'
                    )
                    """);
            }
        });
    }

    private MerchantAccountRegisterRequest registrationRequest() {
        MerchantAccountRegisterRequest request = new MerchantAccountRegisterRequest();
        request.setUsername("Merchant_Owner");
        request.setEmail("Owner@Example.Test");
        request.setNickname("Fixture Owner");
        request.setPassword("fixture-password-123");
        request.setEmailCode("654321");
        return request;
    }

    private MerchantApplicationSaveRequest applicationRequest() {
        MerchantApplicationSaveRequest request = new MerchantApplicationSaveRequest();
        request.setMerchantDisplayName("Fixture Merchant");
        request.setApplicantName("Fixture Owner");
        request.setPhoneNumber("13800000000");
        request.setCountryRegion("CN");
        request.setProvince("Test Province");
        request.setCity("Test City");
        request.setPaymentUseCase("Desensitized payment notification acceptance");
        request.setMonthlyOrderRange("0-1000");
        request.setMonthlyAmountRange("0-100000");
        request.setPlannedPlatforms("WECHAT,ALIPAY");
        request.setAgreementVersion("2026-07");
        request.setPrivacyVersion("2026-07");
        return request;
    }
}
