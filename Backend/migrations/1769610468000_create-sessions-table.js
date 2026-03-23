// Migration: create sessions table for session tokens

/** @type {import('node-pg-migrate').ColumnDefinitions | undefined} */
const shorthands = undefined;

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const up = (pgm) => {
    pgm.createTable('sessions', {
        id: 'id',
        user_id: {
            type: 'integer',
            notNull: true,
            references: 'users',
            onDelete: 'CASCADE',
        },
        token: {
            type: 'varchar(64)',
            notNull: true,
            unique: true,
        },
        expires_at: {
            type: 'timestamp',
            notNull: true,
        },
        created_at: {
            type: 'timestamp',
            default: pgm.func('current_timestamp'),
        },
    });
    pgm.createIndex('sessions', 'token');
    pgm.createIndex('sessions', 'user_id');
    pgm.createIndex('sessions', 'expires_at');
};

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const down = (pgm) => {
    pgm.dropTable('sessions');
};

module.exports = {
    shorthands,
    up,
    down
};