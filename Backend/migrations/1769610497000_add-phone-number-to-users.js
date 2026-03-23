/** @type {import('node-pg-migrate').ColumnDefinitions | undefined} */
const shorthands = undefined;

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const up = (pgm) => {
    pgm.addColumn('users', {
        phone_number: {
            type: 'varchar(20)',
            notNull: false,
        },
    });
};

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const down = (pgm) => {
    pgm.dropColumn('users', 'phone_number');
};

module.exports = {
    shorthands,
    up,
    down
};