/** @type {import('node-pg-migrate').ColumnDefinitions | undefined} */
const shorthands = undefined;

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const up = (pgm) => {
    pgm.sql("ALTER TABLE reservations ADD COLUMN IF NOT EXISTS tenant_email varchar(255);");
    pgm.sql("ALTER TABLE reservations ADD COLUMN IF NOT EXISTS receipt_history jsonb NOT NULL DEFAULT '[]'::jsonb;");
};

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const down = (pgm) => {
    pgm.sql("ALTER TABLE reservations DROP COLUMN IF EXISTS receipt_history;");
    pgm.sql("ALTER TABLE reservations DROP COLUMN IF EXISTS tenant_email;");
};

module.exports = {
    shorthands,
    up,
    down,
};