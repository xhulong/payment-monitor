package org.dromara.common.web.interceptor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("dev")
class PlusWebInvokeTimeInterceptorTest {

    @Test
    void disablesParameterLoggingForAllDeviceProtocolPaths() {
        assertTrue(PlusWebInvokeTimeInterceptor.isParameterLoggingDisabled("/api/v1/devices/pair"));
        assertTrue(PlusWebInvokeTimeInterceptor.isParameterLoggingDisabled("/api/v1/device/heartbeat"));
        assertTrue(PlusWebInvokeTimeInterceptor.isParameterLoggingDisabled("/api/v1/device/config"));
        assertTrue(PlusWebInvokeTimeInterceptor.isParameterLoggingDisabled("/api/v1/payment-events/batch"));
    }

    @Test
    void disablesParameterLoggingForEasyPayKeyQueryAliases() {
        assertTrue(PlusWebInvokeTimeInterceptor.isParameterLoggingDisabled("/api.php"));
        assertTrue(PlusWebInvokeTimeInterceptor.isParameterLoggingDisabled("/pay/api.php"));
        assertTrue(PlusWebInvokeTimeInterceptor.isParameterLoggingDisabled("/epay/api.php"));
    }

    @Test
    void keepsParameterLoggingForUnrelatedManagementPaths() {
        assertFalse(PlusWebInvokeTimeInterceptor.isParameterLoggingDisabled("/system/user/list"));
        assertFalse(PlusWebInvokeTimeInterceptor.isParameterLoggingDisabled("/payment/device/list"));
        assertFalse(PlusWebInvokeTimeInterceptor.isParameterLoggingDisabled(null));
    }
}
