-- Comments on expenses, and one receipt (photo or PDF) per expense, stored in the database.
-- Both go away with their expense (and so with their group) through "on delete cascade".

create table expense_comment (
    id          bigint identity (1, 1) primary key,
    expense_id  bigint            not null references expense (id) on delete cascade,
    author_id   bigint            null references app_user (id),
    author_name nvarchar(50)      not null,
    body        nvarchar(1000)    not null,
    created_at  datetimeoffset(6) not null
);

create index idx_expense_comment_expense on expense_comment (expense_id, created_at);

-- The file itself is varbinary(max). The app limits uploads to 5 MB and checks the first bytes,
-- so only JPEG, PNG, WebP and PDF files are accepted, whatever their name says.
create table expense_receipt (
    id           bigint identity (1, 1) primary key,
    expense_id   bigint            not null references expense (id) on delete cascade,
    file_name    nvarchar(200)     not null,
    content_type varchar(50)       not null,
    size_bytes   int               not null,
    data         varbinary(max)    not null,
    uploaded_by  bigint            null references app_user (id),
    uploaded_at  datetimeoffset(6) not null,
    constraint uk_expense_receipt_expense unique (expense_id)
);
