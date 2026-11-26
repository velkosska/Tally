-- Uneven splits: an expense can be split equally, by exact amounts, by percentages or by shares.
-- Each participant row gets a number: their amount, percentage or shares. For an equal split it is 1
-- for everyone, which is also what existing rows get, so old expenses stay split equally.

alter table expense add split_method varchar(10) not null
    constraint df_expense_split_method default 'EQUAL'
    constraint ck_expense_split_method check (split_method in ('EQUAL', 'EXACT', 'PERCENT', 'SHARES'));

alter table expense_participant add weight numeric(12, 2) not null
    constraint df_expense_participant_weight default 1
    constraint ck_expense_participant_weight check (weight > 0);
