-- Account security is managed from the global personal center.
-- Keep the historical permission assignment but remove the duplicate payment
-- sidebar entry; the standalone /account/security route remains available for
-- mandatory MFA setup before the full authenticated layout is loaded.

update sys_menu
set visible = '1',
    remark = '账号安全已迁移至个人中心',
    update_time = now()
where menu_id = 1900100000000000150;
