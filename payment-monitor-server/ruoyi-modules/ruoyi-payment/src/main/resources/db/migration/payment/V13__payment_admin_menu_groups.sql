-- Phase K follow-up: group payment menus into business-oriented sections.
-- Existing menu ids, permissions and routes are preserved; only hierarchy and
-- display order change.

insert into sys_menu values
    (1900100000000000160, '商户中心', 1900100000000000001, 1, 'merchant-center', '', '', 'N', 'Y', 'M', '0', '0', '', 'peoples', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '商户入驻、成员和账号安全'),
    (1900100000000000170, '支付运营', 1900100000000000001, 2, 'payment-operations', '', '', 'N', 'Y', 'M', '0', '0', '', 'money', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '设备、二维码、订单和支付事件'),
    (1900100000000000180, '资金与风控', 1900100000000000001, 3, 'finance-risk', '', '', 'N', 'Y', 'M', '0', '0', '', 'chart', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '支付交易、审批和内部对账'),
    (1900100000000000190, '平台与开发', 1900100000000000001, 4, 'platform-developer', '', '', 'N', 'Y', 'M', '0', '0', '', 'setting', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '平台审核、Webhook 和 App 发布')
on conflict (menu_id) do update set
    menu_name = excluded.menu_name,
    parent_id = excluded.parent_id,
    order_num = excluded.order_num,
    path = excluded.path,
    component = excluded.component,
    menu_type = excluded.menu_type,
    icon = excluded.icon,
    visible = excluded.visible,
    status = excluded.status,
    remark = excluded.remark,
    update_time = now();

update sys_menu
set parent_id = case
        when menu_id in (1900100000000000140, 1900100000000000070, 1900100000000000120, 1900100000000000150)
            then 1900100000000000160
        when menu_id in (1900100000000000010, 1900100000000000020, 1900100000000000040, 1900100000000000050, 1900100000000000030)
            then 1900100000000000170
        when menu_id in (1900100000000000080, 1900100000000000090, 1900100000000000100)
            then 1900100000000000180
        when menu_id in (1900100000000000110, 1900100000000000060, 1900100000000000130)
            then 1900100000000000190
        else parent_id
    end,
    order_num = case
        when menu_id = 1900100000000000140 then 1
        when menu_id = 1900100000000000070 then 2
        when menu_id = 1900100000000000120 then 3
        when menu_id = 1900100000000000150 then 4
        when menu_id = 1900100000000000010 then 1
        when menu_id = 1900100000000000020 then 2
        when menu_id = 1900100000000000040 then 3
        when menu_id = 1900100000000000050 then 4
        when menu_id = 1900100000000000030 then 5
        when menu_id = 1900100000000000080 then 1
        when menu_id = 1900100000000000090 then 2
        when menu_id = 1900100000000000100 then 3
        when menu_id = 1900100000000000110 then 1
        when menu_id = 1900100000000000060 then 2
        when menu_id = 1900100000000000130 then 3
        else order_num
    end,
    update_time = now()
where menu_id in (
    1900100000000000140, 1900100000000000070, 1900100000000000120,
    1900100000000000150, 1900100000000000010, 1900100000000000020,
    1900100000000000040, 1900100000000000050, 1900100000000000030,
    1900100000000000080, 1900100000000000090, 1900100000000000100,
    1900100000000000110, 1900100000000000060, 1900100000000000130
);

insert into sys_role_menu (role_id, menu_id)
select distinct rm.role_id, groups.menu_id
from sys_role_menu rm
join (
    values
        (1900100000000000160::bigint, array[1900100000000000140, 1900100000000000070, 1900100000000000120, 1900100000000000150]::bigint[]),
        (1900100000000000170::bigint, array[1900100000000000010, 1900100000000000020, 1900100000000000040, 1900100000000000050, 1900100000000000030]::bigint[]),
        (1900100000000000180::bigint, array[1900100000000000080, 1900100000000000090, 1900100000000000100]::bigint[]),
        (1900100000000000190::bigint, array[1900100000000000110, 1900100000000000060, 1900100000000000130]::bigint[])
) groups(menu_id, child_ids) on rm.menu_id = any(groups.child_ids)
on conflict (role_id, menu_id) do nothing;
