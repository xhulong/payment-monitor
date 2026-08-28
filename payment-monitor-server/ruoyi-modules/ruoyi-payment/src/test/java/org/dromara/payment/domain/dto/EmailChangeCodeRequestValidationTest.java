package org.dromara.payment.domain.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("dev")
class EmailChangeCodeRequestValidationTest {

    @Test
    void acceptsExistingPasswordShorterThanNewPasswordPolicyMinimum() {
        EmailChangeCodeRequest request = request("old-pass");

        assertTrue(validate(request).isEmpty());
    }

    @Test
    void rejectsCurrentPasswordLongerThanRequestLimit() {
        EmailChangeCodeRequest request = request("a".repeat(65));

        var violations = validate(request);

        assertEquals(1, violations.size());
        assertEquals(
            "当前密码长度不能超过64位",
            violations.iterator().next().getMessage()
        );
    }

    private EmailChangeCodeRequest request(String password) {
        EmailChangeCodeRequest request = new EmailChangeCodeRequest();
        request.setNewEmail("new@example.test");
        request.setPassword(password);
        return request;
    }

    private java.util.Set<jakarta.validation.ConstraintViolation<EmailChangeCodeRequest>>
    validate(EmailChangeCodeRequest request) {
        try (ValidatorFactory factory =
                 Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            return validator.validate(request);
        }
    }
}
