-- Day 111: one row per sale, so a family buying four tickets is one sale of
-- four tickets. A sale and its tickets are saved in one transaction: after a
-- power cut there's either the whole sale or none of it.

create table sales (
    id          integer primary key autoincrement,
    event_id    text    not null references events (id),
    type_code   text    not null,
    buyer       text    not null,
    quantity    integer not null,
    total_cents integer not null,
    sold_at     text    not null
);

-- Which sale a ticket belongs to. Tickets sold one at a time before today have none.
alter table tickets add column sale_id integer references sales (id);
