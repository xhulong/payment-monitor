package org.dromara.payment.support;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("dev")
class MerchantRoleLabelsTest {

    @Test
    void mapsMerchantRoleCodesToChineseLabels() {
        assertEquals("所有者", MerchantRoleLabels.label("OWNER"));
        assertEquals("管理员", MerchantRoleLabels.label("ADMIN"));
        assertEquals("财务", MerchantRoleLabels.label("FINANCE"));
        assertEquals("开发者", MerchantRoleLabels.label("DEVELOPER"));
        assertEquals("只读", MerchantRoleLabels.label("VIEWER"));
    }
}
