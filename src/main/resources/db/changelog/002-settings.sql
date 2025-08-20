--liquibase formatted sql

--changeset dsamorukov:002-settings.sql-1

CREATE TABLE setting
(
    id                integer      NOT NULL,
    bot_count         bigint       NOT NULL,
    paper_context     boolean      not null,
    bot_leave_percent numeric      not null,
    bot_template_name varchar(128) not null,
    PRIMARY KEY (id)
);
insert into setting (id, bot_count, paper_context, bot_leave_percent, bot_template_name)
VALUES (1, 20, false, 30.0, 'SHORT_TEMPLATE');

--changeset dsamorukov:002-settings.sql-2
alter table setting add column bot_leave_enabled boolean not null default false;

--changeset dsamorukov:002-settings.sql-3
alter table setting add column bot_archive_enabled boolean not null default true;
