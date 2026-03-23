require('dotenv').config();

/** @type {import('node-pg-migrate').Config} */
module.exports = {
    databaseUrl: process.env.DATABASE_URL, // <- MUST be a single connection string
    dir: 'migrations',
    migrationsTable: 'pgmigrations',
    logFileName: 'migrate.log', // optional
};