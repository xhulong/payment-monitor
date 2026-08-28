package org.dromara.payment.context;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("dev")
class MerchantContextTest {

    @AfterEach
    void clearContext() {
        MerchantContext.clear();
    }

    @Test
    void platformAllScopeAllowsAllQueriesButRequiresExplicitCreateTarget() {
        MerchantContext.set(
            MerchantContext.PLATFORM_ACCOUNT,
            MerchantContext.ALL_SCOPE,
            null,
            true,
            MerchantContext.PLATFORM_TIMEZONE);

        assertNull(MerchantContext.resolveQueryMerchantId(null));
        assertEquals(2L, MerchantContext.resolveQueryMerchantId(2L));
        assertEquals(2L, MerchantContext.requireTargetMerchantId(2L));
        MerchantContext.requireAccessibleMerchant(99L);
        assertThrows(
            ServiceException.class,
            () -> MerchantContext.requireTargetMerchantId(null));
    }

    @Test
    void platformMerchantFilterRestrictsListsButNotEntityOwnedDetails() {
        MerchantContext.set(
            MerchantContext.PLATFORM_ACCOUNT,
            MerchantContext.MERCHANT_SCOPE,
            2L,
            true,
            MerchantContext.PLATFORM_TIMEZONE);

        assertEquals(2L, MerchantContext.resolveQueryMerchantId(null));
        assertEquals(2L, MerchantContext.resolveQueryMerchantId(2L));
        assertThrows(
            ServiceException.class,
            () -> MerchantContext.resolveQueryMerchantId(3L));
        MerchantContext.requireAccessibleMerchant(3L);
    }

    @Test
    void merchantUserRemainsPinnedToBoundMerchant() {
        MerchantContext.set(
            MerchantContext.MERCHANT_ACCOUNT,
            MerchantContext.MERCHANT_SCOPE,
            11L,
            false,
            "Asia/Shanghai");

        assertEquals(11L, MerchantContext.resolveQueryMerchantId(null));
        assertEquals(11L, MerchantContext.requireTargetMerchantId(null));
        assertThrows(
            ServiceException.class,
            () -> MerchantContext.resolveQueryMerchantId(12L));
        assertThrows(
            ServiceException.class,
            () -> MerchantContext.requireTargetMerchantId(12L));
        assertThrows(
            ServiceException.class,
            () -> MerchantContext.requireAccessibleMerchant(12L));
    }

    @Test
    void platformAccountWithoutAllMerchantPermissionCannotReadPaymentData() {
        MerchantContext.set(
            MerchantContext.PLATFORM_ACCOUNT,
            MerchantContext.ALL_SCOPE,
            null,
            false,
            MerchantContext.PLATFORM_TIMEZONE);

        assertThrows(
            ServiceException.class,
            () -> MerchantContext.resolveQueryMerchantId(null));
        assertThrows(
            ServiceException.class,
            () -> MerchantContext.requireTargetMerchantId(7L));
        assertThrows(
            ServiceException.class,
            () -> MerchantContext.requireAccessibleMerchant(7L));
    }

    @Test
    void mixedMerchantBatchIsRejectedBeforeMutationStarts() {
        MerchantContext.set(
            MerchantContext.PLATFORM_ACCOUNT,
            MerchantContext.ALL_SCOPE,
            null,
            true,
            MerchantContext.PLATFORM_TIMEZONE);

        assertEquals(
            7L,
            MerchantContext.requireSingleAccessibleMerchant(List.of(7L, 7L)));
        assertThrows(
            ServiceException.class,
            () -> MerchantContext.requireSingleAccessibleMerchant(List.of(7L, 8L)));
    }
}
