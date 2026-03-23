/** @type {import('node-pg-migrate').ColumnDefinitions | undefined} */
const shorthands = undefined;

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const up = (pgm) => {
    pgm.addColumn('reservations', {
        rejection_reason: { type: 'text' },
    });
};

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const down = (pgm) => {
    pgm.dropColumn('reservations', 'rejection_reason');
};

module.exports = {
    shorthands,
    up,
    down,
};