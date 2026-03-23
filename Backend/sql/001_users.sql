-- Table for signup form data (full name, username, email, password, platform).
-- Passwords are stored hashed (bcrypt) by the backend before insert.
-- Run with: psql -U postgres -d dormease_db -f sql/001_users.sql

CREATE TABLE IF NOT EXISTS users (
  id                SERIAL PRIMARY KEY,
  full_name         VARCHAR(100) NOT NULL,
  username          VARCHAR(50)  NOT NULL UNIQUE,
  email             VARCHAR(100) NOT NULL UNIQUE,
  phone_number      VARCHAR(20),
  password          VARCHAR(255) NOT NULL,
  platform          VARCHAR(10)  NOT NULL DEFAULT 'web',
  created_at        TIMESTAMP    NOT NULL DEFAULT current_timestamp
);

-- Optional: index for login lookups by username or email
CREATE INDEX IF NOT EXISTS idx_users_username ON users (username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);

COMMENT ON TABLE users IS 'Stores account data from the signup form (fullName, username, email, hashed password, platform).';
