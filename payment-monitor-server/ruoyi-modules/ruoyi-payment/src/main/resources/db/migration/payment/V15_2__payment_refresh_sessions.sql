create table if not exists pm_refresh_session
(
    id                  bigint primary key,
    session_id          varchar(64)  not null unique,
    family_id           varchar(64)  not null,
    rotated_from_id     bigint references pm_refresh_session (id),
    user_id             bigint       not null,
    login_id            varchar(128) not null,
    client_id           varchar(64)  not null,
    token_hash          varchar(64)  not null unique,
    status              varchar(16)  not null,
    issued_at           timestamptz  not null,
    expires_at          timestamptz  not null,
    last_used_at        timestamptz,
    revoked_at          timestamptz,
    revoke_reason       varchar(64),
    created_ip          varchar(64),
    last_used_ip        varchar(64),
    user_agent_hash     varchar(64),
    created_at          timestamptz  not null,
    updated_at          timestamptz  not null,
    constraint chk_pm_refresh_session_status
        check (status in ('ACTIVE', 'ROTATED', 'REVOKED', 'EXPIRED')),
    constraint chk_pm_refresh_session_token_hash
        check (length(token_hash) = 64),
    constraint chk_pm_refresh_session_lifecycle
        check (
            (status = 'ACTIVE' and revoked_at is null)
            or (status <> 'ACTIVE' and revoked_at is not null)
        ),
    constraint chk_pm_refresh_session_expiry
        check (expires_at > issued_at)
);

create unique index if not exists uk_pm_refresh_session_rotation_parent
    on pm_refresh_session (rotated_from_id)
    where rotated_from_id is not null;

create index if not exists idx_pm_refresh_session_user_active
    on pm_refresh_session (user_id, expires_at desc)
    where status = 'ACTIVE';

create index if not exists idx_pm_refresh_session_family
    on pm_refresh_session (family_id, issued_at desc);

create index if not exists idx_pm_refresh_session_expiry
    on pm_refresh_session (status, expires_at);
