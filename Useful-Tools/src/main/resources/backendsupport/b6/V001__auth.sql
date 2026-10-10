CREATE TABLE auth_schema_version(version INTEGER PRIMARY KEY CHECK(version=1));
INSERT INTO auth_schema_version(version) VALUES(1);
CREATE TABLE auth_users(
 id VARCHAR(36) PRIMARY KEY NOT NULL CHECK(length(id)=36),
 username VARCHAR(32) UNIQUE NOT NULL CHECK(length(username) BETWEEN 3 AND 32 AND username=lower(username)),
 password_hash VARCHAR(256) NOT NULL CHECK(length(password_hash) BETWEEN 50 AND 256),
 role VARCHAR(8) NOT NULL DEFAULT 'user' CHECK(role='user'),
 display_name VARCHAR(100) NOT NULL DEFAULT '' CHECK(length(display_name)<=100),
 preferences VARCHAR(8192) NOT NULL DEFAULT '{}' CHECK(length(preferences)<=8192)
);
