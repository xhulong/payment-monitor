alter table pm_sensitive_operation_log
    drop constraint if exists chk_pm_sensitive_operation_verification;

alter table pm_sensitive_operation_log
    alter column verification_method set default 'SESSION';

alter table pm_sensitive_operation_log
    add constraint chk_pm_sensitive_operation_verification
        check (verification_method in ('MFA', 'SESSION'));

update sys_menu
set remark = '记录通过 MFA 或登录会话确认执行的强制补单和撤销支付确认',
    update_time = now()
where menu_id = 1900100000000000090;
