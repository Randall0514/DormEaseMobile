// migrations/1769610466008_create-users-table.js

/** @type {import('node-pg-migrate').ColumnDefinitions | undefined} */
const shorthands = undefined;

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const up = (pgm) => {
    pgm.createTable('users', {
        id: 'id', // SERIAL PRIMARY KEY
        full_name: {
            type: 'varchar(100)',
            notNull: true
        },
        username: {
            type: 'varchar(50)',
            notNull: true,
            unique: true
        },
        email: {
            type: 'varchar(100)',
            notNull: true,
            unique: true
        },
        password: {
            type: 'varchar(255)',
            notNull: true
        },
        created_at: {
            type: 'timestamp',
            default: pgm.func('current_timestamp')
        },
    });
};

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const down = (pgm) => {
    pgm.dropTable('users');
};

module.exports = {
    shorthands,
    up,
    down
};