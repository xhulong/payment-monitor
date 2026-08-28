package org.dromara.payment.support;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.dromara.payment.constant.PaymentConstants;

import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MerchantRoleLabels {
    private static final Map<String, String> LABELS = Map.of(
        PaymentConstants.MEMBER_OWNER, "所有者",
        PaymentConstants.MEMBER_ADMIN, "管理员",
        PaymentConstants.MEMBER_FINANCE, "财务",
        PaymentConstants.MEMBER_DEVELOPER, "开发者",
        PaymentConstants.MEMBER_VIEWER, "只读"
    );

    public static String label(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return "-";
        }
        return LABELS.getOrDefault(roleCode, roleCode);
    }
}
