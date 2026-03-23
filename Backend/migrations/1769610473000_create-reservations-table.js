/** @type {import('node-pg-migrate').ColumnDefinitions | undefined} */
const shorthands = undefined;

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const up = (pgm) => {
    pgm.createTable('reservations', {
        id: 'id',
        dorm_name: { type: 'varchar(255)', notNull: true },
        location: { type: 'varchar(255)' },
        full_name: { type: 'varchar(255)', notNull: true },
        phone: { type: 'varchar(20)', notNull: true },
        move_in_date: { type: 'varchar(20)', notNull: true },
        duration_months: { type: 'integer', notNull: true },
        price_per_month: { type: 'integer', notNull: true, default: 0 },
        deposit: { type: 'integer', notNull: true, default: 0 },
        advance: { type: 'integer', notNull: true, default: 0 },
        total_amount: { type: 'integer', notNull: true, default: 0 },
        notes: { type: 'text' },
        payment_method: { type: 'varchar(50)', notNull: true, default: 'cash_on_move_in' },
        status: { type: 'varchar(20)', notNull: true, default: 'pending' },
        created_at: { type: 'timestamp', default: pgm.func('current_timestamp') }
    });
    pgm.createIndex('reservations', 'created_at');
};

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const down = (pgm) => {
    pgm.dropTable('reservations');
};

module.exports = {
    shorthands,
    up,
    down
};