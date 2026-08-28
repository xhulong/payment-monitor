package org.dromara.common.encrypt.v2.config;

import org.dromara.common.encrypt.annotation.ApiCryptoV2;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class ApiCryptoV2AnnotationValidatorTest {

    @Test
    void acceptsRequestAndResponseEncryptionTogether() throws Exception {
        RequestMappingHandlerMapping handlerMapping = mappingFor("valid");

        ApiCryptoV2AnnotationValidator validator =
            new ApiCryptoV2AnnotationValidator(new ApiCryptoV2Properties(), handlerMapping);

        assertDoesNotThrow(validator::afterSingletonsInstantiated);
    }

    @Test
    void rejectsResponseEncryptionWithoutRequestEncryptionAtStartup() throws Exception {
        RequestMappingHandlerMapping handlerMapping = mappingFor("invalid");

        ApiCryptoV2AnnotationValidator validator =
            new ApiCryptoV2AnnotationValidator(new ApiCryptoV2Properties(), handlerMapping);

        assertThrows(IllegalStateException.class, validator::afterSingletonsInstantiated);
    }

    @Test
    void rejectsPlaintextDowngradeConfigurationAtStartup() throws Exception {
        ApiCryptoV2Properties properties = new ApiCryptoV2Properties();
        properties.setFailOnPlaintext(false);
        ApiCryptoV2AnnotationValidator validator =
            new ApiCryptoV2AnnotationValidator(properties, mappingFor("valid"));

        assertThrows(IllegalStateException.class, validator::afterSingletonsInstantiated);
    }

    private RequestMappingHandlerMapping mappingFor(String methodName) throws Exception {
        InvalidController controller = new InvalidController();
        Method method = InvalidController.class.getDeclaredMethod(methodName);
        HandlerMethod handlerMethod = new HandlerMethod(controller, method);
        RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
        when(handlerMapping.getHandlerMethods()).thenReturn(Map.of(
            RequestMappingInfo.paths("/" + methodName).build(),
            handlerMethod));
        return handlerMapping;
    }

    @RestController
    private static class InvalidController {

        @PostMapping("/valid")
        @ApiCryptoV2(request = true, response = true)
        public void valid() {
        }

        @PostMapping("/invalid")
        @ApiCryptoV2(request = false, response = true)
        public void invalid() {
        }
    }
}
