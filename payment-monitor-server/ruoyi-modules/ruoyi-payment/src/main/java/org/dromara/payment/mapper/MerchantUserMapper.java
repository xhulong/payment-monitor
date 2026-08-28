package org.dromara.payment.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.payment.domain.PmMerchantUser;
import org.dromara.payment.domain.vo.MerchantAdminBindingVo;

import java.util.Collection;
import java.util.List;

public interface MerchantUserMapper extends BaseMapperPlus<PmMerchantUser, PmMerchantUser> {

    @Insert("""
        insert into sys_user_role (user_id, role_id)
        values (#{userId}, #{roleId})
        on conflict (user_id, role_id) do nothing
        """)
    int grantRole(Long userId, Long roleId);

    @Select("select count(1) from sys_user where user_id = #{userId} and del_flag = '0'")
    long userExists(Long userId);

    @Select("select user_name from sys_user where user_id = #{userId} and del_flag = '0'")
    String selectUserName(Long userId);

    @Select("select user_id from sys_user where lower(email) = lower(#{email}) and del_flag = '0' limit 1")
    Long selectUserIdByEmail(String email);

    @Select("""
        <script>
        select mu.merchant_id as merchant_id,
               mu.user_id as user_id,
               u.user_name as user_name
        from pm_merchant_user mu
        left join sys_user u
          on u.user_id = mu.user_id
         and u.del_flag = '0'
        where mu.merchant_id in
        <foreach collection="merchantIds" item="merchantId" open="(" separator="," close=")">
            #{merchantId}
        </foreach>
        order by mu.merchant_id, mu.created_at
        </script>
        """)
    List<MerchantAdminBindingVo> selectAdminBindings(
        @Param("merchantIds") Collection<Long> merchantIds);

    @Delete("""
        delete from sys_user_role
        where user_id = #{userId}
          and role_id in (
            1900200000000000001,
            1900200000000000002,
            1900200000000000004,
            1900200000000000005,
            1900200000000000006,
            1900200000000000007
          )
        """)
    int revokePaymentRoles(Long userId);
}
