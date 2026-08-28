package org.dromara.web.controller;

import org.dromara.common.encrypt.annotation.ApiCryptoV2;
import org.dromara.payment.controller.AccountRecoveryController;
import org.dromara.system.controller.system.SysProfileController;
import org.dromara.system.controller.system.SysUserController;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("dev")
class AuthTransportContractTest {

    @Test
    void authenticationAndPasswordEndpointsUseApiCryptoV2() {
        assertHasApiCryptoV2(AuthController.class, "login");
        assertHasApiCryptoV2(AuthController.class, "verifyMfaLogin");
        assertHasApiCryptoV2(AccountRecoveryController.class, "passwordResetCode");
        assertHasApiCryptoV2(AccountRecoveryController.class, "passwordResetConfirm");
        assertHasApiCryptoV2(AccountRecoveryController.class, "emailChangeCode");
        assertHasApiCryptoV2(AccountRecoveryController.class, "emailChangeConfirm");
        assertHasApiCryptoV2(SysProfileController.class, "updatePwd");
        assertHasApiCryptoV2(SysUserController.class, "resetPwd");
    }

    private void assertHasApiCryptoV2(Class<?> controllerType, String methodName) {
        boolean encrypted = Arrays.stream(controllerType.getDeclaredMethods())
            .filter(method -> method.getName().equals(methodName))
            .anyMatch(method -> method.getAnnotation(ApiCryptoV2.class) != null);
        assertTrue(
            encrypted,
            () -> controllerType.getSimpleName() + "." + methodName
                + " must opt in to api-crypto-v2");
    }
}
