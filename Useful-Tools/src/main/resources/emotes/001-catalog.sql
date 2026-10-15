CREATE TABLE emote_catalog_version(version TEXT PRIMARY KEY NOT NULL, source_manifest_hash TEXT NOT NULL CHECK(length(source_manifest_hash)=64), imported_at TEXT NOT NULL, state TEXT NOT NULL CHECK(state IN ('staged','validated','retired')));
-- statement
CREATE TABLE emote_catalog_active(singleton_key INTEGER PRIMARY KEY CHECK(singleton_key=1), version TEXT NOT NULL REFERENCES emote_catalog_version(version));
-- statement
CREATE TABLE emote_category(version TEXT NOT NULL REFERENCES emote_catalog_version(version), kind TEXT NOT NULL CHECK(kind IN ('emoji','ascii','kaomoji','symbol')), category_id TEXT NOT NULL, parent_id TEXT, label TEXT NOT NULL, sort_order INTEGER NOT NULL CHECK(sort_order>=0), PRIMARY KEY(version,kind,category_id), FOREIGN KEY(version,kind,parent_id) REFERENCES emote_category(version,kind,category_id));
-- statement
CREATE TABLE emote_catalog_entry(version TEXT NOT NULL, entry_id TEXT NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('emoji','ascii','kaomoji','symbol')), category_id TEXT NOT NULL, subgroup TEXT, name TEXT NOT NULL COLLATE BINARY, value TEXT NOT NULL COLLATE BINARY, codepoints_json TEXT NOT NULL, qualification TEXT, emoji_version TEXT, sort_order INTEGER NOT NULL CHECK(sort_order>=0), PRIMARY KEY(version,entry_id), FOREIGN KEY(version,kind,category_id) REFERENCES emote_category(version,kind,category_id), CHECK((kind='emoji' AND subgroup IS NOT NULL AND qualification IS NOT NULL AND qualification IN ('fully-qualified','component','minimally-qualified','unqualified') AND emoji_version IS NOT NULL) OR (kind<>'emoji' AND subgroup IS NULL AND qualification IS NULL AND emoji_version IS NULL)));
-- statement
CREATE TABLE emote_catalog_tag(version TEXT NOT NULL, entry_id TEXT NOT NULL, ordinal INTEGER NOT NULL CHECK(ordinal>=0), tag TEXT NOT NULL, PRIMARY KEY(version,entry_id,ordinal), FOREIGN KEY(version,entry_id) REFERENCES emote_catalog_entry(version,entry_id));
-- statement
CREATE INDEX emote_catalog_page ON emote_catalog_entry(version,kind,category_id,sort_order,entry_id);
-- statement
CREATE INDEX emote_catalog_qualification ON emote_catalog_entry(version,kind,qualification);
-- statement
CREATE TRIGGER emote_active_insert BEFORE INSERT ON emote_catalog_active WHEN NOT EXISTS(SELECT 1 FROM emote_catalog_version WHERE version=NEW.version AND state='validated') BEGIN SELECT RAISE(ABORT,'Catalog not validated'); END;
-- statement
CREATE TRIGGER emote_active_update BEFORE UPDATE ON emote_catalog_active WHEN NOT EXISTS(SELECT 1 FROM emote_catalog_version WHERE version=NEW.version AND state='validated') BEGIN SELECT RAISE(ABORT,'Catalog not validated'); END;
-- statement
CREATE TRIGGER emote_active_state BEFORE UPDATE OF state ON emote_catalog_version WHEN NEW.state<>'validated' AND EXISTS(SELECT 1 FROM emote_catalog_active WHERE version=OLD.version) BEGIN SELECT RAISE(ABORT,'Active catalog must remain validated'); END;
