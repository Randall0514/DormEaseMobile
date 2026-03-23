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
        const before = await pool.query(`
      SELECT COUNT(*) FILTER (WHERE tenant_email IS NULL OR trim(tenant_email) = '') AS missing
      FROM reservations
    `);

        const updatedFromUsers = await pool.query(`
      UPDATE reservations r
      SET tenant_email = lower(trim(u.email))
      FROM users u
      WHERE (r.tenant_email IS NULL OR trim(r.tenant_email) = '')
        AND lower(trim(u.full_name)) = lower(trim(r.full_name))
      RETURNING r.id
    `);

        const updatedFromReservations = await pool.query(`
      UPDATE reservations r
      SET tenant_email = src.tenant_email
      FROM (
        SELECT
          target.id,
          lower(trim(source.tenant_email)) AS tenant_email
        FROM reservations target
        JOIN LATERAL (
          SELECT rr.tenant_email
          FROM reservations rr
          WHERE rr.id <> target.id
            AND rr.tenant_email IS NOT NULL
            AND trim(rr.tenant_email) <> ''
            AND (
              lower(trim(rr.full_name)) = lower(trim(target.full_name))
              OR trim(rr.phone) = trim(target.phone)
            )
          ORDER BY rr.id DESC
          LIMIT 1
        ) source ON true
        WHERE target.tenant_email IS NULL OR trim(target.tenant_email) = ''
      ) src
      WHERE r.id = src.id
      RETURNING r.id
    `);

        const after = await pool.query(`
      SELECT COUNT(*) FILTER (WHERE tenant_email IS NULL OR trim(tenant_email) = '') AS missing
      FROM reservations
    `);

        console.log('Backfill completed');
        console.log({
            missingBefore: Number(before.rows[0].missing),
            updatedFromUsers: updatedFromUsers.rowCount,
            updatedFromReservations: updatedFromReservations.rowCount,
            missingAfter: Number(after.rows[0].missing),
        });
    } finally {
        await pool.end();
    }
}

main().catch((err) => {
    console.error('Backfill failed:', err.message);
    process.exit(1);
});