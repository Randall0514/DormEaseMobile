// Migration: add 'platform' column to users table

exports.up = function(pgm) {
    pgm.addColumn('users', {
        platform: {
            type: 'varchar(10)',
            notNull: true,
            default: 'web', // existing users default to web
        },
    });
};

exports.down = function(pgm) {
    pgm.dropColumn('users', 'platform');
};