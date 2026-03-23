import express from "express";
import * as dotenv from "dotenv";
import crypto from "crypto";
import bcrypt from "bcryptjs";
import cors from "cors";
import multer from "multer";
import nodemailer from "nodemailer";
import path from "path";
import fs from "fs";
import http from "http";
import { pool } from "./db";
import { initializeWebSocket, notifyUser } from "./websocket";

dotenv.config();

const UPLOAD_DIR = path.join(process.cwd(), "uploads");
const DORMS_PHOTOS_DIR = path.join(UPLOAD_DIR, "dorms");
if (!fs.existsSync(DORMS_PHOTOS_DIR)) {
  fs.mkdirSync(DORMS_PHOTOS_DIR, { recursive: true });
}

const SESSION_MINUTES = Number(process.env.SESSION_MINUTES) || 60 * 24 * 7;
const SIGNUP_OTP_TTL_MINUTES = Number(process.env.SIGNUP_OTP_TTL_MINUTES) || 10;
const SIGNUP_OTP_RESEND_SECONDS = Number(process.env.SIGNUP_OTP_RESEND_SECONDS) || 60;
const SIGNUP_OTP_MAX_ATTEMPTS = Number(process.env.SIGNUP_OTP_MAX_ATTEMPTS) || 5;
const CHANGE_OTP_TTL_MINUTES = Number(process.env.CHANGE_OTP_TTL_MINUTES) || 10;
const CHANGE_OTP_RESEND_SECONDS = Number(process.env.CHANGE_OTP_RESEND_SECONDS) || 60;
const CHANGE_OTP_MAX_ATTEMPTS = Number(process.env.CHANGE_OTP_MAX_ATTEMPTS) || 5;

type SignupOtpRecord = {
  code: string;
  expiresAt: number;
  attempts: number;
  lastSentAt: number;
};

type ChangeOtpRecord = {
  userId: number;
  code: string;
  expiresAt: number;
  attempts: number;
  lastSentAt: number;
};

type PaymentReceiptRecord = {
  paymentNumber: number;
  paymentSource: "monthly" | "advance" | "deposit";
  tenantName: string;
  dormitory: string;
  amountPaid: number;
  paymentDate: string;
  nextPaymentDueDate: string | null;
};

const signupOtpStore = new Map<string, SignupOtpRecord>();
const changeOtpStore = new Map<number, ChangeOtpRecord>();
let smtpTransporter: nodemailer.Transporter | null | undefined;

function normalizeEmail(email: string): string {
  return email.trim().toLowerCase();
}

function generateSignupOtpCode(): string {
  return Math.floor(100000 + Math.random() * 900000).toString();
}

function getSmtpTransporter(): nodemailer.Transporter | null {
  if (smtpTransporter !== undefined) {
    return smtpTransporter;
  }

  const host = process.env.SMTP_HOST;
  const port = Number(process.env.SMTP_PORT || 587);
  const user = process.env.SMTP_USER;
  const pass = process.env.SMTP_PASS;
  const secure = process.env.SMTP_SECURE === "true";
  const allowSelfSigned = process.env.SMTP_ALLOW_SELF_SIGNED === "true";

  if (!host || !user || !pass) {
    smtpTransporter = null;
    return smtpTransporter;
  }

  smtpTransporter = nodemailer.createTransport({
    host,
    port,
    secure,
    auth: {
      user,
      pass,
    },
    tls: allowSelfSigned ? { rejectUnauthorized: false } : undefined,
  });

  return smtpTransporter;
}

function parseReservationDate(value: string): Date | null {
  const input = String(value || "").trim();
  if (!input) return null;

  if (input.includes("/")) {
    const [day, month, year] = input.split("/").map(Number);
    if (!day || !month || !year) return null;
    const parsed = new Date(year, month - 1, day);
    return Number.isNaN(parsed.getTime()) ? null : parsed;
  }

  const parsed = new Date(input);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

function formatDateForEmail(date: Date): string {
  return date.toLocaleDateString("en-US", {
    year: "numeric",
    month: "long",
    day: "numeric",
  });
}

async function sendPaymentConfirmationEmail(params: {
  tenantEmail: string;
  tenantName: string;
  tenantPhone: string;
  dormitory: string;
  dormAddress: string;
  dormPhone: string;
  dormEmail: string;
  ownerName: string;
  paymentNumber: number;
  paymentSource: string;
  amountPaid: number;
  paymentDate: Date;
  nextPaymentDueDate: Date | null;
}): Promise<{ sent: boolean; message: string }> {
  const transporter = getSmtpTransporter();
  if (!transporter) {
    return { sent: false, message: "SMTP is not configured on the server" };
  }

  const fromAddress = process.env.SMTP_FROM || process.env.SMTP_USER;
  if (!fromAddress) {
    return { sent: false, message: "SMTP sender is not configured" };
  }

  const escapeHtml = (value: string) =>
    String(value || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');

  const amountText = `â‚±${Number(params.amountPaid || 0).toLocaleString("en-PH")}`;
  const paymentDateText = formatDateForEmail(params.paymentDate);
  const nextDueDateText = params.nextPaymentDueDate
    ? formatDateForEmail(params.nextPaymentDueDate)
    : "No remaining due date";
  const issuedAtDate = new Date();
  const issuedAt = issuedAtDate.toLocaleString('en-US', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
  }).replace(/,\s*/, ' at ');
  const contactNumber = params.dormPhone ? `+63${params.dormPhone}` : 'N/A';

  const text = [
    `Hello ${params.tenantName},`,
    "",
    "Payment Details",
    `Tenant Name: ${params.tenantName}`,
    `Dormitory: ${params.dormitory}`,
    `Amount Paid: ${amountText}`,
    `Payment Date: ${paymentDateText}`,
    "",
    `Next Payment Due Date: ${nextDueDateText}`,
    "",
    "Thank you for using DormEase. If you have any concerns, please contact the dorm owner.",
    "",
    "Best regards,",
    "DormEase",
    "",
    "---",
    "",
    "PAYMENT RECEIPT",
    `Receipt #${params.paymentNumber}`,
    `Issued: ${issuedAt}`,
    "",
    `System name: DormEase Dormitory, Management System`,
    "",
    `DORMITORY NAME: ${params.dormitory}`,
    `DORM ADDRESS: ${params.dormAddress}`,
    `CONTACT NUMBER: ${contactNumber}`,
    `EMAIL ADDRESS: ${params.dormEmail}`,
    "",
    `TENANT NAME: ${params.tenantName}`,
    `PAYMENT: Payment #${params.paymentNumber} (Paid via ${params.paymentSource})`,
    `PAYMENT DATE: ${paymentDateText}`,
    `NEXT PAYMENT DUE DATE: ${nextDueDateText}`,
    `AMOUNT: ${amountText}`,
    "",
    `TENANT SIGNATURE OVER PRINTED NAME: ${params.tenantName}`,
    `OWNER SIGNATURE OVER PRINTED NAME: ${params.ownerName}`,
    "",
    "DormEase Receipt â€¢ Keep this copy for your payment records",
  ].join("\n");

  const html = `
    <!DOCTYPE html>
    <html>
    <head>
      <meta charset="UTF-8">
      <title>Payment Receipt</title>
      <style>
        @media print {
          body { background: white !important; padding: 0 !important; }
          .no-print { display: none !important; }
          .greeting { page-break-after: always; }
        }
        * { box-sizing: border-box; }
        body { margin: 0; padding: 20px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; background: #f5f5f5; }
        .email-wrapper { max-width: 820px; margin: 0 auto; }
        .greeting { background: white; padding: 30px; margin-bottom: 20px; border-radius: 8px; box-shadow: 0 1px 2px rgba(0,0,0,0.05); }
        .greeting h2 { margin: 0 0 20px 0; color: #1f2937; font-size: 20px; font-weight: 600; }
        .greeting p { margin: 8px 0; color: #4b5563; line-height: 1.6; font-size: 14px; }
        .greeting .detail-label { font-weight: 600; color: #1f2937; }
        .receipt-card { background: white; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); overflow: hidden; border: 1px solid #e5e7eb; }
        .receipt-header { display: flex; align-items: center; justify-content: space-between; padding: 28px 36px; border-bottom: 1px solid #e5e7eb; }
        .logo-section { display: flex; align-items: center; gap: 12px; }
        .logo { width: 52px; height: 52px; background: #1e3a8a; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: white; font-weight: 700; font-size: 20px; flex-shrink: 0; }
        .logo-text { font-size: 13px; color: #6b7280; }
        .receipt-title { font-size: 26px; font-weight: 600; color: #1e293b; letter-spacing: -0.025em; }
        .system-name { padding: 18px 36px; background: white; border-bottom: 1px solid #e5e7eb; font-size: 15px; color: #374151; font-weight: 500; }
        .receipt-content { padding: 32px 36px; }
        .info-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; margin-bottom: 0; }
        .info-field { padding: 18px 20px; background: #f8fafc; border-radius: 8px; border: 1px solid #e2e8f0; }
        .info-label { font-size: 11px; text-transform: uppercase; color: #64748b; font-weight: 600; margin-bottom: 8px; letter-spacing: 0.05em; }
        .info-value { font-size: 15px; color: #0f172a; font-weight: 500; line-height: 1.4; word-break: break-word; }
        .info-field.full-width { grid-column: 1 / -1; }
        .signatures { display: grid; grid-template-columns: repeat(2, 1fr); gap: 40px; margin-top: 48px; padding-top: 40px; border-top: 2px solid #e5e7eb; }
        .signature-box { text-align: center; }
        .signature-line { border-top: 2px solid #0f172a; padding-top: 10px; margin-top: 70px; font-weight: 500; font-size: 15px; color: #0f172a; }
        .signature-label { font-size: 10px; color: #6366f1; text-transform: uppercase; margin-top: 6px; letter-spacing: 0.05em; font-weight: 600; }
        .receipt-footer { padding: 22px 36px; background: white; border-top: 1px solid #e5e7eb; text-align: center; color: #6b7280; font-size: 13px; }
        .receipt-footer strong { color: #1f2937; font-weight: 600; }
      </style>
    </head>
    <body>
      <div class="email-wrapper">
        <!-- Greeting Message -->
        <div class="greeting">
          <h2>DormEase Payment Confirmation</h2>
          <p>Hello ${escapeHtml(params.tenantName)},</p>
          <p style="margin-top: 16px; font-weight: 600;">Payment Details</p>
          <p><span class="detail-label">Tenant Name:</span> ${escapeHtml(params.tenantName)}</p>
          <p><span class="detail-label">Dormitory:</span> ${escapeHtml(params.dormitory)}</p>
          <p><span class="detail-label">Amount Paid:</span> ${escapeHtml(amountText)}</p>
          <p><span class="detail-label">Payment Date:</span> ${escapeHtml(paymentDateText)}</p>
          <p style="margin-top: 12px;"><span class="detail-label">Next Payment Due Date:</span> ${escapeHtml(nextDueDateText)}</p>
          <p style="margin-top: 20px;">Thank you for using DormEase. If you have any concerns, please contact the dorm owner.</p>
          <p style="margin-top: 16px;">Best regards,<br><strong>DormEase</strong></p>
        </div>

        <!-- Receipt -->
        <div class="receipt-card">
          <div class="receipt-header">
            <div class="logo-section">
              <div class="logo">DE</div>
              <div class="logo-text">DormEase</div>
            </div>
            <div class="receipt-title">Payment Receipt</div>
          </div>
          
          <div class="system-name">
            System name: DormEase Dormitory, Management System
          </div>
          
          <div class="receipt-content">
            <div class="info-grid">
              <div class="info-field">
                <div class="info-label">DORMITORY NAME</div>
                <div class="info-value">${escapeHtml(params.dormitory)}</div>
              </div>
              <div class="info-field">
                <div class="info-label">DATE ISSUED</div>
                <div class="info-value">${escapeHtml(issuedAt)}</div>
              </div>
              <div class="info-field full-width">
                <div class="info-label">DORM ADDRESS</div>
                <div class="info-value">${escapeHtml(params.dormAddress)}</div>
              </div>
              <div class="info-field">
                <div class="info-label">CONTACT NUMBER</div>
                <div class="info-value">${escapeHtml(contactNumber)}</div>
              </div>
              <div class="info-field">
                <div class="info-label">EMAIL ADDRESS</div>
                <div class="info-value">${escapeHtml(params.dormEmail)}</div>
              </div>
              <div class="info-field">
                <div class="info-label">TENANT NAME</div>
                <div class="info-value">${escapeHtml(params.tenantName)}</div>
              </div>
              <div class="info-field">
                <div class="info-label">PAYMENT</div>
                <div class="info-value">Payment #${params.paymentNumber} (Paid via ${escapeHtml(params.paymentSource)})</div>
              </div>
              <div class="info-field">
                <div class="info-label">PAYMENT DATE</div>
                <div class="info-value">${escapeHtml(paymentDateText)}</div>
              </div>
              <div class="info-field">
                <div class="info-label">NEXT PAYMENT DUE DATE</div>
                <div class="info-value">${escapeHtml(nextDueDateText)}</div>
              </div>
              <div class="info-field">
                <div class="info-label">AMOUNT</div>
                <div class="info-value">${escapeHtml(amountText)}</div>
              </div>
            </div>
            
            <div class="signatures">
              <div class="signature-box">
                <div class="signature-line">${escapeHtml(params.tenantName)}</div>
                <div class="signature-label">TENANT SIGNATURE OVER PRINTED NAME</div>
              </div>
              <div class="signature-box">
                <div class="signature-line">${escapeHtml(params.ownerName)}</div>
                <div class="signature-label">OWNER SIGNATURE OVER PRINTED NAME</div>
              </div>
            </div>
          </div>
          
          <div class="receipt-footer">
            <strong>DormEase Receipt</strong> â€¢ Keep this copy for your payment records
          </div>
        </div>
      </div>
    </body>
    </html>
  `;

  try {
    await transporter.sendMail({
      from: fromAddress,
      to: params.tenantEmail,
      subject: "DormEase Payment Confirmation",
      text,
      html,
    });
    return { sent: true, message: "Confirmation email sent to tenant" };
  } catch (err: any) {
    const errorMessage = err?.response || err?.message || "Failed to send confirmation email";
    return { sent: false, message: String(errorMessage) };
  }
}

function verifyAndConsumeSignupOtp(email: string, otp: string): { ok: boolean; message?: string } {
  const normalizedEmail = normalizeEmail(email);
  const record = signupOtpStore.get(normalizedEmail);

  if (!record) {
    return { ok: false, message: "OTP is missing or expired. Please request a new OTP." };
  }

  if (Date.now() > record.expiresAt) {
    signupOtpStore.delete(normalizedEmail);
    return { ok: false, message: "OTP has expired. Please request a new OTP." };
  }

  if (record.attempts >= SIGNUP_OTP_MAX_ATTEMPTS) {
    signupOtpStore.delete(normalizedEmail);
    return { ok: false, message: "Too many OTP attempts. Please request a new OTP." };
  }

  if (record.code !== String(otp || "").trim()) {
    record.attempts += 1;
    signupOtpStore.set(normalizedEmail, record);
    return { ok: false, message: "Invalid OTP code." };
  }

  signupOtpStore.delete(normalizedEmail);
  return { ok: true };
}

function verifyAndConsumeChangeOtp(userId: number, otp: string): { ok: boolean; message?: string } {
  const record = changeOtpStore.get(userId);

  if (!record) {
    return { ok: false, message: "OTP is missing or expired. Please request a new OTP." };
  }

  if (Date.now() > record.expiresAt) {
    changeOtpStore.delete(userId);
    return { ok: false, message: "OTP has expired. Please request a new OTP." };
  }

  if (record.attempts >= CHANGE_OTP_MAX_ATTEMPTS) {
    changeOtpStore.delete(userId);
    return { ok: false, message: "Too many OTP attempts. Please request a new OTP." };
  }

  if (record.code !== String(otp || "").trim()) {
    record.attempts += 1;
    changeOtpStore.set(userId, record);
    return { ok: false, message: "Invalid OTP code." };
  }

  changeOtpStore.delete(userId);
  return { ok: true };
}

function createSessionToken(): string {
  return crypto.randomBytes(32).toString("hex");
}

async function createSession(userId: number): Promise<string> {
  const token = createSessionToken();
  const expiresAt = new Date();
  expiresAt.setMinutes(expiresAt.getMinutes() + SESSION_MINUTES);
  await pool.query(
    "INSERT INTO sessions (user_id, token, expires_at) VALUES ($1, $2, $3)",
    [userId, token, expiresAt]
  );
  return token;
}

async function getUserIdFromToken(req: express.Request): Promise<number | null> {
  const authHeader = req.headers.authorization;
  const token = authHeader?.startsWith("Bearer ") ? authHeader.slice(7) : null;
  if (!token) return null;
  const result = await pool.query(
    "SELECT user_id FROM sessions WHERE token = $1 AND expires_at > current_timestamp",
    [token]
  );
  return result.rows.length > 0 ? result.rows[0].user_id : null;
}

async function canUsersMessageEachOther(senderId: number, recipientId: number): Promise<boolean> {
  if (!senderId || !recipientId || senderId === recipientId) {
    return false;
  }

  const result = await pool.query(
    `SELECT 1
     FROM users sender_user
     JOIN users recipient_user ON recipient_user.id = $2
     JOIN reservations r
       ON r.status = 'approved'
       AND r.tenant_action = 'accepted'
     WHERE sender_user.id = $1
       AND (
         (
           r.dorm_owner_id = $1
           AND lower(trim(coalesce(recipient_user.full_name, ''))) = lower(trim(coalesce(r.full_name, '')))
         )
         OR
         (
           r.dorm_owner_id = $2
           AND lower(trim(coalesce(sender_user.full_name, ''))) = lower(trim(coalesce(r.full_name, '')))
         )
       )
     LIMIT 1`,
    [senderId, recipientId]
  );

  return result.rows.length > 0;
}

async function canDormOwnerMessageNonTenant(ownerId: number, recipientId: number): Promise<boolean> {
  if (!ownerId || !recipientId || ownerId === recipientId) {
    return false;
  }
  // Any authenticated user can message any other user
  return true;
}

async function canAccessConversation(currentUserId: number, otherUserId: number): Promise<boolean> {
  if (!currentUserId || !otherUserId || currentUserId === otherUserId) {
    return false;
  }

  const currentlyAllowed = await canUsersMessageEachOther(currentUserId, otherUserId);
  if (currentlyAllowed) {
    return true;
  }

  const existing = await pool.query(
    `SELECT 1
     FROM messages
     WHERE (sender_id = $1 AND recipient_id = $2)
        OR (sender_id = $2 AND recipient_id = $1)
     LIMIT 1`,
    [currentUserId, otherUserId]
  );

  return existing.rows.length > 0;
}

const app = express();
const PORT = process.env.PORT || 3000;

const allowedOrigins = ['http://localhost:5173', 'http://localhost:5176'];
app.use(cors({
  origin: (origin, callback) => {
    if (!origin) return callback(null, true);
    if (allowedOrigins.includes(origin)) {
      callback(null, true);
    } else {
      callback(new Error('Not allowed by CORS'));
    }
  },
  credentials: true,
}));
app.use(express.json());
app.use("/uploads", express.static(UPLOAD_DIR));

async function requireAuth(req: express.Request, res: express.Response, next: express.NextFunction) {
  const userId = await getUserIdFromToken(req);
  if (userId === null) {
    return res.status(401).json({ message: "Unauthorized" });
  }
  (req as express.Request & { userId: number }).userId = userId;
  next();
}

const dormPhotosUpload = multer({
  storage: multer.diskStorage({
    destination: (req, _file, cb) => {
      const uid = (req as express.Request & { userId?: number }).userId;
      if (uid == null) return cb(new Error("Unauthorized"), "");
      const dir = path.join(DORMS_PHOTOS_DIR, String(uid));
      fs.mkdirSync(dir, { recursive: true });
      cb(null, dir);
    },
    filename: (_req, file, cb) => {
      const safe = (file.originalname || "photo").replace(/[^a-zA-Z0-9.-]/g, "_");
      cb(null, `${Date.now()}-${safe}`);
    },
  }),
  limits: { fileSize: 5 * 1024 * 1024 },
});

app.get("/", (_req, res) => {
  res.send("Backend running ");
});

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// AUTH
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

app.post("/auth/request-signup-otp", async (req, res) => {
  const { email, username } = req.body as { email?: string; username?: string };
  if (!email || !username) {
    return res.status(400).json({ message: "Email and username are required" });
  }

  const normalizedEmail = normalizeEmail(email);
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(normalizedEmail)) {
    return res.status(400).json({ message: "Invalid email address" });
  }

  const existingOtp = signupOtpStore.get(normalizedEmail);
  if (existingOtp && Date.now() - existingOtp.lastSentAt < SIGNUP_OTP_RESEND_SECONDS * 1000) {
    const secondsRemaining = Math.ceil(
      (SIGNUP_OTP_RESEND_SECONDS * 1000 - (Date.now() - existingOtp.lastSentAt)) / 1000
    );
    return res.status(429).json({
      message: `Please wait ${secondsRemaining}s before requesting another OTP`,
    });
  }

  try {
    const check = await pool.query(
      "SELECT id FROM users WHERE username=$1 OR email=$2",
      [username, normalizedEmail]
    );
    if (check.rows.length > 0) {
      return res.status(400).json({ message: "Username or email already exists" });
    }

    const transporter = getSmtpTransporter();
    if (!transporter) {
      return res.status(500).json({ message: "SMTP is not configured on the server" });
    }

    const fromAddress = process.env.SMTP_FROM || process.env.SMTP_USER;
    if (!fromAddress) {
      return res.status(500).json({ message: "SMTP sender is not configured" });
    }

    const otpCode = generateSignupOtpCode();
    signupOtpStore.set(normalizedEmail, {
      code: otpCode,
      expiresAt: Date.now() + SIGNUP_OTP_TTL_MINUTES * 60 * 1000,
      attempts: 0,
      lastSentAt: Date.now(),
    });

    await transporter.sendMail({
      from: fromAddress,
      to: normalizedEmail,
      subject: "DormEase signup verification code",
      text: `Your DormEase OTP is ${otpCode}. It expires in ${SIGNUP_OTP_TTL_MINUTES} minutes.`,
      html: `<p>Your DormEase OTP is <b>${otpCode}</b>.</p><p>It expires in ${SIGNUP_OTP_TTL_MINUTES} minutes.</p>`,
    });

    res.json({ message: "OTP sent successfully" });
  } catch (err: any) {
    console.error("Request signup OTP error:", err);
    console.error("SMTP Error details:", {
      message: err.message,
      code: err.code,
      command: err.command,
      response: err.response,
      responseCode: err.responseCode,
    });
    signupOtpStore.delete(normalizedEmail);
    const errorMsg = err.response || err.message || "Failed to send OTP";
    res.status(500).json({ message: `Failed to send OTP: ${errorMsg}` });
  }
});

app.post("/auth/signup", async (req, res) => {
  const { fullName, username, email, password, otp, platform } = req.body;
  if (!fullName || !username || !email || !password || !otp || !platform) {
    return res.status(400).json({ message: "All fields are required" });
  }
  if (!["web", "mobile"].includes(platform)) {
    return res.status(400).json({ message: "Invalid platform" });
  }
  try {
    const normalizedEmail = normalizeEmail(email);
    const check = await pool.query(
      "SELECT * FROM users WHERE username=$1 OR email=$2",
      [username, normalizedEmail]
    );
    if (check.rows.length > 0) {
      return res.status(400).json({ message: "Username or email already exists" });
    }

    const otpResult = verifyAndConsumeSignupOtp(normalizedEmail, String(otp));
    if (!otpResult.ok) {
      return res.status(400).json({ message: otpResult.message });
    }

    const hashedPassword = await bcrypt.hash(password, 10);
    const phoneNumber = req.body.phoneNumber || null;
    const result = await pool.query(
      `INSERT INTO users (full_name, username, email, phone_number, password, platform)
       VALUES ($1, $2, $3, $4, $5, $6) RETURNING id, username, email, phone_number, platform, full_name`,
      [fullName, username, normalizedEmail, phoneNumber, hashedPassword, platform]
    );
    const user = result.rows[0];
    const token = await createSession(user.id);
    res.status(201).json({
      message: "User created successfully",
      user: { id: user.id, username: user.username, email: user.email, phoneNumber: user.phone_number, platform: user.platform, fullName: user.full_name },
      token,
    });
  } catch (err) {
    console.error("Signup error:", err);
    res.status(500).json({ message: "Database error" });
  }
});

app.post("/auth/login", async (req, res) => {
  const { identifier, password, platform } = req.body;
  if (!identifier || !password || !platform) {
    return res.status(400).json({ message: "Username/email, password, and platform are required" });
  }
  if (!["web", "mobile"].includes(platform)) {
    return res.status(400).json({ message: "Invalid platform" });
  }
  try {
    const result = await pool.query(
      "SELECT * FROM users WHERE username=$1 OR email=$1",
      [identifier]
    );
    if (result.rows.length === 0) {
      return res.status(401).json({ message: "User not found" });
    }
    const user = result.rows[0];
    const match = await bcrypt.compare(password, user.password);
    if (!match) {
      return res.status(401).json({ message: "Wrong password" });
    }
    if (user.platform !== platform) {
      return res.status(403).json({ message: `This account cannot log in from ${platform}` });
    }
    const token = await createSession(user.id);
    res.json({
      message: "Login successful",
      user: { id: user.id, username: user.username, email: user.email, phoneNumber: user.phone_number, platform: user.platform, fullName: user.full_name },
      token,
    });
  } catch (err) {
    console.error("Login error:", err);
    res.status(500).json({ message: "Database error" });
  }
});

app.get("/auth/me", async (req, res) => {
  const authHeader = req.headers.authorization;
  const token = authHeader?.startsWith("Bearer ") ? authHeader.slice(7) : null;
  if (!token) {
    return res.status(401).json({ message: "No session token" });
  }
  try {
    const result = await pool.query(
      `SELECT u.id, u.full_name, u.username, u.email, u.phone_number, u.platform
       FROM users u
       JOIN sessions s ON s.user_id = u.id
       WHERE s.token = $1 AND s.expires_at > current_timestamp`,
      [token]
    );
    if (result.rows.length === 0) {
      return res.status(401).json({ message: "Invalid or expired session" });
    }
    const user = result.rows[0];
    res.json({
      user: { id: user.id, username: user.username, email: user.email, phoneNumber: user.phone_number, platform: user.platform, fullName: user.full_name },
    });
  } catch (err) {
    console.error("Auth me error:", err);
    res.status(500).json({ message: "DatabaseD error" });
  }
});

app.patch("/auth/me", requireAuth, async (req, res) => {
  const userId = (req as express.Request & { userId: number }).userId;
  const { fullName, username, email, password, phoneNumber } = req.body as {
    fullName?: string; username?: string; email?: string; password?: string; phoneNumber?: string;
  };
  if (!fullName && !username && !email && !password && phoneNumber === undefined) {
    return res.status(400).json({ message: "No fields to update" });
  }
  try {
    if (username) {
      const q = await pool.query("SELECT id FROM users WHERE username = $1 AND id != $2", [username, userId]);
      if (q.rows.length > 0) return res.status(400).json({ message: "Username already taken" });
    }
    if (email) {
      const q = await pool.query("SELECT id FROM users WHERE email = $1 AND id != $2", [email, userId]);
      if (q.rows.length > 0) return res.status(400).json({ message: "Email already taken" });
    }
    const fields: string[] = [];
    const vals: any[] = [];
    let idx = 1;
    if (fullName) { fields.push(`full_name = $${idx++}`); vals.push(fullName); }
    if (username) { fields.push(`username = $${idx++}`); vals.push(username); }
    if (email)    { fields.push(`email = $${idx++}`);    vals.push(email); }
    if (phoneNumber !== undefined) { fields.push(`phone_number = $${idx++}`); vals.push(phoneNumber || null); }
    if (password) {
      const hashed = await bcrypt.hash(password, 10);
      fields.push(`password = $${idx++}`);
      vals.push(hashed);
    }
    if (fields.length === 0) return res.status(400).json({ message: "Nothing to update" });
    const sql = `UPDATE users SET ${fields.join(", ")} WHERE id = $${idx} RETURNING id, full_name, username, email, phone_number, platform`;
    vals.push(userId);
    const result = await pool.query(sql, vals);
    const updated = result.rows[0];
    res.json({ user: { id: updated.id, fullName: updated.full_name, username: updated.username, email: updated.email, phoneNumber: updated.phone_number, platform: updated.platform } });
  } catch (err) {
    console.error("Update user error:", err);
    res.status(500).json({ message: "Database error" });
  }
});

app.post("/auth/request-change-otp", requireAuth, async (req, res) => {
  const userId = (req as express.Request & { userId: number }).userId;
  try {
    const user = await pool.query("SELECT email FROM users WHERE id = $1", [userId]);
    if (user.rows.length === 0) {
      return res.status(404).json({ message: "User not found" });
    }

    const userEmail = user.rows[0].email;
    const existing = changeOtpStore.get(userId);

    if (existing && Date.now() - existing.lastSentAt < CHANGE_OTP_RESEND_SECONDS * 1000) {
      return res.status(429).json({ message: `Please wait ${Math.ceil((CHANGE_OTP_RESEND_SECONDS * 1000 - (Date.now() - existing.lastSentAt)) / 1000)} seconds before requesting again` });
    }

    const otp = generateSignupOtpCode();
    const expiresAt = Date.now() + CHANGE_OTP_TTL_MINUTES * 60 * 1000;

    changeOtpStore.set(userId, {
      userId,
      code: otp,
      expiresAt,
      attempts: 0,
      lastSentAt: Date.now(),
    });

    const transporter = getSmtpTransporter();
    if (transporter) {
      try {
        await transporter.sendMail({
          from: process.env.SMTP_FROM || 'noreply@dormease.com',
          to: userEmail,
          subject: 'DormEase Account Verification Code',
          html: `
            <div style="font-family: Arial, sans-serif; color: #333;">
              <h2 style="color: #4f73ff;">DormEase Account Verification</h2>
              <p>Your OTP for changing password or email is:</p>
              <h1 style="color: #4f73ff; font-size: 32px; letter-spacing: 4px;">${otp}</h1>
              <p style="color: #666;">This code expires in ${CHANGE_OTP_TTL_MINUTES} minutes.</p>
              <p style="color: #999; font-size: 12px;">If you didn't request this, please ignore this email.</p>
            </div>
          `,
        });
      } catch (emailErr) {
        console.error("Email send error:", emailErr);
        return res.status(500).json({ message: "Failed to send OTP email" });
      }
    }

    res.json({ message: "OTP sent to your email", email: userEmail });
  } catch (err) {
    console.error("Request change OTP error:", err);
    res.status(500).json({ message: "Database error" });
  }
});

app.post("/auth/verify-change-otp", requireAuth, async (req, res) => {
  const userId = (req as express.Request & { userId: number }).userId;
  const { otp } = req.body as { otp?: string };

  if (!otp) {
    return res.status(400).json({ message: "OTP is required" });
  }

  const verification = verifyAndConsumeChangeOtp(userId, otp);
  if (!verification.ok) {
    return res.status(400).json({ message: verification.message || "Invalid OTP" });
  }

  res.json({ message: "OTP verified successfully" });
});

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// FORGOT PASSWORD -- OTP-based password reset (unauthenticated)
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

const passwordResetOtpStore = new Map<string, { code: string; expiresAt: number; attempts: number; lastSentAt: number }>();

app.post("/auth/forgot-password", async (req, res) => {
  const { email } = req.body as { email?: string };
  if (!email || typeof email !== "string") {
    return res.status(400).json({ message: "Email is required" });
  }
  const normalizedEmail = normalizeEmail(email);
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(normalizedEmail)) {
    return res.status(400).json({ message: "Invalid email address" });
  }

  // Throttle resend: same TTL as signup OTP
  const existing = passwordResetOtpStore.get(normalizedEmail);
  if (existing && Date.now() - existing.lastSentAt < SIGNUP_OTP_RESEND_SECONDS * 1000) {
    const wait = Math.ceil((SIGNUP_OTP_RESEND_SECONDS * 1000 - (Date.now() - existing.lastSentAt)) / 1000);
    return res.status(429).json({ message: `Please wait ${wait}s before requesting another OTP` });
  }

  try {
    // Only send OTP if the account actually exists -- but always return the same
    // success message to avoid user enumeration.
    const userResult = await pool.query("SELECT id FROM users WHERE email = $1", [normalizedEmail]);
    if (userResult.rows.length === 0) {
      // Return 200 to prevent email enumeration
      return res.json({ message: "If that email is registered, an OTP has been sent." });
    }

    const transporter = getSmtpTransporter();
    if (!transporter) {
      return res.status(500).json({ message: "SMTP is not configured on the server" });
    }
    const fromAddress = process.env.SMTP_FROM || process.env.SMTP_USER;
    if (!fromAddress) {
      return res.status(500).json({ message: "SMTP sender is not configured" });
    }

    const otpCode = generateSignupOtpCode();
    passwordResetOtpStore.set(normalizedEmail, {
      code: otpCode,
      expiresAt: Date.now() + SIGNUP_OTP_TTL_MINUTES * 60 * 1000,
      attempts: 0,
      lastSentAt: Date.now(),
    });

    await transporter.sendMail({
      from: fromAddress,
      to: normalizedEmail,
      subject: "DormEase Password reset code",
      text: `Your DormEase password reset OTP is ${otpCode}. It expires in ${SIGNUP_OTP_TTL_MINUTES} minutes. If you did not request this, ignore this email.`,
      html: `<p>Your DormEase password reset OTP is <b>${otpCode}</b>.</p><p>It expires in ${SIGNUP_OTP_TTL_MINUTES} minutes.</p><p>If you did not request this, ignore this email.</p>`,
    });

    return res.json({ message: "If that email is registered, an OTP has been sent." });
  } catch (err: any) {
    console.error("Forgot password OTP error:", err);
    passwordResetOtpStore.delete(normalizedEmail);
    return res.status(500).json({ message: "Failed to send OTP. Please try again." });
  }
});

app.post("/auth/reset-password", async (req, res) => {
  const { email, otp, newPassword } = req.body as { email?: string; otp?: string; newPassword?: string };
  if (!email || !otp || !newPassword) {
    return res.status(400).json({ message: "email, otp, and newPassword are required" });
  }
  if (typeof newPassword !== "string" || newPassword.length < 6) {
    return res.status(400).json({ message: "Password must be at least 6 characters" });
  }

  const normalizedEmail = normalizeEmail(String(email));
  const record = passwordResetOtpStore.get(normalizedEmail);

  if (!record) {
    return res.status(400).json({ message: "OTP is missing or expired. Please request a new OTP." });
  }
  if (Date.now() > record.expiresAt) {
    passwordResetOtpStore.delete(normalizedEmail);
    return res.status(400).json({ message: "OTP has expired. Please request a new OTP." });
  }
  if (record.attempts >= SIGNUP_OTP_MAX_ATTEMPTS) {
    passwordResetOtpStore.delete(normalizedEmail);
    return res.status(400).json({ message: "Too many OTP attempts. Please request a new OTP." });
  }
  if (record.code !== String(otp).trim()) {
    record.attempts += 1;
    passwordResetOtpStore.set(normalizedEmail, record);
    return res.status(400).json({ message: "Invalid OTP code." });
  }

  // OTP is valid -- consume it
  passwordResetOtpStore.delete(normalizedEmail);

  try {
    const hashedPassword = await bcrypt.hash(newPassword, 10);
    const result = await pool.query(
      "UPDATE users SET password = $1 WHERE email = $2 RETURNING id",
      [hashedPassword, normalizedEmail]
    );
    if (result.rows.length === 0) {
      return res.status(404).json({ message: "User not found" });
    }
    // Invalidate all existing sessions for this user so old tokens stop working
    await pool.query("DELETE FROM sessions WHERE user_id = $1", [result.rows[0].id]);
    return res.json({ message: "Password reset successful. Please log in with your new password." });
  } catch (err: any) {
    console.error("Reset password error:", err);
    return res.status(500).json({ message: "Database error" });
  }
});

app.post("/auth/logout", async (req, res) => {
  const authHeader = req.headers.authorization;
  const token = authHeader?.startsWith("Bearer ") ? authHeader.slice(7) : null;
  if (!token) {
    return res.status(200).json({ message: "Logged out" });
  }
  try {
    await pool.query("DELETE FROM sessions WHERE token = $1", [token]);
    res.json({ message: "Logged out" });
  } catch (err) {
    console.error("Logout error:", err);
    res.status(500).json({ message: "Database error" });
  }
});

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// DORMS
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

app.get("/dorms/me", async (req, res) => {
  const userId = await getUserIdFromToken(req);
  if (userId === null) {
    return res.status(401).json({ message: "Unauthorized" });
  }
  try {
    const result = await pool.query(
      "SELECT id, dorm_name, email, phone, price, deposit, advance, address, latitude, longitude, room_capacity, utilities, photo_urls FROM dorms WHERE user_id = $1",
      [userId]
    );
    if (result.rows.length === 0) {
      return res.json({ dorm: null });
    }
    res.json({ dorm: result.rows[0] });
  } catch (err) {
    console.error("Get dorm error:", err);
    res.status(500).json({ message: "Database error" });
  }
});

app.post(
  "/dorms",
  requireAuth,
  dormPhotosUpload.array("photos", 10),
  async (req: express.Request, res: express.Response) => {
    const userId = (req as express.Request & { userId: number }).userId;
    const body = req.body as {
      dormName?: string; email?: string; phone?: string; price?: string;
      deposit?: string; advance?: string; address?: string; capacity?: string;
      latitude?: string; longitude?: string;
      utilities?: string[] | string;
    };
    const { dormName, email, phone, price, deposit, advance, address, capacity, latitude, longitude, utilities: utilitiesBody } = body;
    const files = (req as express.Request & { files?: Express.Multer.File[] }).files;

    if (!dormName || !email || !phone || !price || !address || capacity == null) {
      return res.status(400).json({ message: "All dorm fields are required" });
    }

    const phoneVal = String(phone).replace(/^\+63/, "").trim() || phone;

    let utilities: string[] = [];
    if (Array.isArray(utilitiesBody)) utilities = utilitiesBody as string[];
    else if (typeof utilitiesBody === 'string') {
      try { utilities = JSON.parse(utilitiesBody) as string[]; } catch { utilities = []; }
    }

    const newPhotoUrls: string[] =
      files && files.length > 0
        ? files.map(f => "/uploads/dorms/" + String(userId) + "/" + f.filename)
        : [];

    try {
      let photoUrlsToSave: string[] = newPhotoUrls;
      if (newPhotoUrls.length < 4) {
        const existing = await pool.query("SELECT photo_urls FROM dorms WHERE user_id = $1", [userId]);
        const existingUrls: string[] = existing.rows[0]?.photo_urls || [];
        photoUrlsToSave = existingUrls.length > 0 ? existingUrls : newPhotoUrls;
      }

      await pool.query(
        `INSERT INTO dorms (user_id, dorm_name, email, phone, price, deposit, advance, address, latitude, longitude, room_capacity, utilities, photo_urls)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13)
         ON CONFLICT (user_id) DO UPDATE SET
           dorm_name = EXCLUDED.dorm_name,
           email = EXCLUDED.email,
           phone = EXCLUDED.phone,
           price = EXCLUDED.price,
           deposit = EXCLUDED.deposit,
           advance = EXCLUDED.advance,
           address = EXCLUDED.address,
           latitude = EXCLUDED.latitude,
           longitude = EXCLUDED.longitude,
           room_capacity = EXCLUDED.room_capacity,
           utilities = EXCLUDED.utilities,
           photo_urls = CASE WHEN EXCLUDED.photo_urls IS NOT NULL AND jsonb_array_length(EXCLUDED.photo_urls) > 0 THEN EXCLUDED.photo_urls ELSE dorms.photo_urls END,
           updated_at = current_timestamp`,
        [userId, dormName, email, phoneVal, price, deposit || null, advance || null, address, latitude ? parseFloat(latitude) : null, longitude ? parseFloat(longitude) : null, Number(capacity), utilities, JSON.stringify(photoUrlsToSave)]
      );

      const out = await pool.query(
        "SELECT id, dorm_name, email, phone, price, deposit, advance, address, latitude, longitude, room_capacity, utilities, photo_urls FROM dorms WHERE user_id = $1",
        [userId]
      );
      res.json({ message: "Dorm saved", dorm: out.rows[0] });
    } catch (err) {
      console.error("Save dorm error:", err);
      res.status(500).json({ message: "Database error" });
    }
  }
);

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// GET /dorms/available
// Returns all dorms with a real-time occupied_count computed from
// approved + tenant_accepted reservations in the database.
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
app.get("/dorms/available", async (_req, res) => {
  try {
    const result = await pool.query(
      `SELECT d.id, d.user_id AS owner_id, d.dorm_name, d.email, d.phone, d.price, d.deposit, d.advance,
              d.address, d.latitude, d.longitude, d.room_capacity, d.utilities, d.photo_urls,
              u.full_name AS owner_name,
              COUNT(r.id) FILTER (
                WHERE r.status = 'approved' AND r.tenant_action = 'accepted'
              )::int AS occupied_count
       FROM dorms d
       JOIN users u ON d.user_id = u.id
       LEFT JOIN reservations r ON r.dorm_owner_id = d.user_id
       GROUP BY d.id, u.full_name, u.id
       ORDER BY d.created_at DESC`
    );
    res.json(result.rows.map((row: any) => ({
      id: row.id,
      owner_id: row.owner_id,
      dorm_name: row.dorm_name,
      email: row.email,
      phone: row.phone,
      price: row.price,
      deposit: row.deposit,
      advance: row.advance,
      address: row.address,
      room_capacity: row.room_capacity,
      utilities: row.utilities,
      photo_urls: row.photo_urls,
      owner_name: row.owner_name,
      occupied_count: row.occupied_count ?? 0,
    })));
  } catch (err) {
    console.error("Get available dorms error:", err);
    res.status(500).json({ message: "Database error" });
  }
});

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// USERS
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

app.get("/users", requireAuth, async (_req, res) => {
  try {
    const result = await pool.query(
      "SELECT id, full_name, username, email, created_at FROM users"
    );
    res.json(result.rows);
  } catch (err) {
    console.error("Get users error:", err);
    res.status(500).json({ message: "Database error" });
  }
});

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// MESSAGES
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

app.get("/messages/contacts", async (req, res) => {
  const userId = await getUserIdFromToken(req);
  if (userId === null) {
    return res.status(401).json({ message: "Unauthorized" });
  }

  try {
    const result = await pool.query(
      `WITH owner_side AS (
          SELECT DISTINCT
            u.id,
            u.full_name,
            u.username,
            u.email,
            'tenant'::text AS relation,
            1 AS relation_rank
          FROM reservations r
          JOIN users u
            ON lower(trim(coalesce(u.full_name, ''))) = lower(trim(coalesce(r.full_name, '')))
          WHERE r.dorm_owner_id = $1
            AND r.status = 'approved'
            AND r.tenant_action = 'accepted'
        ),
        tenant_side AS (
          SELECT DISTINCT
            owner.id,
            owner.full_name,
            owner.username,
            owner.email,
            'owner'::text AS relation,
            1 AS relation_rank
          FROM users me
          JOIN reservations r
            ON lower(trim(coalesce(me.full_name, ''))) = lower(trim(coalesce(r.full_name, '')))
          JOIN users owner
            ON owner.id = r.dorm_owner_id
          WHERE me.id = $1
            AND r.status = 'approved'
            AND r.tenant_action = 'accepted'
        ),
        message_side AS (
          SELECT DISTINCT
            u.id,
            u.full_name,
            u.username,
            u.email,
            NULL::text AS relation,
            2 AS relation_rank
          FROM messages m
          JOIN users u
            ON u.id = CASE
              WHEN m.sender_id = $1 THEN m.recipient_id
              ELSE m.sender_id
            END
          WHERE (
            m.sender_id = $1
            AND m.sender_deleted_at IS NULL
          )
          OR (
            m.recipient_id = $1
            AND m.recipient_deleted_at IS NULL
          )
        ),
        combined AS (
          SELECT * FROM owner_side
          UNION ALL
          SELECT * FROM tenant_side
          UNION ALL
          SELECT * FROM message_side
        ),
        dedup AS (
          SELECT DISTINCT ON (id)
            id,
            full_name,
            username,
            email,
            relation
          FROM combined
          WHERE id <> $1
          ORDER BY id, relation_rank ASC, full_name ASC, username ASC
        )
        SELECT *
        FROM dedup
        ORDER BY full_name ASC NULLS LAST, username ASC NULLS LAST`,
      [userId]
    );

    return res.json(result.rows);
  } catch (err) {
    console.error("Get message contacts error:", err);
    return res.status(500).json({ message: "Database error" });
  }
});

app.get("/messages/conversations", async (req, res) => {
  const userId = await getUserIdFromToken(req);
  if (userId === null) {
    return res.status(401).json({ message: "Unauthorized" });
  }

  try {
    const result = await pool.query(
      `WITH owner_side AS (
          SELECT DISTINCT
            u.id,
            u.full_name,
            u.username,
            u.email,
            'tenant'::text AS relation,
            1 AS relation_rank
          FROM reservations r
          JOIN users u
            ON lower(trim(coalesce(u.full_name, ''))) = lower(trim(coalesce(r.full_name, '')))
          WHERE r.dorm_owner_id = $1
            AND r.status = 'approved'
            AND r.tenant_action = 'accepted'
        ),
        tenant_side AS (
          SELECT DISTINCT
            owner.id,
            owner.full_name,
            owner.username,
            owner.email,
            'owner'::text AS relation,
            1 AS relation_rank
          FROM users me
          JOIN reservations r
            ON lower(trim(coalesce(me.full_name, ''))) = lower(trim(coalesce(r.full_name, '')))
          JOIN users owner
            ON owner.id = r.dorm_owner_id
          WHERE me.id = $1
            AND r.status = 'approved'
            AND r.tenant_action = 'accepted'
        ),
        message_side AS (
          SELECT DISTINCT
            u.id,
            u.full_name,
            u.username,
            u.email,
            NULL::text AS relation,
            2 AS relation_rank
          FROM messages m
          JOIN users u
            ON u.id = CASE
              WHEN m.sender_id = $1 THEN m.recipient_id
              ELSE m.sender_id
            END
          WHERE (
            m.sender_id = $1
            AND m.sender_deleted_at IS NULL
          )
          OR (
            m.recipient_id = $1
            AND m.recipient_deleted_at IS NULL
          )
        ),
        combined AS (
          SELECT * FROM owner_side
          UNION
          SELECT * FROM tenant_side
          UNION
          SELECT * FROM message_side
        ),
        contacts AS (
          SELECT DISTINCT ON (id)
            id,
            full_name,
            username,
            email,
            relation
          FROM combined
          WHERE id <> $1
          ORDER BY id, relation_rank ASC, full_name ASC, username ASC
        )
        SELECT
          c.id,
          c.full_name,
          c.username,
          c.email,
          c.relation,
          latest.message AS last_message,
          latest.created_at AS last_message_at,
          latest.sender_id AS last_sender_id
        FROM contacts c
        LEFT JOIN LATERAL (
          SELECT m.message, m.created_at, m.sender_id
          FROM messages m
          WHERE (
            m.sender_id = $1
            AND m.recipient_id = c.id
            AND m.sender_deleted_at IS NULL
          )
          OR (
            m.recipient_id = $1
            AND m.sender_id = c.id
            AND m.recipient_deleted_at IS NULL
          )
          ORDER BY m.created_at DESC, m.id DESC
          LIMIT 1
        ) latest ON TRUE
        ORDER BY COALESCE(latest.created_at, to_timestamp(0)) DESC, c.full_name ASC NULLS LAST, c.username ASC NULLS LAST`,
      [userId]
    );

    return res.json(result.rows);
  } catch (err) {
    console.error("Get conversations error:", err);
    return res.status(500).json({ message: "Database error" });
  }
});

app.get("/messages/:contactId/history", async (req, res) => {
  const userId = await getUserIdFromToken(req);
  if (userId === null) {
    return res.status(401).json({ message: "Unauthorized" });
  }

  const contactId = Number(req.params.contactId);
  if (!contactId || contactId <= 0 || contactId === userId) {
    return res.status(400).json({ message: "Invalid contact id" });
  }

  try {
    const allowed = await canAccessConversation(userId, contactId);
    if (!allowed) {
      return res.status(403).json({ message: "Not allowed to access this conversation" });
    }

    const result = await pool.query(
      `SELECT id, sender_id, recipient_id, message, created_at
       FROM messages
       WHERE (
         sender_id = $1
         AND recipient_id = $2
         AND sender_deleted_at IS NULL
       )
       OR (
         sender_id = $2
         AND recipient_id = $1
         AND recipient_deleted_at IS NULL
       )
       ORDER BY created_at ASC, id ASC`,
      [userId, contactId]
    );

    return res.json(result.rows);
  } catch (err) {
    console.error("Get conversation history error:", err);
    return res.status(500).json({ message: "Database error" });
  }
});

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// POST /messages/send
// Single delivery path for all messages (web + mobile).
// Saves to DB once, then pushes `new_message` to the RECIPIENT only (rid).
// The sender NEVER receives a WebSocket echo -- they see their own message
// via optimistic update on the client side.
// The `send_message` socket handler in websocket.ts has been removed to
// prevent double-delivery. This is the only place messages are persisted.
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
app.post("/messages/send", requireAuth, async (req, res) => {
  const senderId = (req as express.Request & { userId: number }).userId;
  const { recipientId, message } = req.body as { recipientId?: number; message?: string };

  if (!recipientId || !message || String(message).trim().length === 0) {
    return res.status(400).json({ message: "recipientId and message are required" });
  }

  const trimmedMessage = String(message).trim();
  const rid = Number(recipientId);

  if (!rid || rid === senderId) {
    return res.status(400).json({ message: "Invalid recipientId" });
  }

  try {
    // Verify recipient exists
    const recipientCheck = await pool.query(
      "SELECT id FROM users WHERE id = $1",
      [rid]
    );
    if (recipientCheck.rows.length === 0) {
      return res.status(404).json({ message: "Recipient user not found" });
    }

    const allowed = await canDormOwnerMessageNonTenant(senderId, rid);
    if (!allowed) {
      return res.status(403).json({
        message: "Cannot message this user",
      });
    }

    const result = await pool.query(
      `INSERT INTO messages (sender_id, recipient_id, message, created_at)
       VALUES ($1, $2, $3, NOW())
       RETURNING id, sender_id, recipient_id, message, created_at`,
      [senderId, rid, trimmedMessage]
    );

    const saved = result.rows[0];

    // FIX: Push `new_message` to the RECIPIENT only (rid).
    // Do NOT notify senderId -- they already see their own message via
    // optimistic update. Notifying them would create a duplicate incoming message.
    const io = req.app.get('io');
    if (io) {
      notifyUser(io, rid, 'new_message', {
        id: saved.id,
        senderId,
        recipientId: rid,
        message: trimmedMessage,
        timestamp: saved.created_at,
      });
    }

    return res.status(201).json({ message: "Message sent", data: saved });
  } catch (err) {
    console.error("Send message error:", err);
    return res.status(500).json({ message: "Database error" });
  }
});

app.delete("/messages/:contactId", async (req, res) => {
  const userId = await getUserIdFromToken(req);
  if (userId === null) {
    return res.status(401).json({ message: "Unauthorized" });
  }

  const contactId = Number(req.params.contactId);
  if (!contactId || contactId <= 0 || contactId === userId) {
    return res.status(400).json({ message: "Invalid contact id" });
  }

  try {
    const allowed = await canAccessConversation(userId, contactId);
    if (!allowed) {
      return res.status(403).json({ message: "Not allowed to delete this conversation" });
    }

    const deletedAsSender = await pool.query(
      `UPDATE messages
       SET sender_deleted_at = NOW()
       WHERE sender_id = $1
         AND recipient_id = $2
         AND sender_deleted_at IS NULL`,
      [userId, contactId]
    );

    const deletedAsRecipient = await pool.query(
      `UPDATE messages
       SET recipient_deleted_at = NOW()
       WHERE sender_id = $2
         AND recipient_id = $1
         AND recipient_deleted_at IS NULL`,
      [userId, contactId]
    );

    return res.json({
      message: "Conversation deleted",
      deletedCount: deletedAsSender.rowCount + deletedAsRecipient.rowCount,
    });
  } catch (err) {
    console.error("Delete conversation error:", err);
    return res.status(500).json({ message: "Database error" });
  }
});

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// RESERVATIONS
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

app.post("/reservations", async (req, res) => {
  const {
    dorm_name, location, full_name, phone, move_in_date,
    duration_months, price_per_month, deposit, advance,
    total_amount, notes, payment_method, dorm_owner_id, tenant_email,
    tenantEmail: tenantEmailCamelCase,
  } = req.body;

  if (!dorm_name || !full_name || !phone || !move_in_date || !duration_months) {
    return res.status(400).json({ message: "Missing required fields" });
  }

  try {
    const requesterUserId = await getUserIdFromToken(req);
    let ownerId = dorm_owner_id ?? null;
    if (!ownerId) {
      const dormResult = await pool.query(
        "SELECT user_id FROM dorms WHERE dorm_name = $1 LIMIT 1",
        [dorm_name]
      );
      if (dormResult.rows.length > 0) {
        ownerId = dormResult.rows[0].user_id;
      }
    }

    const tenantEmailFromRequest = tenant_email ?? tenantEmailCamelCase;
    let tenantEmail: string | null = tenantEmailFromRequest
      ? String(tenantEmailFromRequest).trim().toLowerCase()
      : null;

    if (!tenantEmail && requesterUserId !== null) {
      const requester = await pool.query("SELECT email FROM users WHERE id = $1", [requesterUserId]);
      if (requester.rows.length > 0) {
        tenantEmail = String(requester.rows[0].email || "").trim().toLowerCase() || null;
      }
    }

    if (!tenantEmail) {
      const byName = await pool.query(
        `SELECT email
         FROM users
         WHERE lower(trim(full_name)) = lower(trim($1))
         ORDER BY id DESC
         LIMIT 1`,
        [full_name]
      );
      if (byName.rows.length > 0) {
        tenantEmail = String(byName.rows[0].email || "").trim().toLowerCase() || null;
      }
    }

    console.log("ðŸ“§ Reservation Creation - tenant_email:", {
      fromRequestSnakeCase: tenant_email || null,
      fromRequestCamelCase: tenantEmailCamelCase || null,
      fromAuthUser: requesterUserId ? "lookup attempted" : "no auth",
      finalEmail: tenantEmail,
      tenantName: full_name
    });

    const result = await pool.query(
      `INSERT INTO reservations
        (dorm_name, location, full_name, phone, move_in_date, duration_months,
         price_per_month, deposit, advance, total_amount, notes, payment_method,
         dorm_owner_id, tenant_email, status, created_at)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, 'pending', NOW())
       RETURNING *`,
      [
        dorm_name, location, full_name, phone, move_in_date, duration_months,
        price_per_month  ?? 0,
        deposit          ?? 0,
        advance          ?? 0,
        total_amount     ?? 0,
        notes            ?? "",
        payment_method   ?? "cash_on_move_in",
        ownerId,
        tenantEmail,
      ]
    );

    const createdReservation = result.rows[0];
    const io = req.app.get('io');
    if (io && ownerId) {
      notifyUser(io, Number(ownerId), 'notification', {
        type: 'reservation_created',
        reservationId: createdReservation.id,
        status: createdReservation.status,
        dormName: createdReservation.dorm_name,
        tenantName: createdReservation.full_name,
        moveInDate: createdReservation.move_in_date,
        durationMonths: createdReservation.duration_months,
        message: `${createdReservation.full_name} submitted a reservation for ${createdReservation.dorm_name}`,
      });
    }
    return res.status(201).json({ message: "Reservation submitted successfully", reservation: createdReservation });
  } catch (err) {
    console.error("Reservation error:", err);
    return res.status(500).json({ message: "Database error" });
  }
});

app.get("/reservations", async (req, res) => {
  const userId = await getUserIdFromToken(req);
  if (userId === null) {
    return res.status(401).json({ message: "Unauthorized" });
  }
  try {
    const result = await pool.query(
      `SELECT r.id, r.dorm_name, r.location, r.full_name, r.phone, r.move_in_date,
              r.duration_months, r.price_per_month, r.deposit, r.advance, r.total_amount,
              r.notes, r.payment_method, r.status, r.rejection_reason, r.termination_reason,
              r.tenant_action, r.cancel_reason, r.tenant_action_at, r.payments_paid,
              r.advance_used, r.deposit_used,
              r.tenant_email, r.receipt_history,
              r.appeal_message, r.appeal_submitted_at, r.appeal_dismissed_at,
              r.created_at, d.room_capacity, d.id as dorm_id
       FROM reservations r
       LEFT JOIN dorms d ON d.user_id = r.dorm_owner_id
       WHERE r.dorm_owner_id = $1
       ORDER BY r.created_at DESC`,
      [userId]
    );
    return res.json(result.rows);
  } catch (err) {
    console.error("Get reservations error:", err);
    return res.status(500).json({ message: "Database error" });
  }
});

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// GET /reservations/tenant -- Android polls for status + dashboard data
// Returns ALL fields needed by TenantDashboardActivity, including
// tenant_action, payments_paid, advance_used, deposit_used, and owner_name.
// IMPORTANT: must be declared BEFORE /reservations/:id routes so Express
// does not match "tenant" or "tenant/me" as a numeric id.
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
const TENANT_RESERVATIONS_SELECT = `SELECT
  r.id,
  r.dorm_name,
  r.location,
  r.full_name,
  r.phone,
  r.move_in_date,
  r.duration_months,
  r.price_per_month,
  r.deposit,
  r.advance,
  r.total_amount,
  r.notes,
  r.payment_method,
  r.status,
  r.rejection_reason,
  r.termination_reason,
  r.tenant_action,
  r.cancel_reason,
  r.tenant_action_at,
  r.payments_paid,
  r.advance_used,
  r.deposit_used,
  r.tenant_email,
  r.receipt_history,
  r.appeal_message,
  r.appeal_submitted_at,
  r.appeal_dismissed_at,
  r.created_at,
  r.dorm_owner_id,
  u.full_name AS owner_name
FROM reservations r
LEFT JOIN users u ON u.id = r.dorm_owner_id`;

async function queryTenantReservations(whereClause: string, params: any[]) {
  return pool.query(
    `${TENANT_RESERVATIONS_SELECT} WHERE ${whereClause} ORDER BY r.created_at DESC`,
    params
  );
}

app.get("/reservations/tenant", async (req, res) => {
  const { phone, email } = req.query as { phone?: string; email?: string };

  // Querying by phone or email without owning that identity is a data-leak risk.
  // Require a valid session for all three code-paths.
  const userId = await getUserIdFromToken(req);
  if (userId === null) {
    return res.status(401).json({ message: "Unauthorized" });
  }

  try {
    if (email && String(email).trim().length > 0) {
      const normalizedEmail = String(email).trim();
      const result = await queryTenantReservations(
        "lower(trim(r.tenant_email)) = lower(trim($1))",
        [normalizedEmail]
      );
      return res.json(result.rows);
    }

    if (phone) {
      const digits = String(phone).replace(/\D/g, "");
      const last10 = digits.slice(-10);
      if (last10.length < 7) {
        return res.status(400).json({ message: "Invalid phone number" });
      }

      const result = await queryTenantReservations(
        "regexp_replace(r.phone, '[^0-9]', '', 'g') LIKE $1",
        [`%${last10}`]
      );
      return res.json(result.rows);
    }

    const userResult = await pool.query(
      "SELECT email, full_name FROM users WHERE id = $1",
      [userId]
    );
    if (userResult.rows.length === 0) {
      return res.status(404).json({ message: "User not found" });
    }

    const userEmail = String(userResult.rows[0].email || "").trim();
    const fullName = String(userResult.rows[0].full_name || "").trim();
    const result = await queryTenantReservations(
      `(
         (r.tenant_email IS NOT NULL AND trim(r.tenant_email) <> '' AND lower(trim(r.tenant_email)) = lower(trim($1)))
         OR lower(trim(r.full_name)) = lower(trim($2))
       )`,
      [userEmail, fullName]
    );
    return res.json(result.rows);
  } catch (err) {
    console.error("Tenant reservation lookup error:", err);
    return res.status(500).json({ message: "Database error" });
  }
});

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// GET /reservations/tenant/me -- token-based lookup
// Identifies tenant by Bearer token and matches reservations primarily by
// tenant_email, with full_name fallback for legacy rows.
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
app.get("/reservations/tenant/me", async (req, res) => {
  const userId = await getUserIdFromToken(req);
  if (userId === null) {
    return res.status(401).json({ message: "Unauthorized" });
  }
  try {
    const userResult = await pool.query(
      "SELECT email, full_name FROM users WHERE id = $1",
      [userId]
    );
    if (userResult.rows.length === 0) {
      return res.status(404).json({ message: "User not found" });
    }
    const userEmail = String(userResult.rows[0].email || "").trim();
    const fullName = String(userResult.rows[0].full_name || "").trim();

    const result = await queryTenantReservations(
      `(
         (r.tenant_email IS NOT NULL AND trim(r.tenant_email) <> '' AND lower(trim(r.tenant_email)) = lower(trim($1)))
         OR lower(trim(r.full_name)) = lower(trim($2))
       )`,
      [userEmail, fullName]
    );
    return res.json(result.rows);
  } catch (err) {
    console.error("Tenant/me reservation error:", err);
    return res.status(500).json({ message: "Database error" });
  }
});

app.patch("/reservations/:id/status", async (req, res) => {
  const userId = await getUserIdFromToken(req);
  if (userId === null) {
    return res.status(401).json({ message: "Unauthorized" });
  }
  const { status, rejection_reason } = req.body;
  if (!["pending", "approved", "rejected"].includes(status)) {
    return res.status(400).json({ message: "Invalid status" });
  }
  if (status === 'rejected' && (!rejection_reason || String(rejection_reason).trim().length === 0)) {
    return res.status(400).json({ message: "Rejection reason required" });
  }
  try {
    const rid = Number(req.params.id);
    const result = await pool.query(
      `UPDATE reservations r
       SET status = $1, rejection_reason = $2
       FROM dorms d
       WHERE r.id = $3
         AND (
           r.dorm_owner_id = $4
           OR (r.dorm_owner_id IS NULL AND d.dorm_name = r.dorm_name AND d.user_id = $4)
         )
       RETURNING r.*`,
      [status, status === 'rejected' ? rejection_reason : null, rid, userId]
    );
    if (result.rows.length === 0) {
      return res.status(404).json({ message: "Reservation not found or not authorized" });
    }

    const io = req.app.get('io');
    if (io) {
      const updated = result.rows[0];
      const tenantLookup = await pool.query(
        `SELECT id FROM users
         WHERE (
           $1::text IS NOT NULL AND trim($1::text) <> '' AND lower(trim(email)) = lower(trim($1::text))
         ) OR lower(trim(full_name)) = lower(trim($2::text))
         ORDER BY CASE WHEN lower(trim(email)) = lower(trim($1::text)) THEN 0 ELSE 1 END
         LIMIT 1`,
        [updated.tenant_email ?? null, updated.full_name ?? ""]
      );

      const tenantUserId = tenantLookup.rows[0]?.id;
      if (tenantUserId) {
        notifyUser(io, tenantUserId, 'reservation_updated', {
          reservationId: rid,
          status,
          message: `Reservation ${status === 'approved' ? 'approved' : 'rejected'}`,
        });
      }
    }

    return res.json({ message: "Status updated", reservation: result.rows[0] });
  } catch (err) {
    console.error("Update status error:", err);
    const msg = process.env.NODE_ENV === 'production' ? 'Database error' : (err && (err as any).message) || 'Database error';
    return res.status(500).json({ message: msg });
  }
});

app.patch("/reservations/:id/tenant-action", async (req, res) => {
  const { action, email, phone, cancel_reason } = req.body as {
    action?: string;
    email?: string;
    phone?: string;
    cancel_reason?: string;
  };

  if (!action || !["accepted", "cancelled"].includes(action)) {
    return res.status(400).json({ message: "action must be 'accepted' or 'cancelled'" });
  }
  if (action === "cancelled" && (!cancel_reason || String(cancel_reason).trim().length === 0)) {
    return res.status(400).json({ message: "cancel_reason is required when cancelling" });
  }

  const rid = Number(req.params.id);
  if (isNaN(rid)) {
    return res.status(400).json({ message: "Invalid reservation id" });
  }

  const providedEmail = email ? normalizeEmail(String(email)) : "";
  const digits = phone ? String(phone).replace(/\D/g, "") : "";
  const last10 = digits.slice(-10);

  try {
    const authUserId = await getUserIdFromToken(req);
    let authEmail = "";
    let authFullName = "";

    if (authUserId !== null) {
      const userResult = await pool.query(
        "SELECT email, full_name FROM users WHERE id = $1",
        [authUserId]
      );
      if (userResult.rows.length > 0) {
        authEmail = normalizeEmail(String(userResult.rows[0].email || ""));
        authFullName = String(userResult.rows[0].full_name || "").trim();
      }
    }

    let check;
    if (authEmail) {
      check = await pool.query(
        `SELECT id FROM reservations
         WHERE id = $1
           AND (
             (tenant_email IS NOT NULL AND trim(tenant_email) <> '' AND lower(trim(tenant_email)) = lower(trim($2)))
             OR lower(trim(full_name)) = lower(trim($3))
           )`,
        [rid, authEmail, authFullName]
      );
    } else if (providedEmail) {
      check = await pool.query(
        `SELECT id FROM reservations
         WHERE id = $1
           AND lower(trim(coalesce(tenant_email, ''))) = lower(trim($2))`,
        [rid, providedEmail]
      );
    } else if (last10.length >= 7) {
      check = await pool.query(
        `SELECT id FROM reservations
         WHERE id = $1
           AND regexp_replace(phone, '[^0-9]', '', 'g') LIKE $2`,
        [rid, `%${last10}`]
      );
    } else {
      return res.status(400).json({ message: "email or phone is required" });
    }

    if (check.rows.length === 0) {
      return res.status(404).json({ message: "Reservation not found or tenant mismatch" });
    }

    const result = await pool.query(
      `UPDATE reservations
       SET tenant_action    = $1,
           cancel_reason    = $2,
           tenant_action_at = NOW()
       WHERE id = $3
       RETURNING *`,
      [
        action,
        action === "cancelled" ? String(cancel_reason).trim() : null,
        rid,
      ]
    );

    const io = req.app.get('io');
    if (io && result.rows[0]?.dorm_owner_id) {
      notifyUser(io, result.rows[0].dorm_owner_id, 'reservation_updated', {
        reservationId: rid,
        tenantAction: action,
        message: `Tenant has ${action} the reservation`,
      });
    }

    return res.json({ message: "Tenant action recorded", reservation: result.rows[0] });
  } catch (err) {
    console.error("Tenant action error:", err);
    return res.status(500).json({ message: "Database error" });
  }
});

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// POST /reservations/:id/appeal
// Tenant submits an appeal after termination (archived status).
// Saves appeal text, notifies owner via WebSocket + email.
// Auth: token/email preferred, phone fallback for legacy clients.
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
app.post("/reservations/:id/appeal", async (req, res) => {
  const { email, phone, appeal_message } = req.body as {
    email?: string;
    phone?: string;
    appeal_message?: string;
  };

  if (!appeal_message || String(appeal_message).trim().length === 0) {
    return res.status(400).json({ message: "appeal_message is required" });
  }

  const rid = Number(req.params.id);
  if (isNaN(rid)) {
    return res.status(400).json({ message: "Invalid reservation id" });
  }

  const providedEmail = email ? normalizeEmail(String(email)) : "";
  const digits = phone ? String(phone).replace(/\D/g, "") : "";
  const last10 = digits.slice(-10);

  try {
    const authUserId = await getUserIdFromToken(req);
    let authEmail = "";
    let authFullName = "";

    if (authUserId !== null) {
      const userResult = await pool.query(
        "SELECT email, full_name FROM users WHERE id = $1",
        [authUserId]
      );
      if (userResult.rows.length > 0) {
        authEmail = normalizeEmail(String(userResult.rows[0].email || ""));
        authFullName = String(userResult.rows[0].full_name || "").trim();
      }
    }

    let check;
    if (authEmail) {
      check = await pool.query(
        `SELECT r.*, u.full_name AS owner_name, u.email AS owner_email
         FROM reservations r
         LEFT JOIN users u ON u.id = r.dorm_owner_id
         WHERE r.id = $1
           AND (
             (r.tenant_email IS NOT NULL AND trim(r.tenant_email) <> '' AND lower(trim(r.tenant_email)) = lower(trim($2)))
             OR lower(trim(r.full_name)) = lower(trim($3))
           )`,
        [rid, authEmail, authFullName]
      );
    } else if (providedEmail) {
      check = await pool.query(
        `SELECT r.*, u.full_name AS owner_name, u.email AS owner_email
         FROM reservations r
         LEFT JOIN users u ON u.id = r.dorm_owner_id
         WHERE r.id = $1
           AND lower(trim(coalesce(r.tenant_email, ''))) = lower(trim($2))`,
        [rid, providedEmail]
      );
    } else if (last10.length >= 7) {
      check = await pool.query(
        `SELECT r.*, u.full_name AS owner_name, u.email AS owner_email
         FROM reservations r
         LEFT JOIN users u ON u.id = r.dorm_owner_id
         WHERE r.id = $1
           AND regexp_replace(r.phone, '[^0-9]', '', 'g') LIKE $2`,
        [rid, `%${last10}`]
      );
    } else {
      return res.status(400).json({ message: "email or phone is required" });
    }

    if (check.rows.length === 0) {
      return res.status(404).json({ message: "Reservation not found or tenant mismatch" });
    }

    const reservation = check.rows[0];

    if (reservation.status !== "archived") {
      return res.status(400).json({ message: "Appeals can only be submitted for terminated reservations" });
    }

    if (reservation.appeal_dismissed_at) {
      return res.status(403).json({
        message: "This appeal was dismissed by the owner and cannot be submitted again",
      });
    }

    if (reservation.appeal_message && reservation.appeal_submitted_at) {
      return res.status(409).json({
        message: "You already submitted an appeal for this reservation",
      });
    }

    const trimmedAppeal = String(appeal_message).trim();

    // Persist the appeal on the reservation row
    const result = await pool.query(
      `UPDATE reservations
       SET appeal_message = $1, appeal_submitted_at = NOW()
       WHERE id = $2
       RETURNING *`,
      [trimmedAppeal, rid]
    );

    // â”€â”€ Notify owner via WebSocket â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    const io = req.app.get('io');
    if (io && reservation.dorm_owner_id) {
      notifyUser(io, reservation.dorm_owner_id, 'appeal_submitted', {
        reservationId: rid,
        message: `New appeal from ${reservation.full_name}`,
      });
    }

    // â”€â”€ Send email to owner â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    let emailSent = false;
    let emailMessage = "Owner email not available";

    const ownerEmail = String(reservation.owner_email || "").trim().toLowerCase() || null;
    if (ownerEmail) {
      const transporter = getSmtpTransporter();
      const fromAddress = process.env.SMTP_FROM || process.env.SMTP_USER;

      if (transporter && fromAddress) {
        const escapeHtml = (v: string) =>
          String(v || "")
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;");

        const submittedAt = new Date().toLocaleDateString("en-US", {
          year: "numeric",
          month: "long",
          day: "numeric",
          hour: "2-digit",
          minute: "2-digit",
        });

        try {
          await transporter.sendMail({
            from: fromAddress,
            to: ownerEmail,
            subject: `DormEase Tenancy Appeal from ${reservation.full_name}`,
            text: [
              `Hello ${reservation.owner_name || "Dorm Owner"},`,
              "",
              `${reservation.full_name} has submitted an appeal regarding their terminated tenancy at ${reservation.dorm_name}.`,
              "",
              "Appeal Message:",
              trimmedAppeal,
              "",
              `Submitted: ${submittedAt}`,
              "",
              "Please log in to DormEase to review and respond to this appeal.",
              "",
              "Best regards,",
              "DormEase",
            ].join("\n"),
            html: `
              <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background: #2979FF; color: white; padding: 20px; border-radius: 8px 8px 0 0; text-align: center;">
                  <h2 style="margin: 0;">ðŸ“‹ Tenancy Appeal Received</h2>
                </div>
                <div style="padding: 24px; background: #f9f9f9; border: 1px solid #e8e8e8; border-radius: 0 0 8px 8px;">
                  <p>Hello <strong>${escapeHtml(reservation.owner_name || "Dorm Owner")}</strong>,</p>
                  <p>
                    <strong>${escapeHtml(reservation.full_name)}</strong> has submitted an appeal
                    regarding their terminated tenancy at <strong>${escapeHtml(reservation.dorm_name)}</strong>.
                  </p>
                  <div style="background: #eef4fd; border-left: 4px solid #2979FF; border-radius: 4px; padding: 16px; margin: 20px 0;">
                    <p style="margin: 0 0 8px 0; font-weight: 600; color: #2979FF;">Appeal Message:</p>
                    <p style="margin: 0; white-space: pre-wrap;">${escapeHtml(trimmedAppeal)}</p>
                  </div>
                  <table style="width: 100%; border-collapse: collapse; margin-bottom: 20px;">
                    <tr>
                      <td style="padding: 8px; color: #666; width: 40%;">Tenant</td>
                      <td style="padding: 8px; font-weight: 600;">${escapeHtml(reservation.full_name)}</td>
                    </tr>
                    <tr style="background: #f3f3f3;">
                      <td style="padding: 8px; color: #666;">Property</td>
                      <td style="padding: 8px; font-weight: 600;">${escapeHtml(reservation.dorm_name)}</td>
                    </tr>
                    <tr>
                      <td style="padding: 8px; color: #666;">Submitted</td>
                      <td style="padding: 8px;">${escapeHtml(submittedAt)}</td>
                    </tr>
                  </table>
                  <p>Please log in to <strong>DormEase</strong> to review and respond to this appeal.</p>
                  <hr style="border: none; border-top: 1px solid #e8e8e8; margin: 20px 0;"/>
                  <p style="color: #999; font-size: 12px; text-align: center;">This is an automated message from DormEase.</p>
                </div>
              </div>`,
          });
          emailSent = true;
          emailMessage = "Appeal notification sent to owner";
        } catch (emailErr: any) {
          emailMessage = `Failed to send email: ${emailErr.message || "Unknown error"}`;
          console.error("Appeal email error:", emailErr.message);
        }
      } else {
        emailMessage = "SMTP not configured";
      }
    }

    console.log(`ðŸ“‹ Appeal submitted for reservation #${rid} by ${reservation.full_name}. Email: ${emailSent}`);

    return res.json({
      message: "Appeal submitted successfully. The owner has been notified.",
      reservation: result.rows[0],
      emailSent,
      emailMessage,
    });
  } catch (err) {
    console.error("Appeal error:", err);
    return res.status(500).json({ message: "Database error" });
  }
});

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// PATCH /reservations/:id/dismiss-appeal
// Owner dismisses (acknowledges) a tenant appeal, removing it from
// the notification stream. Clears current appeal payload and stores
// appeal_dismissed_at so the tenant cannot submit another appeal.
// Auth: Bearer token (owner only).
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
app.patch("/reservations/:id/dismiss-appeal", async (req, res) => {
  const userId = await getUserIdFromToken(req);
  if (userId === null) {
    return res.status(401).json({ message: "Unauthorized" });
  }
  try {
    const rid = Number(req.params.id);
    if (isNaN(rid)) {
      return res.status(400).json({ message: "Invalid reservation id" });
    }
    const result = await pool.query(
      `UPDATE reservations
       SET appeal_message = NULL,
           appeal_submitted_at = NULL,
           appeal_dismissed_at = COALESCE(appeal_dismissed_at, NOW())
       WHERE id = $1 AND dorm_owner_id = $2
       RETURNING id`,
      [rid, userId]
    );
    if (result.rows.length === 0) {
      return res.status(404).json({ message: "Reservation not found or not authorized" });
    }
    console.log(`ðŸ“‹ Appeal dismissed for reservation #${rid} by owner ${userId}`);
    return res.json({ message: "Appeal dismissed" });
  } catch (err) {
    console.error("Dismiss appeal error:", err);
    return res.status(500).json({ message: "Database error" });
  }
});

app.patch("/reservations/:id/mark-payment-paid", async (req, res) => {
  const userId = await getUserIdFromToken(req);
  if (userId === null) {
    return res.status(401).json({ message: "Unauthorized" });
  }
  try {
    const rid = Number(req.params.id);
    const { paymentNumber, paymentSource } = req.body as {
      paymentNumber?: number;
      paymentSource?: "advance" | "deposit" | "monthly";
    };
    const parsedPaymentNumber = Number(paymentNumber);

    if (!Number.isInteger(parsedPaymentNumber) || parsedPaymentNumber < 1) {
      return res.status(400).json({ message: "Invalid payment number" });
    }

    const current = await pool.query(
      `SELECT
         r.payments_paid,
         r.duration_months,
         r.advance_used,
         r.deposit_used,
         r.full_name,
         r.phone,
         r.dorm_name,
         r.price_per_month,
         r.advance,
         r.deposit,
         r.move_in_date,
         r.tenant_email,
         r.receipt_history,
         d.address AS dorm_address,
         d.phone AS dorm_phone,
         d.email AS dorm_email,
         d.dorm_name AS dorm_owner_name,
         tenant_lookup.email AS tenant_email_from_user,
         reservation_lookup.tenant_email AS tenant_email_from_other_reservation
       FROM reservations r
       LEFT JOIN dorms d ON d.user_id = r.dorm_owner_id
       LEFT JOIN LATERAL (
         SELECT u.email
         FROM users u
         WHERE lower(trim(u.full_name)) = lower(trim(r.full_name))
         ORDER BY u.id DESC
         LIMIT 1
       ) tenant_lookup ON true
       LEFT JOIN LATERAL (
         SELECT rr.tenant_email
         FROM reservations rr
         WHERE rr.id <> r.id
           AND rr.tenant_email IS NOT NULL
           AND trim(rr.tenant_email) <> ''
           AND (
             lower(trim(rr.full_name)) = lower(trim(r.full_name))
             OR trim(rr.phone) = trim(r.phone)
           )
         ORDER BY rr.id DESC
         LIMIT 1
       ) reservation_lookup ON true
       WHERE r.id = $1 AND r.dorm_owner_id = $2`,
      [rid, userId]
    );

    if (current.rows.length === 0) {
      return res.status(404).json({ message: "Reservation not found or not authorized" });
    }

    const currentPaid    = current.rows[0].payments_paid || 0;
    const totalPayments  = current.rows[0].duration_months;
    const advanceUsed    = current.rows[0].advance_used || false;
    const depositUsed    = current.rows[0].deposit_used || false;
    const paymentSourceValue: "monthly" | "advance" | "deposit" =
      paymentSource === "advance" || paymentSource === "deposit" ? paymentSource : "monthly";

    if (paymentSourceValue === "advance" && advanceUsed) {
      return res.status(400).json({ message: "Advance has already been used" });
    }
    if (paymentSourceValue === "deposit" && depositUsed) {
      return res.status(400).json({ message: "Deposit has already been used" });
    }
    if (parsedPaymentNumber !== currentPaid + 1) {
      return res.status(400).json({
        message: `Can only mark payment #${currentPaid + 1} as paid. Please pay in order.`
      });
    }
    if (parsedPaymentNumber > totalPayments) {
      return res.status(400).json({ message: "Payment number exceeds contract duration" });
    }

    const paymentDate = new Date();
    const moveInDate = parseReservationDate(String(current.rows[0].move_in_date || ""));
    const nextPaymentDueDate =
      moveInDate && parsedPaymentNumber < totalPayments
        ? new Date(moveInDate.getFullYear(), moveInDate.getMonth() + parsedPaymentNumber, moveInDate.getDate())
        : null;

    const amountPaid =
      paymentSourceValue === 'advance'
        ? Number(current.rows[0].advance || 0)
        : paymentSourceValue === 'deposit'
          ? Number(current.rows[0].deposit || 0)
          : Number(current.rows[0].price_per_month || 0);

    const receiptRecord: PaymentReceiptRecord = {
      paymentNumber: parsedPaymentNumber,
      paymentSource: paymentSourceValue,
      tenantName: String(current.rows[0].full_name || "Tenant"),
      dormitory: String(current.rows[0].dorm_name || "Dormitory"),
      amountPaid,
      paymentDate: paymentDate.toISOString(),
      nextPaymentDueDate: nextPaymentDueDate ? nextPaymentDueDate.toISOString() : null,
    };

    const tenantEmailFromReservation = String(current.rows[0].tenant_email || "").trim().toLowerCase();
    const tenantEmailFromOtherReservation = String(current.rows[0].tenant_email_from_other_reservation || "").trim().toLowerCase();
    const tenantEmailFromLookup = String(current.rows[0].tenant_email_from_user || "").trim().toLowerCase();
    const tenantEmail = tenantEmailFromReservation || tenantEmailFromOtherReservation || tenantEmailFromLookup || null;

    console.log("ðŸ“§ Payment Email Debug:", {
      reservationId: rid,
      tenantName: current.rows[0].full_name,
      tenantEmailFromReservation,
      tenantEmailFromOtherReservation,
      tenantEmailFromLookup,
      finalTenantEmail: tenantEmail,
      willSendEmail: !!tenantEmail
    });

    let updateQuery = "UPDATE reservations SET payments_paid = $1, receipt_history = COALESCE(receipt_history, '[]'::jsonb) || $2::jsonb";
    const params: any[] = [parsedPaymentNumber, JSON.stringify([receiptRecord])];
    if (paymentSourceValue === "advance") updateQuery += `, advance_used = true`;
    else if (paymentSourceValue === "deposit") updateQuery += `, deposit_used = true`;
    if (tenantEmail) {
      updateQuery += `, tenant_email = COALESCE(tenant_email, $3)`;
      params.push(tenantEmail);
      updateQuery += ` WHERE id = $4 AND dorm_owner_id = $5 RETURNING *`;
      params.push(rid, userId);
    } else {
      updateQuery += ` WHERE id = $3 AND dorm_owner_id = $4 RETURNING *`;
      params.push(rid, userId);
    }

    const result = await pool.query(updateQuery, params);

    let emailSent = false;
    let emailMessage = "Tenant email is not available";
    if (tenantEmail) {
      console.log("ðŸ“§ Attempting to send payment email to:", tenantEmail);
      const paymentSourceLabel =
        paymentSourceValue === 'advance'
          ? 'Advance'
          : paymentSourceValue === 'deposit'
            ? 'Deposit'
            : 'Monthly';
      const emailResult = await sendPaymentConfirmationEmail({
        tenantEmail,
        tenantName: receiptRecord.tenantName,
        tenantPhone: String(current.rows[0].phone || 'N/A'),
        dormitory: receiptRecord.dormitory,
        dormAddress: String(current.rows[0].dorm_address || 'N/A'),
        dormPhone: String(current.rows[0].dorm_phone || ''),
        dormEmail: String(current.rows[0].dorm_email || 'N/A'),
        ownerName: String(current.rows[0].dorm_owner_name || receiptRecord.dormitory) + ' Owner',
        paymentNumber: parsedPaymentNumber,
        paymentSource: paymentSourceLabel,
        amountPaid: receiptRecord.amountPaid,
        paymentDate,
        nextPaymentDueDate,
      });
      emailSent = emailResult.sent;
      emailMessage = emailResult.message;
      console.log("ðŸ“§ Email result:", { emailSent, emailMessage });
    } else {
      console.log("ðŸ“§ Skipping email - no tenant email found");
    }

    return res.json({
      message: emailSent
        ? "Payment marked as paid and confirmation email sent"
        : "Payment marked as paid",
      reservation: result.rows[0],
      receipt: receiptRecord,
      emailSent,
      emailMessage,
    });
  } catch (err) {
    console.error("Mark payment paid error:", err);
    return res.status(500).json({ message: "Database error" });
  }
});

app.patch("/reservations/:id/archive", async (req, res) => {
  const userId = await getUserIdFromToken(req);
  if (userId === null) {
    return res.status(401).json({ message: "Unauthorized" });
  }
  const { termination_reason } = req.body as { termination_reason?: string };
  try {
    const rid = Number(req.params.id);
    const result = await pool.query(
      "UPDATE reservations SET status = $1, termination_reason = $2 WHERE id = $3 AND dorm_owner_id = $4 RETURNING *",
      ["archived", termination_reason || null, rid, userId]
    );
    if (result.rows.length === 0) {
      return res.status(404).json({ message: "Reservation not found or not authorized" });
    }

    const reservation = result.rows[0];

    // ── Send termination email to tenant ──
    let emailSent = false;
    let emailMessage = "Tenant email is not available";

    // Resolve tenant email: reservation.tenant_email → lookup by name → lookup by phone
    let tenantEmail = String(reservation.tenant_email || "").trim().toLowerCase() || null;
    if (!tenantEmail) {
      const emailLookup = await pool.query(
        `SELECT email FROM users WHERE lower(trim(full_name)) = lower(trim($1)) ORDER BY id DESC LIMIT 1`,
        [reservation.full_name]
      );
      if (emailLookup.rows.length > 0) {
        tenantEmail = emailLookup.rows[0].email;
      }
    }

    if (tenantEmail) {
      const transporter = getSmtpTransporter();
      const fromAddress = process.env.SMTP_FROM || process.env.SMTP_USER;

      if (transporter && fromAddress) {
        const ownerResult = await pool.query("SELECT full_name FROM users WHERE id = $1", [userId]);
        const ownerName = ownerResult.rows[0]?.full_name || "Dorm Owner";
        const reasonText = termination_reason ? termination_reason : "No reason provided";

        const escapeHtml = (v: string) => String(v || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

        try {
          await transporter.sendMail({
            from: fromAddress,
            to: tenantEmail,
            subject: `DormEase – Your tenancy at ${reservation.dorm_name} has been terminated`,
            text: [
              `Hello ${reservation.full_name},`,
              "",
              `We regret to inform you that your tenancy at ${reservation.dorm_name} has been terminated by the dorm owner (${ownerName}).`,
              "",
              `Reason: ${reasonText}`,
              "",
              `Termination Date: ${new Date().toLocaleDateString("en-US", { year: "numeric", month: "long", day: "numeric" })}`,
              "",
              "If you have any concerns, please contact the dorm owner directly.",
              "",
              "Best regards,",
              "DormEase",
            ].join("\n"),
            html: `
              <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background: #ff4d4f; color: white; padding: 20px; border-radius: 8px 8px 0 0; text-align: center;">
                  <h2 style="margin: 0;">Tenancy Terminated</h2>
                </div>
                <div style="padding: 24px; background: #f9f9f9; border: 1px solid #e8e8e8; border-radius: 0 0 8px 8px;">
                  <p>Hello <strong>${escapeHtml(reservation.full_name)}</strong>,</p>
                  <p>We regret to inform you that your tenancy at <strong>${escapeHtml(reservation.dorm_name)}</strong> has been terminated by the dorm owner (<strong>${escapeHtml(ownerName)}</strong>).</p>
                  <div style="background: #fff2f0; border: 1px solid #ffccc7; border-radius: 6px; padding: 16px; margin: 16px 0;">
                    <strong>Reason:</strong><br/>
                    <p style="margin: 8px 0 0 0;">${escapeHtml(reasonText)}</p>
                  </div>
                  <p><strong>Termination Date:</strong> ${new Date().toLocaleDateString("en-US", { year: "numeric", month: "long", day: "numeric" })}</p>
                  <p style="margin-top: 16px;">If you have any concerns, please contact the dorm owner directly.</p>
                  <hr style="border: none; border-top: 1px solid #e8e8e8; margin: 20px 0;"/>
                  <p style="color: #999; font-size: 12px; text-align: center;">This is an automated message from DormEase.</p>
                </div>
              </div>`,
          });
          emailSent = true;
          emailMessage = "Termination email sent to tenant";
          console.log(`📧 Termination email sent to ${tenantEmail} for reservation #${rid}`);
        } catch (emailErr: any) {
          emailMessage = `Failed to send email: ${emailErr.message || "Unknown error"}`;
          console.error("📧 Termination email error:", emailErr.message);
        }
      } else {
        emailMessage = "SMTP not configured";
      }
    }

    // ── Record final payment summary in payment_history ──
    const paymentsPaid = reservation.payments_paid || 0;
    const totalPayments = reservation.duration_months || 0;
    const unpaidCount = totalPayments - paymentsPaid;

    if (unpaidCount > 0) {
      try {
        // Find tenant user id for payment_history
        let tenantUserId: number | null = null;
        const tenantLookup = await pool.query(
          "SELECT id FROM users WHERE lower(trim(full_name)) = lower(trim($1)) ORDER BY id DESC LIMIT 1",
          [reservation.full_name]
        );
        if (tenantLookup.rows.length > 0) {
          tenantUserId = tenantLookup.rows[0].id;
        }

        await pool.query(
          `INSERT INTO payment_history
            (owner_id, tenant_id, reservation_id, tenant_name, dorm_name, amount, payment_source, payment_number, status)
          VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)`,
          [
            userId,
            tenantUserId,
            rid,
            reservation.full_name,
            reservation.dorm_name,
            0,
            "monthly",
            paymentsPaid,
            "paid",
          ]
        );
        console.log(`📋 Payment history termination record created for reservation #${rid} (${paymentsPaid}/${totalPayments} paid, ${unpaidCount} unpaid)`);
      } catch (phErr: any) {
        console.error("Payment history termination record error:", phErr.message);
      }
    }

    return res.json({
      message: "Archived",
      reservation,
      emailSent,
      emailMessage,
      paymentSummary: {
        paid: paymentsPaid,
        total: totalPayments,
        unpaid: unpaidCount,
      },
    });
  } catch (err) {
    console.error("Archive reservation error:", err);
    const msg = process.env.NODE_ENV === 'production' ? 'Database error' : (err && (err as any).message) || 'Database error';
    return res.status(500).json({ message: msg });
  }
});

app.patch("/reservations/:id/unarchive", async (req, res) => {
  const userId = await getUserIdFromToken(req);
  if (userId === null) {
    return res.status(401).json({ message: "Unauthorized" });
  }
  try {
    const rid = Number(req.params.id);
    const result = await pool.query(
      "UPDATE reservations SET status = $1 WHERE id = $2 AND dorm_owner_id = $3 RETURNING *",
      ["pending", rid, userId]
    );
    if (result.rows.length === 0) {
      return res.status(404).json({ message: "Reservation not found or not authorized" });
    }
    return res.json({ message: "Unarchived", reservation: result.rows[0] });
  } catch (err) {
    console.error("Unarchive reservation error:", err);
    const msg = process.env.NODE_ENV === 'production' ? 'Database error' : (err && (err as any).message) || 'Database error';
    return res.status(500).json({ message: msg });
  }
});

app.patch("/reservations/:id/extend-duration", async (req, res) => {
  const userId = await getUserIdFromToken(req);
  if (userId === null) {
    return res.status(401).json({ message: "Unauthorized" });
  }
  const { additional_months } = req.body as { additional_months?: number };
  if (!additional_months || additional_months <= 0) {
    return res.status(400).json({ message: "additional_months must be a positive number" });
  }
  try {
    const rid = Number(req.params.id);
    const current = await pool.query(
      "SELECT duration_months FROM reservations WHERE id = $1 AND dorm_owner_id = $2",
      [rid, userId]
    );
    if (current.rows.length === 0) {
      return res.status(404).json({ message: "Reservation not found or not authorized" });
    }
    const newDuration = (current.rows[0].duration_months || 0) + additional_months;
    const result = await pool.query(
      "UPDATE reservations SET duration_months = $1 WHERE id = $2 AND dorm_owner_id = $3 RETURNING *",
      [newDuration, rid, userId]
    );
    return res.json({ message: `Contract extended by ${additional_months} month(s)`, reservation: result.rows[0] });
  } catch (err) {
    console.error("Extend duration error:", err);
    const msg = process.env.NODE_ENV === 'production' ? 'Database error' : (err && (err as any).message) || 'Database error';
    return res.status(500).json({ message: msg });
  }
});

app.delete("/reservations/:id", async (req, res) => {
  const userId = await getUserIdFromToken(req);
  if (userId === null) {
    return res.status(401).json({ message: "Unauthorized" });
  }
  try {
    const rid = Number(req.params.id);
    const result = await pool.query(
      "DELETE FROM reservations WHERE id = $1 AND dorm_owner_id = $2 RETURNING id",
      [rid, userId]
    );
    if (result.rows.length === 0) {
      return res.status(404).json({ message: "Reservation not found or not authorized" });
    }
    return res.json({ message: "Deleted" });
  } catch (err) {
    console.error("Delete reservation error:", err);
    const msg = process.env.NODE_ENV === 'production' ? 'Database error' : (err && (err as any).message) || 'Database error';
    return res.status(500).json({ message: msg });
  }
});

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// PAYMENT HISTORY
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

app.get("/payment-history", async (req, res) => {
  const userId = await getUserIdFromToken(req);
  if (userId === null) {
    return res.status(401).json({ message: "Unauthorized" });
  }

  try {
    const { sortBy = "payment_date", order = "DESC", limit = 100, offset = 0 } = req.query;
    const sortColumn = ["payment_date", "amount", "status", "tenant_name", "dorm_name"].includes(String(sortBy)) ? String(sortBy) : "payment_date";
    const sortOrder = String(order).toUpperCase() === "ASC" ? "ASC" : "DESC";
    const pageLimit = Math.min(Number(limit) || 100, 500);
    const pageOffset = Math.max(Number(offset) || 0, 0);

    const result = await pool.query(
      `SELECT
        ph.id, ph.owner_id, ph.tenant_id, ph.reservation_id, ph.tenant_name, ph.dorm_name,
        ph.amount, ph.payment_source, ph.payment_number, ph.payment_date, ph.status, ph.created_at,
        CASE
          WHEN r.id IS NOT NULL
           AND r.status = 'approved'
           AND r.tenant_action = 'accepted'
          THEN 'current'
          ELSE 'old'
        END AS tenant_type
      FROM payment_history ph
      LEFT JOIN reservations r ON r.id = ph.reservation_id
      WHERE ph.owner_id = $1 OR ph.tenant_id = $1
      ORDER BY ph.${sortColumn} ${sortOrder}
      LIMIT $2 OFFSET $3`,
      [userId, pageLimit, pageOffset]
    );

    const countResult = await pool.query(
      "SELECT COUNT(*) FROM payment_history WHERE owner_id = $1 OR tenant_id = $1",
      [userId]
    );

    return res.json({
      payments: result.rows,
      total: Number(countResult.rows[0].count),
      limit: pageLimit,
      offset: pageOffset,
    });
  } catch (err) {
    console.error("Get payment history error:", err);
    const msg = process.env.NODE_ENV === 'production' ? 'Database error' : (err && (err as any).message) || 'Database error';
    return res.status(500).json({ message: msg });
  }
});

app.post("/payment-history", async (req, res) => {
  const userId = await getUserIdFromToken(req);
  if (userId === null) {
    return res.status(401).json({ message: "Unauthorized" });
  }

  try {
    const { tenant_id, reservation_id, tenant_name, dorm_name, amount, payment_source, payment_number, status } = req.body;

    if (!tenant_name || !dorm_name || !amount || !payment_source) {
      return res.status(400).json({ message: "Missing required fields" });
    }

    const result = await pool.query(
      `INSERT INTO payment_history 
        (owner_id, tenant_id, reservation_id, tenant_name, dorm_name, amount, payment_source, payment_number, status)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
      RETURNING *`,
      [userId, tenant_id || null, reservation_id || null, tenant_name, dorm_name, amount, payment_source, payment_number || null, status || 'paid']
    );

    return res.status(201).json({ payment: result.rows[0], message: "Payment recorded" });
  } catch (err) {
    console.error("Create payment history error:", err);
    const msg = process.env.NODE_ENV === 'production' ? 'Database error' : (err && (err as any).message) || 'Database error';
    return res.status(500).json({ message: msg });
  }
});

app.get("/payment-history/stats", async (req, res) => {
  const userId = await getUserIdFromToken(req);
  if (userId === null) {
    return res.status(401).json({ message: "Unauthorized" });
  }

  try {
    const statsResult = await pool.query(
      `SELECT 
        COUNT(*) as total_payments,
        SUM(CASE WHEN status = 'paid' THEN amount ELSE 0 END) as total_paid,
        AVG(amount) as avg_payment
      FROM payment_history
      WHERE owner_id = $1 OR tenant_id = $1`,
      [userId]
    );

    // Calculate pending from active reservations (unpaid months Ã— price_per_month)
    const pendingResult = await pool.query(
      `SELECT COALESCE(SUM((duration_months - payments_paid) * price_per_month), 0) as total_pending
       FROM reservations
       WHERE dorm_owner_id = $1
         AND status IN ('approved', 'pending')
         AND payments_paid < duration_months`,
      [userId]
    );

    const stats = {
      ...statsResult.rows[0],
      total_pending: pendingResult.rows[0].total_pending,
    };

    const monthlyResult = await pool.query(
      `SELECT 
        DATE_TRUNC('month', payment_date)::date as month,
        SUM(amount) as total_amount,
        COUNT(*) as payment_count
      FROM payment_history
      WHERE (owner_id = $1 OR tenant_id = $1) AND status = 'paid'
      GROUP BY DATE_TRUNC('month', payment_date)
      ORDER BY month DESC
      LIMIT 12`,
      [userId]
    );

    return res.json({
      stats,
      monthly: monthlyResult.rows,
    });
  } catch (err) {
    console.error("Get payment stats error:", err);
    const msg = process.env.NODE_ENV === 'production' ? 'Database error' : (err && (err as any).message) || 'Database error';
    return res.status(500).json({ message: msg });
  }
});

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// SCHEMA MIGRATION
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

async function ensureSchema(): Promise<void> {
  try {
    await pool.query(`
      CREATE TABLE IF NOT EXISTS messages (
        id serial PRIMARY KEY,
        sender_id integer NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        recipient_id integer NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        message text NOT NULL,
        sender_deleted_at timestamptz,
        recipient_deleted_at timestamptz,
        created_at timestamp DEFAULT current_timestamp
      );
    `);
    await pool.query("CREATE INDEX IF NOT EXISTS idx_messages_sender_recipient_created ON messages(sender_id, recipient_id, created_at);");
    await pool.query("CREATE INDEX IF NOT EXISTS idx_messages_recipient_sender_created ON messages(recipient_id, sender_id, created_at);");

    // Existing columns
    await pool.query("ALTER TABLE reservations ADD COLUMN IF NOT EXISTS rejection_reason text;");
    await pool.query("ALTER TABLE reservations ADD COLUMN IF NOT EXISTS tenant_action text;");
    await pool.query("ALTER TABLE reservations ADD COLUMN IF NOT EXISTS cancel_reason text;");
    await pool.query("ALTER TABLE reservations ADD COLUMN IF NOT EXISTS tenant_action_at timestamptz;");
    await pool.query("ALTER TABLE reservations ADD COLUMN IF NOT EXISTS termination_reason text;");

    // â”€â”€ Columns required by TenantDashboardActivity â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    await pool.query("ALTER TABLE dorms ADD COLUMN IF NOT EXISTS latitude double precision;");
    await pool.query("ALTER TABLE dorms ADD COLUMN IF NOT EXISTS longitude double precision;");

    // â”€â”€ Columns required by TenantDashboardActivity â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    await pool.query("ALTER TABLE reservations ADD COLUMN IF NOT EXISTS payments_paid integer NOT NULL DEFAULT 0;");
    await pool.query("ALTER TABLE reservations ADD COLUMN IF NOT EXISTS advance_used boolean NOT NULL DEFAULT false;");
    await pool.query("ALTER TABLE reservations ADD COLUMN IF NOT EXISTS deposit_used boolean NOT NULL DEFAULT false;");
    await pool.query("ALTER TABLE reservations ADD COLUMN IF NOT EXISTS tenant_email varchar(255);");
    await pool.query("ALTER TABLE reservations ADD COLUMN IF NOT EXISTS receipt_history jsonb NOT NULL DEFAULT '[]'::jsonb;");

    // â”€â”€ Appeal columns (NEW) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    await pool.query("ALTER TABLE reservations ADD COLUMN IF NOT EXISTS appeal_message text;");
    await pool.query("ALTER TABLE reservations ADD COLUMN IF NOT EXISTS appeal_submitted_at timestamptz;");
    await pool.query("ALTER TABLE reservations ADD COLUMN IF NOT EXISTS appeal_dismissed_at timestamptz;");

    // â”€â”€ Payment History Table â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    await pool.query(`
      CREATE TABLE IF NOT EXISTS payment_history (
        id serial PRIMARY KEY,
        owner_id integer NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        tenant_id integer REFERENCES users(id) ON DELETE SET NULL,
        reservation_id integer REFERENCES reservations(id) ON DELETE CASCADE,
        tenant_name varchar(255) NOT NULL,
        dorm_name varchar(255) NOT NULL,
        amount decimal(10, 2) NOT NULL,
        payment_source varchar(50) NOT NULL CHECK (payment_source IN ('monthly', 'advance', 'deposit')),
        payment_number integer,
        payment_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
        status varchar(50) NOT NULL DEFAULT 'paid' CHECK (status IN ('paid', 'pending', 'overdue')),
        created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
      );
    `);
    await pool.query("CREATE INDEX IF NOT EXISTS idx_payment_history_owner ON payment_history(owner_id);");
    await pool.query("CREATE INDEX IF NOT EXISTS idx_payment_history_tenant ON payment_history(tenant_id);");
    await pool.query("CREATE INDEX IF NOT EXISTS idx_payment_history_reservation ON payment_history(reservation_id);");
    await pool.query("CREATE INDEX IF NOT EXISTS idx_payment_history_date ON payment_history(payment_date);");
    await pool.query("CREATE INDEX IF NOT EXISTS idx_payment_history_status ON payment_history(status);");

    console.log('âœ… Schema up to date');
  } catch (err) {
    console.error('Error ensuring schema:', err);
  }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// START
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

async function startServer() {
  await ensureSchema();

  const httpServer = http.createServer(app);
  const io = initializeWebSocket(httpServer);

  // @ts-ignore
  httpServer._socketio = io;
  app.set('io', io);

  httpServer.listen(PORT, () => {
    console.log(` Server running at http://192.168.1.20/:${PORT}`);
    console.log(` WebSocket server is ready`);
  });
}

startServer();

