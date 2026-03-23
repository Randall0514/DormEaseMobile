require('dotenv').config();
const nodemailer = require('nodemailer');

async function main() {
    const host = process.env.SMTP_HOST;
    const port = Number(process.env.SMTP_PORT || 587);
    const secure = process.env.SMTP_SECURE === 'true';
    const user = process.env.SMTP_USER;
    const pass = process.env.SMTP_PASS;
    const from = process.env.SMTP_FROM || user;
    const allowSelfSigned = process.env.SMTP_ALLOW_SELF_SIGNED === 'true';

    if (!host || !user || !pass) {
        throw new Error('Missing SMTP env values');
    }

    const transporter = nodemailer.createTransport({
        host,
        port,
        secure,
        auth: { user, pass },
        tls: allowSelfSigned ? { rejectUnauthorized: false } : undefined,
    });

    await transporter.verify();
    const info = await transporter.sendMail({
        from,
        to: user,
        subject: 'DormEase SMTP Smoke Test',
        text: 'SMTP smoke test from backend scripts/smtp_smoke_test.js',
    });

    console.log('SMTP smoke test succeeded');
    console.log('messageId:', info.messageId);
}

main().catch((err) => {
    console.error('SMTP smoke test failed:', err.message);
    process.exit(1);
});