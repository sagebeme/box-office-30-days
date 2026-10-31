-- The sync server's tables. The same ideas as on the gate laptop, in H2.

-- One row per gate laptop allowed to sync. We keep a hash of its token, never
-- the token itself: someone who copies this table still can't log in.
create table devices (
    gate        varchar(3)  primary key,
    token_hash  char(64)    not null unique,
    created_at  varchar(40) not null
);

-- One row per sale a gate has sent, by idempotency key: sent twice, kept once.
create table sales (
    idempotency_key varchar(100) primary key,
    gate            varchar(3)   not null references devices (gate),
    event_id        varchar(50)  not null,
    received_at     varchar(40)  not null
);

create table tickets (
    event_id    varchar(50)  not null,
    code        varchar(20)  not null,
    sale_key    varchar(100) not null references sales (idempotency_key),
    type_code   varchar(10)  not null,
    buyer       varchar(200) not null,
    phone       varchar(12)  not null,
    price_cents bigint       not null,
    sold_at     varchar(40)  not null,
    primary key (event_id, code)
);
