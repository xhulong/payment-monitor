package org.dromara.common.encrypt.v2.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dromara.common.encrypt.annotation.ApiCryptoV2;
import org.dromara.common.encrypt.v2.crypto.ApiCryptoV2Context;
import org.dromara.common.encrypt.v2.crypto.ApiCryptoV2Exception;
import org.dromara.common.encrypt.v2.crypto.ApiCryptoV2ReplayGuard;
import org.dromara.common.encrypt.v2.crypto.ApiCryptoV2Service;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;

/**
 * Opt-in request/response filter for api-crypto-v2.
 */
public class ApiCryptoV2Filter implements Filter {

    private static final MediaType API_CRYPTO_MEDIA_TYPE =
        MediaType.parseMediaType(ApiCryptoV2Service.CONTENT_TYPE);

    private final org.dromara.common.encrypt.v2.config.ApiCryptoV2Properties properties;
    private final ApiCryptoV2Service service;
    private final ApiCryptoV2ReplayGuard replayGuard;
    private final RequestMappingHandlerMapping handlerMapping;

    public ApiCryptoV2Filter(
        org.dromara.common.encrypt.v2.config.ApiCryptoV2Properties properties,
        ApiCryptoV2Service service,
        ApiCryptoV2ReplayGuard replayGuard,
        RequestMappingHandlerMapping handlerMapping
    ) {
        this.properties = properties;
        this.service = service;
        this.replayGuard = replayGuard;
        this.handlerMapping = handlerMapping;
    }

    @Override
    public void doFilter(
        ServletRequest request,
        ServletResponse response,
        FilterChain chain
    ) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
            || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        ApiCryptoV2 annotation = findAnnotation(httpRequest);
        boolean hasV2Marker = hasV2Marker(httpRequest);
        if (annotation == null) {
            if (hasV2Marker) {
                writeProtocolError(httpResponse);
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        if (!annotation.request() && annotation.response()) {
            writeProtocolError(httpResponse);
            return;
        }

        ApiCryptoV2Context context = null;
        ServletRequest effectiveRequest = httpRequest;
        if (annotation.request()) {
            if (!isBodyMethod(httpRequest.getMethod()) || !hasValidV2Headers(httpRequest)) {
                writeProtocolError(httpResponse);
                return;
            }
            try {
                byte[] encryptedBody = readBody(httpRequest);
                String path = requestPath(httpRequest);
                ApiCryptoV2Service.DecodedRequest decoded = service.decryptRequest(
                    encryptedBody,
                    httpRequest.getMethod(),
                    path);
                consumeReplayId(decoded.context().jti());
                context = decoded.context();
                effectiveRequest = new ApiCryptoV2RequestWrapper(httpRequest, decoded.plaintext());
            } catch (Exception e) {
                writeProtocolError(httpResponse);
                return;
            }
        }

        if (!annotation.response()) {
            try {
                chain.doFilter(effectiveRequest, httpResponse);
            } finally {
                clearContext(context);
            }
            return;
        }

        ApiCryptoV2ResponseWrapper responseWrapper = new ApiCryptoV2ResponseWrapper(httpResponse);
        try {
            chain.doFilter(effectiveRequest, responseWrapper);

            if (context == null) {
                writeProtocolError(httpResponse);
                return;
            }

            byte[] encryptedResponse = service.encryptResponse(
                responseWrapper.body(),
                context,
                responseWrapper.getStatus());

            httpResponse.setContentType(ApiCryptoV2Service.CONTENT_TYPE);
            httpResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
            httpResponse.setHeader(ApiCryptoV2Service.VERSION_HEADER, "2");
            httpResponse.setContentLength(encryptedResponse.length);
            httpResponse.getOutputStream().write(encryptedResponse);
        } catch (ApiCryptoV2Exception e) {
            writeProtocolError(httpResponse);
        } finally {
            clearContext(context);
        }
    }

    private void consumeReplayId(String jti) {
        boolean accepted = replayGuard.consume(
            jti,
            Duration.ofSeconds(properties.getReplayTtlSeconds()));
        if (!accepted) {
            throw new ApiCryptoV2Exception("Replay request");
        }
    }

    private byte[] readBody(HttpServletRequest request) throws IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > properties.getMaxBodyBytes()) {
            throw new ApiCryptoV2Exception("Request body too large");
        }
        byte[] body = request.getInputStream().readAllBytes();
        if (body.length > properties.getMaxBodyBytes()) {
            throw new ApiCryptoV2Exception("Request body too large");
        }
        if (body.length == 0) {
            throw new ApiCryptoV2Exception("Request body is empty");
        }
        return body;
    }

    private boolean hasV2Marker(HttpServletRequest request) {
        String version = request.getHeader(ApiCryptoV2Service.VERSION_HEADER);
        String contentType = request.getContentType();
        return version != null
            || (contentType != null
            && contentType.toLowerCase(Locale.ROOT)
            .startsWith(ApiCryptoV2Service.CONTENT_TYPE));
    }

    private boolean hasValidV2Headers(HttpServletRequest request) {
        String version = request.getHeader(ApiCryptoV2Service.VERSION_HEADER);
        String contentType = request.getContentType();
        return "2".equals(version)
            && isValidContentType(contentType);
    }

    private boolean isValidContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        try {
            MediaType candidate = MediaType.parseMediaType(contentType);
            return API_CRYPTO_MEDIA_TYPE.getType().equalsIgnoreCase(candidate.getType())
                && API_CRYPTO_MEDIA_TYPE.getSubtype().equalsIgnoreCase(candidate.getSubtype());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isBodyMethod(String method) {
        return HttpMethod.POST.matches(method)
            || HttpMethod.PUT.matches(method)
            || HttpMethod.PATCH.matches(method)
            || HttpMethod.DELETE.matches(method);
    }

    private String requestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private ApiCryptoV2 findAnnotation(HttpServletRequest request) {
        try {
            HandlerExecutionChain chain = handlerMapping.getHandler(request);
            if (chain != null && chain.getHandler() instanceof HandlerMethod method) {
                return method.getMethodAnnotation(ApiCryptoV2.class);
            }
        } catch (Exception ignored) {
            // The MVC dispatcher will report the actual routing error later.
        }
        return null;
    }

    private void clearContext(ApiCryptoV2Context context) {
        if (context != null && context.masterKey() != null) {
            Arrays.fill(context.masterKey(), (byte) 0);
        }
    }

    private void writeProtocolError(HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.reset();
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(
            "{\"code\":400,\"msg\":\"" + ApiCryptoV2Service.ERROR_CODE + "\",\"data\":null}");
    }
}
