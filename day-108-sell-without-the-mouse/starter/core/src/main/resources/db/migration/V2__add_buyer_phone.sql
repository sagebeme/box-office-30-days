-- Day 108: tickets can carry the buyer's phone number (2547XXXXXXXX), for
-- reminders later. Gates that already ran V1 get the new column the next time
-- they open; their old tickets get '' (no phone).

-- TODO: add a column called buyer_phone to the tickets table.
--       It's text, it can't be null, and it defaults to '' so old tickets get an empty phone.
--       The SQL starts: alter table tickets add column ...
