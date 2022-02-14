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
CREATE INDEX artists_name_lower_idx ON artists (lower(name));

-- Tracks table
CREATE TABLE tracks (
       track_id SERIAL PRIMARY KEY,
       artist_id INTEGER REFERENCES artists (artist_id),
       name TEXT NOT NULL
);
CREATE INDEX tracks_name_idx ON tracks USING gin(to_tsvector('english', name));
CREATE INDEX tracks_name_lower_idx ON tracks (lower(name));

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
       ep_id INTEGER REFERENCES episodes (ep_id) ON DELETE CASCADE,
       track_id INTEGER REFERENCES tracks (track_id) ON DELETE CASCADE,
       feature_id INTEGER
);

-- Users table
CREATE TABLE users (
       user_id SERIAL PRIMARY KEY,
       username VARCHAR(100) NOT NULL UNIQUE
);

-- WebAuthn authenticator table
CREATE TABLE webauthn_authenticators (
       authn_id SERIAL PRIMARY KEY,
       user_id INTEGER REFERENCES users (user_id) ON DELETE CASCADE,
       name VARCHAR(40),
       counter INTEGER NOT NULL,
       attested_credential VARCHAR(500) NOT NULL,
       attestation_statement VARCHAR(2000) NOT NULL,
       login_count INTEGER NOT NULL
);
