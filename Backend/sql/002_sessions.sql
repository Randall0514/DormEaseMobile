-- Session tokens for existing and future accounts.
-- Run after 001_users.sql. Or use: npx node-pg-migrate up

CREATE TABLE IF NOT EXISTS sessions (
  id         SERIAL PRIMARY KEY,
  user_id    INTEGER      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token      VARCHAR(64)  NOT NULL UNIQUE,
  expires_at TIMESTAMP    NOT NULL,
  created_at TIMESTAMP    NOT NULL DEFAULT current_timestamp
);

CREATE INDEX IF NOT EXISTS idx_sessions_token ON sessions (token);
CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_expires_at ON sessions (expires_at);

COMMENT ON TABLE sessions IS 'Session tokens issued on login/signup; validated by /auth/me and invalidated by /auth/logout.';
