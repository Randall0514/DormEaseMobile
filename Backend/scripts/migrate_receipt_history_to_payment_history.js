/**
 * Migration Script: Sync existing receipt_history data to payment_history table
 * 
 * This script extracts all payment receipts from the reservations.receipt_history 
 * JSONB column and creates corresponding entries in the payment_history table.
 * 
 * Run with: node scripts/migrate_receipt_history_to_payment_history.js
 */

const { Pool } = require('pg');
require('dotenv').config();

const pool = new Pool({
    host: process.env.DB_HOST || 'localhost',
    port: Number(process.env.DB_PORT) || 5432,
    user: process.env.DB_USER || 'postgres',
    password: process.env.DB_PASSWORD || 'postgres',
    database: process.env.DB_NAME || 'dormease',
});

async function migrateReceiptHistoryToPaymentHistory() {
    const client = await pool.connect();

    try {
        console.log('🔄 Starting migration of receipt_history to payment_history...\n');

        // Get all reservations with receipt history
        const reservationsResult = await client.query(`
      SELECT 
        r.id as reservation_id,
        r.dorm_owner_id,
        r.full_name as tenant_name,
        r.dorm_name,
        r.receipt_history,
        r.price_per_month,
        r.advance,
        r.deposit,
        r.tenant_email,
        u.id as tenant_user_id
      FROM reservations r
      LEFT JOIN users u ON lower(trim(u.email)) = lower(trim(r.tenant_email))
      WHERE r.receipt_history IS NOT NULL 
        AND jsonb_array_length(r.receipt_history) > 0
      ORDER BY r.id
    `);

        console.log(`📋 Found ${reservationsResult.rows.length} reservations with receipt history\n`);

        let totalReceipts = 0;
        let insertedReceipts = 0;
        let skippedReceipts = 0;

        for (const reservation of reservationsResult.rows) {
            const receipts = reservation.receipt_history || [];

            console.log(`Processing reservation #${reservation.reservation_id} - ${reservation.tenant_name}`);
            console.log(`  Owner ID: ${reservation.dorm_owner_id}`);
            console.log(`  Tenant User ID: ${reservation.tenant_user_id || 'N/A'}`);
            console.log(`  Dorm: ${reservation.dorm_name}`);
            console.log(`  Receipts: ${receipts.length}`);

            for (const receipt of receipts) {
                totalReceipts++;

                const paymentNumber = receipt.paymentNumber;
                const paymentSource = receipt.paymentSource || 'monthly';
                const paymentDate = receipt.paymentDate ? new Date(receipt.paymentDate) : new Date();

                // Determine the amount based on payment source
                let amount = receipt.amountPaid;
                if (!amount) {
                    if (paymentSource === 'advance') {
                        amount = reservation.advance || 0;
                    } else if (paymentSource === 'deposit') {
                        amount = reservation.deposit || 0;
                    } else {
                        amount = reservation.price_per_month || 0;
                    }
                }

                // Check if this payment already exists in payment_history
                const existingCheck = await client.query(`
          SELECT id FROM payment_history
          WHERE owner_id = $1
            AND reservation_id = $2
            AND payment_number = $3
            AND payment_source = $4
          LIMIT 1
        `, [
                    reservation.dorm_owner_id,
                    reservation.reservation_id,
                    paymentNumber,
                    paymentSource
                ]);

                if (existingCheck.rows.length > 0) {
                    console.log(`  ⏭️  Skipping payment #${paymentNumber} (${paymentSource}) - already exists`);
                    skippedReceipts++;
                    continue;
                }

                // Insert into payment_history
                try {
                    await client.query(`
            INSERT INTO payment_history 
              (owner_id, tenant_id, reservation_id, tenant_name, dorm_name, 
               amount, payment_source, payment_number, payment_date, status)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
          `, [
                        reservation.dorm_owner_id,
                        reservation.tenant_user_id || null,
                        reservation.reservation_id,
                        reservation.tenant_name,
                        reservation.dorm_name,
                        amount,
                        paymentSource,
                        paymentNumber,
                        paymentDate,
                        'paid'
                    ]);

                    insertedReceipts++;
                    console.log(`  ✅ Inserted payment #${paymentNumber} (${paymentSource}) - ₱${amount}`);
                } catch (insertErr) {
                    console.error(`  ❌ Failed to insert payment #${paymentNumber}:`, insertErr.message);
                }
            }

            console.log('');
        }

        console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
        console.log('✨ Migration Complete!');
        console.log(`📊 Total receipts found: ${totalReceipts}`);
        console.log(`✅ Receipts inserted: ${insertedReceipts}`);
        console.log(`⏭️  Receipts skipped (duplicates): ${skippedReceipts}`);
        console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

    } catch (err) {
        console.error('❌ Migration error:', err);
        throw err;
    } finally {
        client.release();
        await pool.end();
    }
}

// Run the migration
migrateReceiptHistoryToPaymentHistory()
    .then(() => {
        console.log('✅ Script completed successfully');
        process.exit(0);
    })
    .catch((err) => {
        console.error('❌ Script failed:', err);
        process.exit(1);
    });