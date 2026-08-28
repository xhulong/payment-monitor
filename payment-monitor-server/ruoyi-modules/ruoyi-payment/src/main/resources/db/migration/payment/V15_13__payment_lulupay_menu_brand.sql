do $$
begin
    if to_regclass('sys_menu') is not null then
        update sys_menu
        set menu_name = 'LuLuPay项目',
            path = 'https://github.com/xhulong/payment-monitor-server',
            remark = 'LuLuPay项目地址',
            update_time = now()
        where menu_id = 1761400000000000004
           or path = 'https://gitee.com/dromara/RuoYi-Vue-Plus'
           or remark like '%RuoYi-Vue-Plus%';
    end if;
end
$$;
