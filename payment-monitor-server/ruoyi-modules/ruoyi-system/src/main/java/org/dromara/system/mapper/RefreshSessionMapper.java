package org.dromara.system.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.system.domain.PmRefreshSession;

import java.time.OffsetDateTime;

public interface RefreshSessionMapper
    extends BaseMapperPlus<PmRefreshSession, PmRefreshSession> {

    @Select("""
        select *
        from pm_refresh_session
        where token_hash = #{tokenHash}
        for update
        """)
    PmRefreshSession selectByTokenHashForUpdate(String tokenHash);

    @Update("""
        update pm_refresh_session
        set status = 'REVOKED',
            revoked_at = #{revokedAt},
            revoke_reason = #{reason},
            updated_at = #{revokedAt}
        where user_id = #{userId}
          and status = 'ACTIVE'
        """)
    int revokeActiveByUserId(
        @Param("userId") Long userId,
        @Param("reason") String reason,
        @Param("revokedAt") OffsetDateTime revokedAt
    );

    @Update("""
        update pm_refresh_session
        set status = 'REVOKED',
            revoked_at = #{revokedAt},
            revoke_reason = #{reason},
            updated_at = #{revokedAt}
        where family_id = #{familyId}
          and status = 'ACTIVE'
        """)
    int revokeActiveByFamilyId(
        @Param("familyId") String familyId,
        @Param("reason") String reason,
        @Param("revokedAt") OffsetDateTime revokedAt
    );
}
