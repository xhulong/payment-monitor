do $$
begin
    if to_regclass('sys_message') is not null then
        update sys_message
        set title = replace(title, 'RuoYi-Vue-Plus', 'LuLuPay'),
            message = replace(message, 'RuoYi-Vue-Plus', 'LuLuPay'),
            content = replace(content, 'RuoYi-Vue-Plus', 'LuLuPay')
        where title like '%RuoYi-Vue-Plus%'
           or message like '%RuoYi-Vue-Plus%'
           or content like '%RuoYi-Vue-Plus%';
    end if;
end
$$;
