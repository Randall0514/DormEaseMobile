// Quick SMTP Test
require('dotenv').config();
const nodemailer = require('nodemailer');

const transporter = nodemailer.createTransport({
    host: process.env.SMTP_HOST,
    port: Number(process.env.SMTP_PORT),
    secure: process.env.SMTP_SECURE === 'true',
    auth: {
        user: process.env.SMTP_USER,
        pass: process.env.SMTP_PASS,
    },
});

async function testEmail() {
    console.log('Testing SMTP connection...');
    console.log('SMTP Config:', {
        host: process.env.SMTP_HOST,
        port: process.env.SMTP_PORT,
        user: process.env.SMTP_USER,
        from: process.env.SMTP_FROM,
    });

    try {
        // Verify connection
        await transporter.verify();
        console.log('✅ SMTP connection successful!');

        // Send test email - CHANGE THIS TO YOUR EMAIL!
        const testEmail = 'YOUR_EMAIL@gmail.com'; // ← PUT YOUR EMAIL HERE

        const info = await transporter.sendMail({
            from: process.env.SMTP_FROM,
            to: testEmail,
            subject: 'DormEase Payment Confirmation - TEST',
            text: 'This is a test email from DormEase backend.',
            html: '<p>This is a <strong>test email</strong> from DormEase backend.</p>',
        });

        console.log('✅ Test email sent successfully!');
        console.log('Message ID:', info.messageId);
        console.log(`Check ${testEmail} inbox!`);
    } catch (error) {
        console.error('❌ SMTP Error:', error.message);
        console.error('Full error:', error);
    }
}

testEmail();