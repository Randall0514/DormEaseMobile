/** @type {import('node-pg-migrate').ColumnDefinitions | undefined} */
const shorthands = undefined;

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const up = (pgm) => {
    pgm.addColumns('reservations', {
        tenant_action: { type: 'text', default: null }, // 'accepted' | 'cancelled' | NULL
        cancel_reason: { type: 'text', default: null }, // filled when tenant cancels
        tenant_action_at: { type: 'timestamptz', default: null } // when the tenant acted
    });
};

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const down = (pgm) => {
    pgm.dropColumns('reservations', ['tenant_action', 'cancel_reason', 'tenant_action_at']);
};

module.exports = {
    shorthands,
    up,
    down
};