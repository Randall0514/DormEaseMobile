/** @type {import('node-pg-migrate').ColumnDefinitions | undefined} */
const shorthands = undefined;

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const up = (pgm) => {
    pgm.addColumn('reservations', {
        advance_used: {
            type: 'boolean',
            notNull: true,
            default: false,
            comment: 'Whether the advance payment has been used for a monthly payment'
        },
        deposit_used: {
            type: 'boolean',
            notNull: true,
            default: false,
            comment: 'Whether the deposit has been used for a monthly payment'
        }
    });
};

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const down = (pgm) => {
    pgm.dropColumn('reservations', 'advance_used');
    pgm.dropColumn('reservations', 'deposit_used');
};

module.exports = {
    shorthands,
    up,
    down
};