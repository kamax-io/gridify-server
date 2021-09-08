ALTER TABLE channels ADD COLUMN type text;
UPDATE channels SET type = 'r' WHERE network = 'matrix';
ALTER TABLE channels ALTER COLUMN type SET NOT NULL;

ALTER TABLE channel_event_stream ADD COLUMN type text NOT NULL DEFAULT 'm:r', ADD COLUMN scope text NOT NULL DEFAULT '';
ALTER TABLE channel_event_stream ALTER COLUMN type DROP DEFAULT, ALTER COLUMN scope DROP DEFAULT;
ALTER TABLE channel_event_stream ALTER COLUMN sid TYPE bigint;
ALTER TABLE channel_event_stream ADD CONSTRAINT ces_sid_unique UNIQUE(type, scope, sid);
ALTER SEQUENCE channel_event_stream_sid_seq RENAME TO "seq_channel_event_stream_sid_bTpy";
