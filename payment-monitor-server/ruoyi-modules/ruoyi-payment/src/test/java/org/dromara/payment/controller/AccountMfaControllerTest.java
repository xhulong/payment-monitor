package org.dromara.payment.controller;

import org.dromara.payment.service.AccountMfaService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@Tag("dev")
class AccountMfaControllerTest {

    @Test
    void disablePassesOperationBoundTokenToService() {
        AccountMfaService service = mock(AccountMfaService.class);
        AccountMfaController controller = new AccountMfaController(service);

        controller.disable("step-up-token");

        verify(service).disable("step-up-token");
    }

    @Test
    void disableWithoutTokenSupportsIdempotentDisabledState() {
        AccountMfaService service = mock(AccountMfaService.class);
        AccountMfaController controller = new AccountMfaController(service);

        controller.disable(null);

        verify(service).disable(null);
    }
}
