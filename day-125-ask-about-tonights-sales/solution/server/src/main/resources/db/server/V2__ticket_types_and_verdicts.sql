-- Day 120: the server knows how many places each ticket type has, so it can
-- say no when two gates, offline, both sold the last one.

create table ticket_types (
    event_id varchar(50) not null,
    code     varchar(10) not null,
    capacity integer     not null,
    primary key (event_id, code)
);

insert into ticket_types (event_id, code, capacity) values ('kilele-2026', 'REG', 500);
insert into ticket_types (event_id, code, capacity) values ('kilele-2026', 'VIP', 50);

-- What the server decided about each sale, so a repeat gets the same answer.
alter table sales add column result varchar(10) default 'ACCEPTED' not null;
alter table sales add column detail varchar(200) default '' not null;
