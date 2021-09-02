ALTER TABLE channel_events
    ADD COLUMN extra jsonb
;

ALTER TABLE channel_states
    ADD COLUMN trusted boolean NOT NULL DEFAULT false,
    ADD COLUMN complete boolean NOT NULL DEFAULT false,
    ADD COLUMN final boolean NOT NULL DEFAULT false
;
