require('dotenv').config();
const { Pool } = require('pg');

async function main() {
    const pool = new Pool({
        host: process.env.DB_HOST,
        port: Number(process.env.DB_PORT),
        user: process.env.DB_USER,
        password: process.env.DB_PASSWORD,
        database: process.env.DB_NAME,
    });

    try {
        const stats = await pool.query(`
      SELECT
        COUNT(*) AS total,
        COUNT(*) FILTER (WHERE tenant_email IS NULL OR trim(tenant_email) = '') AS missing_email,
        COUNT(*) FILTER (WHERE tenant_email IS NOT NULL AND trim(tenant_email) <> '') AS has_email
      FROM reservations
    `);

        console.log('Reservation email stats:');
        console.table(stats.rows);

        const rows = await pool.query(`
      SELECT
        r.id,
        r.full_name,
        r.tenant_email,
        r.payments_paid,
        r.status,
        u.email AS matched_user_email
      FROM reservations r
      LEFT JOIN LATERAL (
        SELECT email
        FROM users u
        WHERE lower(trim(u.full_name)) = lower(trim(r.full_name))
        ORDER BY u.id DESC
        LIMIT 1
      ) u ON true
      ORDER BY r.id DESC
      LIMIT 25
    `);

        console.log('Recent reservations and resolved emails:');
        console.table(rows.rows);
    } finally {
        await pool.end();
    }
}

main().catch((err) => {
    console.error('Inspection failed:', err.message);
    process.exit(1);
});