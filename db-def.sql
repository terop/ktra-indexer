
-- Episodes table
CREATE TABLE episodes (
       ep_id SERIAL PRIMARY KEY,
       number INTEGER NOT NULL UNIQUE,
       name VARCHAR(100) NOT NULL UNIQUE,
       date DATE NOT NULL
);

-- Artists table
CREATE TABLE artists (
       artist_id SERIAL PRIMARY KEY,
       name VARCHAR(100) NOT NULL UNIQUE
);

-- Tracks table
CREATE TABLE tracks (
       track_id SERIAL PRIMARY KEY,
       artist_id INTEGER REFERENCES artists (artist_id),
       name VARCHAR(100) NOT NULL UNIQUE
);

-- Features table
CREATE TABLE features (
       feature_id INTEGER PRIMARY KEY,
       name VARCHAR(50) NOT NULL UNIQUE
);
INSERT INTO features (feature_id, name) VALUES (1, 'Does It Sound Good At 170?');
INSERT INTO features (feature_id, name) VALUES (2, 'The Hardest Record In The World');
INSERT INTO features (feature_id, name) VALUES (3, 'Sample Mania');
INSERT INTO features (feature_id, name) VALUES (4, 'Guest Mix');
INSERT INTO features (feature_id, name) VALUES (5, 'Final Vinyl');

-- Tracks per episode
CREATE TABLE episode_tracks (
       ep_tr_id SERIAL PRIMARY KEY,
       ep_id INTEGER REFERENCES episodes (ep_id),
       track_id INTEGER REFERENCES tracks (track_id),
       feature_id INTEGER
);

-- Users table
CREATE TABLE users (
       user_id SERIAL PRIMARY KEY,
       username VARCHAR(100) NOT NULL UNIQUE
);

-- Yubikey ID table
CREATE TABLE yubikeys (
    key_id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users (user_id),
    yubikey_id VARCHAR(32) NOT NULL UNIQUE
);
