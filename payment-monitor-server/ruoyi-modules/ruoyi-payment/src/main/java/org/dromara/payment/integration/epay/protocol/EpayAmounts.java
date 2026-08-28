package org.dromara.payment.integration.epay.protocol;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class EpayAmounts {
    private EpayAmounts() {
    }

    public static long toMinor(String money) {
        try {
            long value = new BigDecimal(money).setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2).longValueExact();
            if (value <= 0) {
                throw new ArithmeticException();
            }
            return value;
        } catch (RuntimeException exception) {
            throw new EpayException("订单金额格式不合法");
        }
    }

    public static String toYuan(long minor) {
        return BigDecimal.valueOf(minor, 2).setScale(2).toPlainString();
    }
}
