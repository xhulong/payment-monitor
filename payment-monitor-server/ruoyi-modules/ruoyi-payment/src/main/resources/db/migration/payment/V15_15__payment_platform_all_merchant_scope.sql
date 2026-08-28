-- Platform-wide payment data scope. Super administrators receive this through
-- the existing wildcard permission; no non-super role is granted automatically.
insert into sys_menu values
    (1900100000000001250, '全商户数据范围', 1900100000000000001, 90, '#', '', '', 'N', 'Y', 'F',
     '0', '0', 'payment:scope:all', '#', '', '', 1761000000000000103, 1761100000000000001,
     now(), null, null, '允许平台角色跨商户查询和管理支付数据')
on conflict (menu_id) do update set
    menu_name = excluded.menu_name,
    perms = excluded.perms,
    status = excluded.status,
    update_time = now(),
    remark = excluded.remark;
