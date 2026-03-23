-- Add payments_paid column to reservations table
ALTER TABLE reservations 
ADD COLUMN IF NOT EXISTS payments_paid INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN reservations.payments_paid IS 'Number of payments that have been marked as paid';
