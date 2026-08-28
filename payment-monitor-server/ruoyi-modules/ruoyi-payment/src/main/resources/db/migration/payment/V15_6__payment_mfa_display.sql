-- Use MFA terminology in user-facing menu metadata while retaining TOTP internals.

update sys_menu
set remark = 'MFA 与恢复码管理',
    update_time = now()
where menu_id = 1900100000000000150;
