-- Day 117: two gate laptops on the same router, no internet. Each gate has
-- its own id, so their ticket codes never clash (A-REG-0001, B-REG-0001), and
-- each gate copies the other's sales over the local network.

-- Things this laptop knows about itself, like its gate id.
create table settings (
    name  text primary key,
    value text not null
);

-- Sales copied from another gate, by idempotency key: copied once, never twice.
create table peer_sales (
    idempotency_key text primary key,
    peer            text not null,
    received_at     text not null
);

-- How far through each other gate's feed we've read.
create table peer_cursors (
    peer    text    primary key,
    last_id integer not null
);
