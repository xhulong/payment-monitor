alter table pm_account_mfa
    alter column totp_secret_ciphertext drop not null,
    add column if not exists pending_secret_ciphertext varchar(1024),
    add column if not exists pending_expires_at timestamptz;

alter table pm_app_release
    add column if not exists verified_package_name varchar(255),
    add column if not exists verified_version_code integer,
    add column if not exists verified_version_name varchar(64),
    add column if not exists verification_status varchar(16) not null default 'LEGACY',
    add column if not exists update_mode varchar(16) not null default 'REQUIRED';

alter table pm_app_release
    add constraint chk_pm_app_release_verification_status
        check (verification_status in ('LEGACY', 'VERIFIED', 'FAILED')),
    add constraint chk_pm_app_release_update_mode
        check (update_mode in ('OPTIONAL', 'REQUIRED', 'SECURITY_BLOCK'));

update pm_app_release
set verified_version_code = version_code,
    verified_version_name = version_name
where verified_version_code is null;
