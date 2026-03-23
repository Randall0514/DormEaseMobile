// Migration: create dorms table (one per user - setup form data)

/** @type {import('node-pg-migrate').ColumnDefinitions | undefined} */
const shorthands = undefined;

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const up = (pgm) => {
    pgm.createTable('dorms', {
        id: 'id',
        user_id: {
            type: 'integer',
            notNull: true,
            unique: true,
            references: 'users',
            onDelete: 'CASCADE',
        },
        dorm_name: {
            type: 'varchar(150)',
            notNull: true
        },
        email: {
            type: 'varchar(100)',
            notNull: true
        },
        phone: {
            type: 'varchar(20)',
            notNull: true
        },
        price: {
            type: 'varchar(50)',
            notNull: true
        },
        address: {
            type: 'varchar(255)',
            notNull: true
        },
        room_capacity: {
            type: 'integer',
            notNull: true
        },
        created_at: {
            type: 'timestamp',
            default: pgm.func('current_timestamp')
        },
        updated_at: {
            type: 'timestamp',
            default: pgm.func('current_timestamp')
        },
    });
    pgm.createIndex('dorms', 'user_id');
};

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const down = (pgm) => {
    pgm.dropTable('dorms');
};

module.exports = {
    shorthands,
    up,
    down
};