-- Usernames and email addresses are both login identifiers. Enforce
-- case-insensitive uniqueness so one identifier always resolves to one account.
create unique index if not exists uk_sys_user_name_normalized
    on sys_user (lower(user_name))
    where del_flag = '0';
