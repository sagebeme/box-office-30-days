-- Day 122: paying with M-Pesa.

-- Every "enter your PIN" prompt we sent, and the order it was for. A fan who
-- asks for a second prompt may still pay the first one, so we keep them all:
-- the callback names its CheckoutRequestID, and this table says which order.
create table stk_requests (
    checkout_request_id varchar(60) primary key,
    order_reference     varchar(12) not null references orders (reference),
    created_at          timestamp with time zone not null
);

-- The M-Pesa receipt once paid, and a few words on why an order didn't go through.
alter table orders add column receipt varchar(20);
alter table orders add column detail varchar(200) default '' not null;

-- Tickets paid for online come from an order, not from a gate's sale.
alter table tickets alter column sale_key set null;
alter table tickets add column order_reference varchar(12);
alter table tickets add constraint tickets_order foreign key (order_reference) references orders (reference);
