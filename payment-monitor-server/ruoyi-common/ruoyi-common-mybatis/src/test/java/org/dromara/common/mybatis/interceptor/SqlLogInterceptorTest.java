package org.dromara.common.mybatis.interceptor;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("dev")
class SqlLogInterceptorTest {

    @Test
    void redactsAllPaymentMapperParameters() {
        assertTrue(SqlLogInterceptor.isSqlParametersRedacted(
            "org.dromara.payment.mapper.PaymentEventMapper.insertOnConflict"));
        assertTrue(SqlLogInterceptor.isSqlParametersRedacted(
            "org.dromara.payment.mapper.DeviceCredentialMapper.insert"));
        assertTrue(SqlLogInterceptor.isSqlParametersRedacted(
            "org.dromara.payment.mapper.PairingCodeMapper.selectForUpdate"));
    }

    @Test
    void leavesUnrelatedMapperSqlLoggingUnchanged() {
        assertFalse(SqlLogInterceptor.isSqlParametersRedacted(
            "org.dromara.system.mapper.SysUserMapper.selectPage"));
        assertFalse(SqlLogInterceptor.isSqlParametersRedacted(null));
    }
}
