--liquibase formatted sql

--changeset dsamorukov:001-bot.sql-1

CREATE TABLE bot
(
    bot_id  VARCHAR(128) NOT NULL,
    symbol  VARCHAR(32)  NOT NULL,
    created timestamp    not null,
    adts    NUMERIC      NOT NULL,
    adtv    NUMERIC      NOT NULL,
    PRIMARY KEY (bot_id)
);
