package org.dromara.payment.domain.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("dev")
class PairDeviceRequestValidationTest {

    @Test
    void missingVersionCodeExplainsThatTheInstalledAppIsOutdated() {
        PairDeviceRequest request = new PairDeviceRequest();
        request.setPairingCode("12345678");
        request.setDeviceName("test-device");
        request.setAppVersion("1.5.0-dev");
        request.setParserVersion("3");

        try (ValidatorFactory factory =
                 Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            var violations = validator.validate(request);

            assertEquals(1, violations.size());
            assertEquals(
                "当前 App 版本过旧，缺少 appVersionCode，请安装最新版后重新配对",
                violations.iterator().next().getMessage()
            );
        }
    }
}
