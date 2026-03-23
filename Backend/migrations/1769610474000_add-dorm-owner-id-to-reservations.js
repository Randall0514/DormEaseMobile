/** @type {import('node-pg-migrate').ColumnDefinitions | undefined} */
const shorthands = undefined;

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const up = (pgm) => {
    // Use raw SQL to ensure IF NOT EXISTS semantics
    pgm.sql(`ALTER TABLE reservations ADD COLUMN IF NOT EXISTS dorm_owner_id integer`);
    pgm.createIndex('reservations', 'dorm_owner_id');
};

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const down = (pgm) => {
    pgm.sql(`ALTER TABLE reservations DROP COLUMN IF EXISTS dorm_owner_id`);
};

module.exports = {
    shorthands,
    up,
    down
};