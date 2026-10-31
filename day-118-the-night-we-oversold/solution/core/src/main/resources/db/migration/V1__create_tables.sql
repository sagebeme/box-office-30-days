-- Day 104: the gate's first tables.
-- Flyway runs this file once, the first time a gate opens the database,
-- and remembers that it did in a table of its own (flyway_schema_history).
-- Never edit this file after it has run: add a V2 file instead.

create table events (
    id        text primary key,
    name      text not null,
    starts_at text not null,  -- an Instant, e.g. 2026-11-14T15:00:00Z
    ends_at   text not null
);

create table ticket_types (
    event_id    text    not null references events (id),
    code        text    not null,
    name        text    not null,
    price_cents integer not null,
    capacity    integer not null,
    position    integer not null,  -- keeps the types in the order the event lists them
    primary key (event_id, code)
);

create table tickets (
    event_id    text    not null references events (id),
    code        text    not null,
    type_code   text    not null,
    buyer       text    not null,
    price_cents integer not null,
    sold_at     text    not null,
    primary key (event_id, code)
);
