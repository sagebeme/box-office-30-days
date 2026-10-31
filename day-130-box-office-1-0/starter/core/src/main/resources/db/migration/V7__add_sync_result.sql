-- Day 120: what the sync server said about each sale. Empty while waiting,
-- 'accepted' once the server has it, 'oversold' when another gate got the
-- last place first: then staff need to sort it out with the buyer.
alter table outbox add column sync_result text;
