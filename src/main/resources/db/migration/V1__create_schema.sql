-- SQL Server / Azure SQL Database. Text columns are nvarchar so names like "Zoë" and "pastéis" are stored as typed.

create table tally_group (
    id          bigint identity (1, 1) primary key,
    name        nvarchar(80)      not null,
    description nvarchar(255),
    currency    nvarchar(3)       not null,
    created_at  datetimeoffset(6) not null
);

create table group_member (
    id       bigint identity (1, 1) primary key,
    group_id bigint       not null references tally_group (id) on delete cascade,
    name     nvarchar(50) not null,
    constraint uk_group_member_name unique (group_id, name)
);

-- paid_by_id and member_id use "no action" (the default): SQL Server allows only one cascading path
-- to a table, and deleting a group already cascades to its expenses and members.
create table expense (
    id          bigint identity (1, 1) primary key,
    group_id    bigint            not null references tally_group (id) on delete cascade,
    description nvarchar(120)     not null,
    amount      numeric(12, 2)    not null check (amount > 0),
    category    varchar(20)       not null,
    spent_on    date              not null,
    paid_by_id  bigint            not null references group_member (id),
    created_at  datetimeoffset(6) not null
);

create table expense_participant (
    expense_id bigint not null references expense (id) on delete cascade,
    member_id  bigint not null references group_member (id),
    primary key (expense_id, member_id)
);

create index idx_group_member_group on group_member (group_id);
create index idx_expense_group on expense (group_id);
create index idx_expense_participant_member on expense_participant (member_id);
