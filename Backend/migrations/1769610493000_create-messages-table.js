/** @type {import('node-pg-migrate').ColumnDefinitions | undefined} */
const shorthands = undefined;

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const up = (pgm) => {
    pgm.createTable('messages', {
        id: 'id',
        sender_id: {
            type: 'integer',
            notNull: true,
            references: 'users(id)',
            onDelete: 'CASCADE',
        },
        recipient_id: {
            type: 'integer',
            notNull: true,
            references: 'users(id)',
            onDelete: 'CASCADE',
        },
        message: { type: 'text', notNull: true },
        sender_deleted_at: { type: 'timestamptz' },
        recipient_deleted_at: { type: 'timestamptz' },
        created_at: { type: 'timestamp', default: pgm.func('current_timestamp') },
    });

    pgm.createIndex('messages', ['sender_id', 'recipient_id', 'created_at']);
    pgm.createIndex('messages', ['recipient_id', 'sender_id', 'created_at']);
};

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const down = (pgm) => {
    pgm.dropTable('messages');
};

module.exports = {
    shorthands,
    up,
    down,
};