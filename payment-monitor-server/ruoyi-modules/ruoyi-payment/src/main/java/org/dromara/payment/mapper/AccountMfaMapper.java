package org.dromara.payment.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.payment.domain.PmAccountMfa;

import java.time.OffsetDateTime;

public interface AccountMfaMapper extends BaseMapperPlus<PmAccountMfa, PmAccountMfa> {

    @Select("select * from pm_account_mfa where user_id = #{userId} for update")
    PmAccountMfa selectByUserForUpdate(Long userId);

    @Update("""
        update pm_account_mfa
        set enabled = false,
            totp_secret_ciphertext = null,
            pending_secret_ciphertext = null,
            pending_expires_at = null,
            recovery_code_hashes = '[]'::jsonb,
            last_used_time_step = null,
            enabled_at = null,
            last_used_at = null,
            updated_at = #{updatedAt}
        where user_id = #{userId}
        """)
    int disableForUser(
        @Param("userId") Long userId,
        @Param("updatedAt") OffsetDateTime updatedAt
    );
}
