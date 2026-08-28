package org.dromara.payment.mapper;

import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.payment.domain.PmMerchantApplication;

public interface MerchantApplicationMapper
    extends BaseMapperPlus<PmMerchantApplication, PmMerchantApplication> {

    @Select("select * from pm_merchant_application where id = #{id} for update")
    PmMerchantApplication selectByIdForUpdate(Long id);
}
