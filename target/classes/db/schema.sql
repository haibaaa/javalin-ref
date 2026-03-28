-- TCCS Schema - idempotent, runs on startup

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Users
CREATE TABLE IF NOT EXISTS users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    branch_location VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE,
    last_login TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Trucks
CREATE TABLE IF NOT EXISTS trucks (
    truck_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    registration_number VARCHAR(20) UNIQUE NOT NULL,
    capacity DECIMAL(10,2) NOT NULL,
    driver_name VARCHAR(100) NOT NULL,
    driver_license VARCHAR(50) NOT NULL,
    status VARCHAR(30) DEFAULT 'Available',
    current_location VARCHAR(100),
    cargo_volume DECIMAL(10,2) DEFAULT 0,
    destination VARCHAR(100),
    status_history JSONB DEFAULT '[]'::JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Pricing Rules
CREATE TABLE IF NOT EXISTS pricing_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    destination VARCHAR(100) NOT NULL,
    rate_per_cubic_meter DECIMAL(10,2) NOT NULL,
    minimum_charge DECIMAL(10,2) NOT NULL,
    effective_date DATE NOT NULL,
    expiry_date DATE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Consignments
CREATE TABLE IF NOT EXISTS consignments (
    consignment_number VARCHAR(30) PRIMARY KEY,
    volume DECIMAL(10,2) NOT NULL,
    destination VARCHAR(100) NOT NULL,
    sender_address TEXT NOT NULL,
    receiver_address TEXT NOT NULL,
    registration_timestamp TIMESTAMPTZ DEFAULT NOW(),
    status VARCHAR(30) DEFAULT 'Registered',
    assigned_truck_id UUID REFERENCES trucks(truck_id),
    transport_charges DECIMAL(10,2),
    status_change_log JSONB DEFAULT '[]'::JSONB,
    created_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Dispatch Documents
CREATE TABLE IF NOT EXISTS dispatch_documents (
    dispatch_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    truck_id UUID REFERENCES trucks(truck_id) NOT NULL,
    destination VARCHAR(100) NOT NULL,
    dispatch_timestamp TIMESTAMPTZ DEFAULT NOW(),
    total_consignments INTEGER NOT NULL,
    total_volume DECIMAL(10,2) NOT NULL,
    driver_name VARCHAR(100) NOT NULL,
    departure_time TIMESTAMPTZ,
    arrival_time TIMESTAMPTZ,
    dispatch_status VARCHAR(30) DEFAULT 'Dispatched',
    consignment_manifest JSONB DEFAULT '[]'::JSONB,
    created_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Bills
CREATE TABLE IF NOT EXISTS bills (
    bill_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consignment_number VARCHAR(30) REFERENCES consignments(consignment_number) NOT NULL,
    transport_charges DECIMAL(10,2) NOT NULL,
    registration_date TIMESTAMPTZ DEFAULT NOW(),
    pricing_breakdown JSONB NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_consignments_status ON consignments(status);
CREATE INDEX IF NOT EXISTS idx_consignments_destination ON consignments(destination);
CREATE INDEX IF NOT EXISTS idx_consignments_truck ON consignments(assigned_truck_id);
CREATE INDEX IF NOT EXISTS idx_consignments_reg ON consignments(registration_timestamp);
CREATE INDEX IF NOT EXISTS idx_trucks_status ON trucks(status);
CREATE INDEX IF NOT EXISTS idx_dispatch_truck ON dispatch_documents(truck_id);
CREATE INDEX IF NOT EXISTS idx_bills_consignment ON bills(consignment_number);
CREATE INDEX IF NOT EXISTS idx_pricing_dest ON pricing_rules(destination);

-- Update trigger
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_users_upd ON users;
CREATE TRIGGER trg_users_upd BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at();
DROP TRIGGER IF EXISTS trg_trucks_upd ON trucks;
CREATE TRIGGER trg_trucks_upd BEFORE UPDATE ON trucks FOR EACH ROW EXECUTE FUNCTION update_updated_at();
DROP TRIGGER IF EXISTS trg_cons_upd ON consignments;
CREATE TRIGGER trg_cons_upd BEFORE UPDATE ON consignments FOR EACH ROW EXECUTE FUNCTION update_updated_at();
DROP TRIGGER IF EXISTS trg_dispatch_upd ON dispatch_documents;
CREATE TRIGGER trg_dispatch_upd BEFORE UPDATE ON dispatch_documents FOR EACH ROW EXECUTE FUNCTION update_updated_at();
