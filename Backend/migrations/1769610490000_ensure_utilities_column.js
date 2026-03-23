/** Migration: ensure utilities column exists on dorms table (text[] default empty) */

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const up = (pgm) => {
    // Add utilities column if it doesn't exist
    pgm.sql(`ALTER TABLE dorms ADD COLUMN IF NOT EXISTS utilities text[] DEFAULT '{}'::text[]`);
};

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const down = (pgm) => {
    pgm.sql(`ALTER TABLE dorms DROP COLUMN IF EXISTS utilities`);
};

module.exports = { up, down };