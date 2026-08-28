create table if not exists pm_mail_outbox
(
    id                  bigint primary key,
    message_id          varchar(64)   not null unique,
    message_type        varchar(64)   not null,
    deduplication_key   varchar(128) unique,
    payload_ciphertext  text          not null,
    encryption_key_id   varchar(64)   not null,
    status              varchar(16)   not null,
    attempt_count       integer       not null default 0,
    max_attempts        integer       not null,
    next_attempt_at     timestamptz   not null,
    locked_at           timestamptz,
    expires_at          timestamptz,
    sent_at             timestamptz,
    last_error          varchar(500),
    created_at          timestamptz   not null,
    updated_at          timestamptz   not null,
    constraint chk_pm_mail_outbox_status
        check (status in ('PENDING', 'SENDING', 'RETRYING', 'SENT', 'DEAD', 'CANCELLED')),
    constraint chk_pm_mail_outbox_attempts
        check (
            max_attempts between 1 and 20
            and attempt_count between 0 and max_attempts
        ),
    constraint chk_pm_mail_outbox_expiry
        check (expires_at is null or expires_at > created_at)
);

create index if not exists idx_pm_mail_outbox_due
    on pm_mail_outbox (status, next_attempt_at);

create index if not exists idx_pm_mail_outbox_dead
    on pm_mail_outbox (updated_at desc)
    where status = 'DEAD';

create index if not exists idx_pm_mail_outbox_type_time
    on pm_mail_outbox (message_type, created_at desc);
