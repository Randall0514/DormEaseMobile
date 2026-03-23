-- Create reservations table
CREATE TABLE IF NOT EXISTS reservations (
    id               SERIAL PRIMARY KEY,
    dorm_name        VARCHAR(255) NOT NULL,
    location         VARCHAR(255),
    full_name        VARCHAR(255) NOT NULL,
    phone            VARCHAR(20)  NOT NULL,
    move_in_date     VARCHAR(20)  NOT NULL,
    duration_months  INT          NOT NULL,
    price_per_month  INT          NOT NULL DEFAULT 0,
    deposit          INT          NOT NULL DEFAULT 0,
    advance          INT          NOT NULL DEFAULT 0,
    total_amount     INT          NOT NULL DEFAULT 0,
    notes            TEXT,
    payment_method   VARCHAR(50)  NOT NULL DEFAULT 'cash_on_move_in',
    status           VARCHAR(20)  NOT NULL DEFAULT 'pending',
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);
