-- Activity feed: who added, edited or deleted what, and when. One row per change, written in the same
-- transaction as the change itself. Names and amounts are copied in, so an entry still reads correctly
-- after the expense or member it mentions has been deleted or renamed.

create table activity (
    id         bigint identity (1, 1) primary key,
    group_id   bigint            not null references tally_group (id) on delete cascade,
    actor_id   bigint            null references app_user (id),
    actor_name nvarchar(50)      not null,
    type       varchar(30)       not null,
    subject    nvarchar(120)     null,
    amount     numeric(12, 2)    null,
    details    nvarchar(400)     null,
    created_at datetimeoffset(6) not null
);

create index idx_activity_group_created on activity (group_id, created_at desc);
