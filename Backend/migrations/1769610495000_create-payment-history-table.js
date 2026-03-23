exports.up = (pgm) => {
    pgm.createTable('payment_history', {
        id: {
            type: 'serial',
            primaryKey: true,
        },
        owner_id: {
            type: 'integer',
            notNull: true,
            references: {
                relation: 'users',
                column: 'id',
                onDelete: 'CASCADE',
            },
        },
        tenant_id: {
            type: 'integer',
            references: {
                relation: 'users',
                column: 'id',
                onDelete: 'SET NULL',
            },
        },
        reservation_id: {
            type: 'integer',
            references: {
                relation: 'reservations',
                column: 'id',
                onDelete: 'CASCADE',
            },
        },
        tenant_name: {
            type: 'varchar(255)',
            notNull: true,
        },
        dorm_name: {
            type: 'varchar(255)',
            notNull: true,
        },
        amount: {
            type: 'decimal(10, 2)',
            notNull: true,
        },
        payment_source: {
            type: 'varchar(50)',
            notNull: true,
            check: "payment_source IN ('monthly', 'advance', 'deposit')",
        },
        payment_number: {
            type: 'integer',
        },
        payment_date: {
            type: 'timestamp',
            notNull: true,
            default: pgm.func('CURRENT_TIMESTAMP'),
        },
        status: {
            type: 'varchar(50)',
            notNull: true,
            default: 'paid',
            check: "status IN ('paid', 'pending', 'overdue')",
        },
        created_at: {
            type: 'timestamp',
            notNull: true,
            default: pgm.func('CURRENT_TIMESTAMP'),
        },
    });

    pgm.createIndex('payment_history', 'owner_id');
    pgm.createIndex('payment_history', 'tenant_id');
    pgm.createIndex('payment_history', 'reservation_id');
    pgm.createIndex('payment_history', 'payment_date');
    pgm.createIndex('payment_history', 'status');
};

exports.down = (pgm) => {
    pgm.dropTable('payment_history');
};