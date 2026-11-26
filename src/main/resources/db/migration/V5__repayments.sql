-- Payments people make to each other to settle up ("Ben paid Ana €42.10"). They count towards the
-- balances like an expense paid by one person for exactly one other person, so the settle-up plan shrinks.

create table repayment (
    id             bigint identity (1, 1) primary key,
    group_id       bigint            not null references tally_group (id) on delete cascade,
    from_member_id bigint            not null references group_member (id),
    to_member_id   bigint            not null references group_member (id),
    amount         numeric(12, 2)    not null check (amount > 0),
    paid_on        date              not null,
    note           nvarchar(120)     null,
    created_by     bigint            null references app_user (id),
    created_at     datetimeoffset(6) not null,
    constraint ck_repayment_different_people check (from_member_id <> to_member_id)
);

create index idx_repayment_group on repayment (group_id);
create index idx_repayment_from on repayment (from_member_id);
create index idx_repayment_to on repayment (to_member_id);
