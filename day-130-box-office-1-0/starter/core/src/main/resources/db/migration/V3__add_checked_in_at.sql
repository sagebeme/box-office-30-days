-- Day 110: when each ticket came through the gate. Empty (null) until it's
-- scanned, so a ticket can only be used once.

alter table tickets add column checked_in_at text;
