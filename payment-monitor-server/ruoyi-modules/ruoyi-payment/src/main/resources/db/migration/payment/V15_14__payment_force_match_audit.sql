alter table pm_order_match_audit
    drop constraint if exists chk_pm_order_audit_action;

alter table pm_order_match_audit
    add constraint chk_pm_order_audit_action check (
        action in (
            'CREATE',
            'AUTO_MATCH',
            'MANUAL_MATCH',
            'FORCE_MATCH',
            'CONFLICT',
            'CANCEL',
            'EXPIRE'
        )
    );
