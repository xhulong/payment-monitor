package org.dromara.common.encrypt.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opt-in application-layer request/response encryption for API crypto v2.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiCryptoV2 {

    /**
     * Whether the request body must use api-crypto-v2.
     */
    boolean request() default true;

    /**
     * Whether the response body must use api-crypto-v2.
     *
     * <p>Response encryption requires request encryption so the response key can
     * be derived from the request master key.</p>
     */
    boolean response() default false;
}
