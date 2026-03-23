/** @type {import('node-pg-migrate').ColumnDefinitions | undefined} */
const shorthands = undefined;

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const up = (pgm) => {
    pgm.addColumn('reservations', {
        payments_paid: {
            type: 'integer',
            notNull: true,
            default: 0,
            comment: 'Number of payments that have been marked as paid'
        }
    });
};

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const down = (pgm) => {
    pgm.dropColumn('reservations', 'payments_paid');
};

module.exports = {
    shorthands,
    up,
    down
};