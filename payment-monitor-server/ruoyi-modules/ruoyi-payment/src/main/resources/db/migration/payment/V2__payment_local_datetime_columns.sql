alter table pm_merchant
    alter column created_at type timestamp without time zone using created_at at time zone 'Asia/Shanghai',
    alter column updated_at type timestamp without time zone using updated_at at time zone 'Asia/Shanghai';

alter table pm_device
    alter column paired_at type timestamp without time zone using paired_at at time zone 'Asia/Shanghai',
    alter column last_seen_at type timestamp without time zone using last_seen_at at time zone 'Asia/Shanghai',
    alter column created_at type timestamp without time zone using created_at at time zone 'Asia/Shanghai',
    alter column updated_at type timestamp without time zone using updated_at at time zone 'Asia/Shanghai';

alter table pm_device_credential
    alter column created_at type timestamp without time zone using created_at at time zone 'Asia/Shanghai',
    alter column revoked_at type timestamp without time zone using revoked_at at time zone 'Asia/Shanghai';

alter table pm_pairing_code
    alter column expires_at type timestamp without time zone using expires_at at time zone 'Asia/Shanghai',
    alter column used_at type timestamp without time zone using used_at at time zone 'Asia/Shanghai',
    alter column created_at type timestamp without time zone using created_at at time zone 'Asia/Shanghai';

alter table pm_payment_event
    alter column event_time type timestamp without time zone using event_time at time zone 'Asia/Shanghai',
    alter column received_at type timestamp without time zone using received_at at time zone 'Asia/Shanghai';
