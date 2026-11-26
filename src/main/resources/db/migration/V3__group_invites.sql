-- Invite links: anyone with a valid link can join the group, either as a new member or by claiming
-- a member who was added by name before they had an account.
-- Only a SHA-256 hash of the token is stored, so a leaked database can't be used to join groups.

create table group_invite (
    id         bigint identity (1, 1) primary key,
    group_id   bigint            not null
        constraint fk_group_invite_group references tally_group (id) on delete cascade,
    token_hash nvarchar(64)      not null,
    created_by bigint            null
        constraint fk_group_invite_user references app_user (id),
    created_at datetimeoffset(6) not null,
    expires_at datetimeoffset(6) not null,
    revoked_at datetimeoffset(6) null,
    constraint uk_group_invite_token unique (token_hash)
);

create index idx_group_invite_group on group_invite (group_id);
