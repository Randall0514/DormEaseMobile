const fs = require('fs');
const { Client } = require('pg');
require('dotenv').config();

async function apply(filePath) {
    const sql = fs.readFileSync(filePath, 'utf8');
    const clientConfig = process.env.DATABASE_URL ?
        { connectionString: process.env.DATABASE_URL } :
        {
            host: process.env.DB_HOST,
            port: Number(process.env.DB_PORT || 5432),
            user: process.env.DB_USER,
            password: process.env.DB_PASSWORD,
            database: process.env.DB_NAME,
        };

    const client = new Client(clientConfig);

    await client.connect();
    try {
        await client.query('BEGIN');
        await client.query(sql);
        await client.query('COMMIT');
        console.log('Migration applied successfully');
    } catch (err) {
        await client.query('ROLLBACK');
        console.error('Error applying migration:', err.message || err);
        process.exitCode = 1;
    } finally {
        await client.end();
    }
}

const arg = process.argv[2];
if (!arg) {
    console.error('Usage: node apply_sql.js <path-to-sql-file>');
    process.exit(1);
}

apply(arg).catch(err => {
    console.error(err);
    process.exit(1);
});