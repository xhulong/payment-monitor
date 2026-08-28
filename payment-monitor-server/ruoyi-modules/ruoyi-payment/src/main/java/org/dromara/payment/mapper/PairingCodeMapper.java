package org.dromara.payment.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.payment.domain.PmPairingCode;

/**
 * 配对码 Mapper。
 */
public interface PairingCodeMapper extends BaseMapperPlus<PmPairingCode, PmPairingCode> {

    /**
     * 锁定配对码记录。
     */
    @Select("select * from pm_pairing_code where code_hash = #{codeHash} for update")
    PmPairingCode selectForUpdate(@Param("codeHash") String codeHash);
}
