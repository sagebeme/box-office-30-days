-- Day 116: the outbox. Every sale also leaves a note here, in the same
-- transaction, saying "tell the server about this". The sale and its note are
-- saved together or not at all, so no sale is ever forgotten by the sync, even
-- when the Wi-Fi is down for hours.

create table outbox (
    id              integer primary key autoincrement,
    event_id        text    not null references events (id),
    -- The same key every time this sale is sent, so the server can spot a repeat.
    idempotency_key text    not null unique,
    payload         text    not null,
    created_at      text    not null,
    attempts        integer not null default 0,
    last_error      text,
    -- Empty until the server has it.
    sent_at         text
);
