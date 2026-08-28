package org.dromara.payment.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.payment.domain.PmAccountRecoveryChallenge;

import java.time.OffsetDateTime;

public interface AccountRecoveryChallengeMapper
    extends BaseMapperPlus<
        PmAccountRecoveryChallenge,
        PmAccountRecoveryChallenge
    > {

    @Select("""
        select 1
        from pg_advisory_xact_lock(
            hashtextextended(#{scope}, 0)
        )
        """)
    Integer lockIssueScope(@Param("scope") String scope);

    @Select("""
        select *
        from pm_account_recovery_challenge
        where challenge_type = #{challengeType}
          and user_id = #{userId}
          and target_email = #{targetEmail}
          and status = 'PENDING'
        order by created_at desc
        limit 1
        for update
        """)
    PmAccountRecoveryChallenge selectPendingForUpdate(
        @Param("challengeType") String challengeType,
        @Param("userId") Long userId,
        @Param("targetEmail") String targetEmail
    );

    @Select("""
        select *
        from pm_account_recovery_challenge
        where id = #{id}
        for update
        """)
    PmAccountRecoveryChallenge selectByIdForUpdate(Long id);

    @Update("""
        update pm_account_recovery_challenge
        set status = 'CANCELLED',
            resolved_at = #{resolvedAt},
            resolution_reason = #{reason},
            updated_at = #{resolvedAt}
        where challenge_type = #{challengeType}
          and user_id = #{userId}
          and status = 'PENDING'
        """)
    int cancelPending(
        @Param("challengeType") String challengeType,
        @Param("userId") Long userId,
        @Param("reason") String reason,
        @Param("resolvedAt") OffsetDateTime resolvedAt
    );
}
