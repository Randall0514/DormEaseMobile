-- Add advance_used and deposit_used columns to reservations table if they don't exist
DO $$ 
BEGIN
    -- Add advance_used column if it doesn't exist
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name='reservations' AND column_name='advance_used'
    ) THEN
        ALTER TABLE reservations 
        ADD COLUMN advance_used BOOLEAN NOT NULL DEFAULT false;
        
        COMMENT ON COLUMN reservations.advance_used IS 'Whether the advance payment has been used for a monthly payment';
    END IF;

    -- Add deposit_used column if it doesn't exist
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name='reservations' AND column_name='deposit_used'
    ) THEN
        ALTER TABLE reservations 
        ADD COLUMN deposit_used BOOLEAN NOT NULL DEFAULT false;
        
        COMMENT ON COLUMN reservations.deposit_used IS 'Whether the deposit has been used for a monthly payment';
    END IF;
END $$;
