package org.dromara.common.encrypt.v2.web;

import cn.dev33.satoken.annotation.SaIgnore;
import org.dromara.common.encrypt.v2.crypto.ApiCryptoV2Crypto;
import org.dromara.common.encrypt.v2.crypto.ApiCryptoV2KeyStore;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public RSA key set for api-crypto-v2 clients.
 */
@RestController
@SaIgnore
@RequestMapping("${api-crypto-v2.jwks-path:/api/v2/crypto/jwks}")
public class ApiCryptoV2JwksController {

    private final ApiCryptoV2KeyStore keyStore;

    public ApiCryptoV2JwksController(ApiCryptoV2KeyStore keyStore) {
        this.keyStore = keyStore;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> jwks() {
        Map<String, Object> body = new LinkedHashMap<>();
        List<Map<String, String>> keys = keyStore.jwks();
        body.put("activeKid", keyStore.activeKid());
        body.put("keys", keys);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setCacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic().mustRevalidate());
        String fingerprint = ApiCryptoV2Crypto.base64Url(
            ApiCryptoV2Crypto.sha256(keys.toString().getBytes(StandardCharsets.UTF_8)));
        headers.setETag("\"" + fingerprint + "\"");
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }
}
