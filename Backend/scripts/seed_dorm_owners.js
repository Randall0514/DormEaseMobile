require('dotenv').config();
const bcrypt = require('bcryptjs');
const { Pool } = require('pg');

const owners = [{
        fullName: 'Maria Teresa Santos',
        email: 'mariateresasantos.dorm@gmail.com',
        username: 'maria.santos.owner',
        password: 'D0rmEase!Maria917',
        phoneNumber: '+63 917 345 1298',
        platform: 'web',
        dorm: {
            dormName: 'Santos Boarding House',
            price: '2500',
            phone: '9173451298',
            address: 'Tapuac District, Dagupan City',
            roomCapacity: 3,
            utilities: ['WiFi', 'Water', 'Electricity', 'Bed Frame', 'Kitchen'],
        },
    },
    {
        fullName: 'Jonathan Reyes',
        email: 'jonathanreyes.dorm@gmail.com',
        username: 'jonathan.reyes.owner',
        password: 'D0rmEase!Reyes928',
        phoneNumber: '+63 928 564 7821',
        platform: 'web',
        dorm: {
            dormName: 'Reyes Student Dormitory',
            price: '2000',
            phone: '9285647821',
            address: 'Bonuan Boquig, Dagupan City',
            roomCapacity: 2,
            utilities: ['WiFi', 'Water', 'Bed Frame', 'Foam', 'Visitors Allowed'],
        },
    },
    {
        fullName: 'Angela Dela Cruz',
        email: 'angeladelacruz.dorm@gmail.com',
        username: 'angela.delacruz.owner',
        password: 'D0rmEase!ADC945',
        phoneNumber: '+63 945 778 2234',
        platform: 'web',
        dorm: {
            dormName: 'ADC Dormitel',
            price: '3000',
            phone: '9457782234',
            address: 'Mangin Street, Dagupan City',
            roomCapacity: 4,
            utilities: ['WiFi', 'Water', 'Electricity', 'Kitchen', 'Comfort Room'],
        },
    },
    {
        fullName: 'Carlo Mendoza',
        email: 'carlomendoza.dorm@gmail.com',
        username: 'carlo.mendoza.owner',
        password: 'D0rmEase!Carlo918',
        phoneNumber: '+63 918 672 5519',
        platform: 'web',
        dorm: {
            dormName: 'CM Student Residence',
            price: '2800',
            phone: '9186725519',
            address: 'Mayombo District, Dagupan City',
            roomCapacity: 2,
            utilities: ['WiFi', 'Electricity', 'Bed Frame', 'Foam', 'Visitors Allowed'],
        },
    },
    {
        fullName: 'Patricia Villanueva',
        email: 'patriciavillanueva.dorm@gmail.com',
        username: 'patricia.villanueva.owner',
        password: 'D0rmEase!PV926',
        phoneNumber: '+63 926 341 8875',
        platform: 'web',
        dorm: {
            dormName: 'PV Dormitory',
            price: '2700',
            phone: '9263418875',
            address: 'Lucao District, Dagupan City',
            roomCapacity: 3,
            utilities: ['WiFi', 'Water', 'Kitchen', 'Bed Frame', 'Comfort Room'],
        },
    },
];

function getPool() {
    if (process.env.DATABASE_URL) {
        return new Pool({ connectionString: process.env.DATABASE_URL });
    }

    return new Pool({
        host: process.env.DB_HOST,
        port: Number(process.env.DB_PORT || 5432),
        user: process.env.DB_USER,
        password: process.env.DB_PASSWORD,
        database: process.env.DB_NAME,
    });
}

async function upsertOwner(client, owner) {
    const hashedPassword = await bcrypt.hash(owner.password, 10);

    const userResult = await client.query(
        `INSERT INTO users (full_name, username, email, phone_number, password, platform)
     VALUES ($1, $2, $3, $4, $5, $6)
     ON CONFLICT (email) DO UPDATE SET
       full_name = EXCLUDED.full_name,
       username = EXCLUDED.username,
       phone_number = EXCLUDED.phone_number,
       password = EXCLUDED.password,
       platform = EXCLUDED.platform
     RETURNING id, email, username`, [
            owner.fullName,
            owner.username,
            owner.email.toLowerCase(),
            owner.phoneNumber,
            hashedPassword,
            owner.platform,
        ]
    );

    const userId = userResult.rows[0].id;

    await client.query(
        `INSERT INTO dorms (user_id, dorm_name, email, phone, price, address, room_capacity, utilities)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
     ON CONFLICT (user_id) DO UPDATE SET
       dorm_name = EXCLUDED.dorm_name,
       email = EXCLUDED.email,
       phone = EXCLUDED.phone,
       price = EXCLUDED.price,
       address = EXCLUDED.address,
       room_capacity = EXCLUDED.room_capacity,
       utilities = EXCLUDED.utilities,
       updated_at = current_timestamp`, [
            userId,
            owner.dorm.dormName,
            owner.email.toLowerCase(),
            owner.dorm.phone,
            owner.dorm.price,
            owner.dorm.address,
            owner.dorm.roomCapacity,
            owner.dorm.utilities,
        ]
    );

    return userResult.rows[0];
}

async function main() {
    const pool = getPool();
    const client = await pool.connect();

    try {
        await client.query('BEGIN');

        const inserted = [];
        for (const owner of owners) {
            const row = await upsertOwner(client, owner);
            inserted.push(`${row.email} (${row.username})`);
        }

        await client.query('COMMIT');
        console.log('Seed complete. Upserted dorm owner accounts:');
        for (const line of inserted) {
            console.log(`- ${line}`);
        }
    } catch (err) {
        await client.query('ROLLBACK');
        console.error('Seed failed:', err.message || err);
        process.exitCode = 1;
    } finally {
        client.release();
        await pool.end();
    }
}

main().catch((err) => {
    console.error('Unexpected seed error:', err.message || err);
    process.exit(1);
});