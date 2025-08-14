--liquibase formatted sql

--changeset dsamorukov:0

CREATE TABLE shedlock
(
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

CREATE TABLE FILE_RULES
(
    DIRECTORY_INFILE    VARCHAR           NOT NULL,
    FILEMASK            VARCHAR           NOT NULL,
    PROCESSEDBATCHSIZE  INTEGER           NOT NULL,
    PROCESSEDCOMMITSIZE INTEGER DEFAULT 1 NOT NULL,
    RETRYCOUNT          INTEGER           NOT NULL,
    MODEBLOB            INTEGER           NOT NULL
);

Insert into FILE_RULES (DIRECTORY_INFILE, FILEMASK, PROCESSEDBATCHSIZE, PROCESSEDCOMMITSIZE,
                                   RETRYCOUNT, MODEBLOB)
values ('/home1/change/ors4p/', '*.ORS', '1000', '1', '10', '1');
