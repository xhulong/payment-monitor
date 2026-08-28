create table if not exists pm_account_recovery_challenge
(
    id                  bigint primary key,
    challenge_id        varchar(64)  not null unique,
    challenge_type      varchar(32)  not null,
    user_id             bigint       not null,
    target_email        varchar(254) not null,
    code_hash           varchar(64)  not null,
    status              varchar(16)  not null,
    attempt_count       integer      not null default 0,
    max_attempts        integer      not null,
    expires_at          timestamptz  not null,
    last_attempt_at     timestamptz,
    resolved_at         timestamptz,
    resolution_reason   varchar(64),
    created_ip          varchar(64),
    created_at          timestamptz  not null,
    updated_at          timestamptz  not null,
    constraint chk_pm_account_recovery_type
        check (challenge_type in ('PASSWORD_RESET', 'EMAIL_CHANGE')),
    constraint chk_pm_account_recovery_status
        check (status in ('PENDING', 'CONSUMED', 'EXPIRED', 'CANCELLED', 'LOCKED')),
    constraint chk_pm_account_recovery_code_hash
        check (length(code_hash) = 64),
    constraint chk_pm_account_recovery_attempts
        check (
            max_attempts between 1 and 20
            and attempt_count between 0 and max_attempts
        ),
    constraint chk_pm_account_recovery_lifecycle
        check (
            (status = 'PENDING' and resolved_at is null)
            or (status <> 'PENDING' and resolved_at is not null)
        ),
    constraint chk_pm_account_recovery_expiry
        check (expires_at > created_at)
);

create unique index if not exists uk_pm_account_recovery_pending
    on pm_account_recovery_challenge (challenge_type, user_id)
    where status = 'PENDING';

create index if not exists idx_pm_account_recovery_lookup
    on pm_account_recovery_challenge (
        challenge_type,
        user_id,
        target_email,
        created_at desc
    );

create index if not exists idx_pm_account_recovery_expiry
    on pm_account_recovery_challenge (status, expires_at);
