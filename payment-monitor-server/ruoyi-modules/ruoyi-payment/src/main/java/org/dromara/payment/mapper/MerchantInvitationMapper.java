package org.dromara.payment.mapper;

import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.payment.domain.PmMerchantInvitation;

public interface MerchantInvitationMapper
    extends BaseMapperPlus<PmMerchantInvitation, PmMerchantInvitation> {

    @Select("select * from pm_merchant_invitation where token_hash = #{tokenHash} for update")
    PmMerchantInvitation selectByTokenForUpdate(String tokenHash);
}
