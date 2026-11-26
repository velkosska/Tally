-- Accounts: people log in with email + password, and group members can be linked to an account.

create table app_user (
    id            bigint identity (1, 1) primary key,
    email         nvarchar(254)     not null,
    display_name  nvarchar(50)      not null,
    password_hash nvarchar(100)     not null,
    created_at    datetimeoffset(6) not null,
    constraint uk_app_user_email unique (email)
);

-- Spring Security's table for "remember me" logins (JdbcTokenRepositoryImpl).
-- Each row is one remembered browser; the token rotates on every use.
-- (datetime2, because "timestamp" in SQL Server is a row-version counter, not a date.)
create table persistent_logins (
    series    nvarchar(64)  primary key,
    username  nvarchar(254) not null,
    token     nvarchar(64)  not null,
    last_used datetime2     not null
);

-- A member is either just a name (someone without an account) or linked to a user.
alter table group_member add user_id bigint null
    constraint fk_group_member_user references app_user (id) on delete set null;
GO

-- (GO ends the batch, so SQL Server knows the new column before the indexes below use it.)

-- A user can be linked to at most one member per group. Filtered, because a plain unique constraint
-- in SQL Server would allow only one member without an account per group.
create unique index uk_group_member_user on group_member (group_id, user_id) where user_id is not null;
create index idx_group_member_user on group_member (user_id);
