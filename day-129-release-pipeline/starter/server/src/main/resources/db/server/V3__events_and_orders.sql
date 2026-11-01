-- Day 121: the public ticket site. The server now knows the event itself
-- (name, venue, times, prices), and keeps the orders fans place online.

create table events (
    id        varchar(50)  primary key,
    name      varchar(100) not null,
    venue     varchar(100) not null,
    starts_at timestamp with time zone not null,
    ends_at   timestamp with time zone not null
);

insert into events (id, name, venue, starts_at, ends_at)
values ('kilele-2026', 'Kilele Nights', 'Ngong Racecourse',
        timestamp with time zone '2026-11-14 18:00:00+03:00',
        timestamp with time zone '2026-11-15 04:00:00+03:00');

alter table ticket_types add column name varchar(30) default '' not null;
alter table ticket_types add column price_cents bigint default 0 not null;
update ticket_types set name = 'Regular', price_cents = 150000 where event_id = 'kilele-2026' and code = 'REG';
update ticket_types set name = 'VIP', price_cents = 500000 where event_id = 'kilele-2026' and code = 'VIP';

-- One row per order placed on the site. While an order is AWAITING_PAYMENT its
-- places are held: nobody else, online or at a gate, can have them.
create table orders (
    reference    varchar(12)  primary key,
    event_id     varchar(50)  not null,
    type_code    varchar(10)  not null,
    quantity     integer      not null,
    buyer        varchar(60)  not null,
    phone        varchar(12)  not null,
    amount_cents bigint       not null,
    status       varchar(20)  not null,
    created_at   timestamp with time zone not null,
    foreign key (event_id, type_code) references ticket_types (event_id, code)
);
