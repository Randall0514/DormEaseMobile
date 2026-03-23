// Migration: consolidate utilities into a single column

/** @type {import('node-pg-migrate').ColumnDefinitions | undefined} */
const shorthands = undefined;

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const up = (pgm) => {
    // Add new utilities column as text array
    pgm.addColumn('dorms', {
        utilities: {
            type: 'text[]',
            default: pgm.func("'{}'::text[]"),
        },
    });

    // Copy data from separate columns to the new utilities column
    pgm.sql(`
        UPDATE dorms 
        SET utilities = ARRAY_REMOVE(ARRAY[
            CASE WHEN water = true THEN 'water' ELSE NULL END,
            CASE WHEN electricity = true THEN 'electricity' ELSE NULL END,
            CASE WHEN gas = true THEN 'gas' ELSE NULL END
        ], NULL)
    `);

    // Drop the old separate columns
    pgm.dropColumn('dorms', 'water');
    pgm.dropColumn('dorms', 'electricity');
    pgm.dropColumn('dorms', 'gas');
};

/** @param {import('node-pg-migrate').MigrationBuilder} pgm */
const down = (pgm) => {
    // Add back the separate columns
    pgm.addColumn('dorms', {
        water: {
            type: 'boolean',
            default: false,
        },
        electricity: {
            type: 'boolean',
            default: false,
        },
        gas: {
            type: 'boolean',
            default: false,
        },
    });

    // Copy data back from utilities column
    pgm.sql(`
        UPDATE dorms 
        SET 
            water = 'water' = ANY(utilities),
            electricity = 'electricity' = ANY(utilities),
            gas = 'gas' = ANY(utilities)
    `);

    // Drop the utilities column
    pgm.dropColumn('dorms', 'utilities');
};

module.exports = {
    shorthands,
    up,
    down
};