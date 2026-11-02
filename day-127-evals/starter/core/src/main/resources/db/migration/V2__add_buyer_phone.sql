-- Day 108: tickets can carry the buyer's phone number (2547XXXXXXXX), for
-- reminders later. Gates that already ran V1 get the new column the next time
-- they open; their old tickets get '' (no phone).

alter table tickets add column buyer_phone text not null default '';
