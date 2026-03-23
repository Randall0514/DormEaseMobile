// Migration: add deposit, advance, and utilities columns to dorms table

/** @type {import('node-pg-migrate').ColumnDefinitions | undefined} */
const shorthands = undefined;

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const up = (pgm) => {
    pgm.addColumn('dorms', {
        deposit: {
            type: 'varchar(50)',
        },
        advance: {
            type: 'varchar(50)',
        },
        water: {
            type: 'boolean',
            default: false,
        },
        electricity: {
            type: 'boolean',
            default: false,
        },
        gas: {
            type: 'boolean',
            default: false,
        },
    });
};

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const down = (pgm) => {
    pgm.dropColumn('dorms', 'deposit');
    pgm.dropColumn('dorms', 'advance');
    pgm.dropColumn('dorms', 'water');
    pgm.dropColumn('dorms', 'electricity');
    pgm.dropColumn('dorms', 'gas');
};

module.exports = {
    shorthands,
    up,
    down
};