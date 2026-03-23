// Migration: add photo_urls to dorms (array of relative URLs for uploaded photos)

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const up = (pgm) => {
    pgm.addColumn('dorms', {
        photo_urls: {
            type: 'jsonb',
            notNull: false,
            default: null,
        },
    });
};

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const down = (pgm) => {
    pgm.dropColumn('dorms', 'photo_urls');
};

module.exports = {
    up,
    down
};