-- Day Watcher PostgreSQL Schema
-- Replaces DynamoDB tables with RDS PostgreSQL

-- Table: entities (master entity list for incremental sync)
CREATE TABLE IF NOT EXISTS entities (
    entity_id VARCHAR(100) PRIMARY KEY,
    entity_type VARCHAR(20) NOT NULL CHECK (entity_type IN ('individual', 'business', 'counterparty')),
    name VARCHAR(500) NOT NULL,
    addresses JSONB,
    dob DATE,
    incorporation_date DATE,
    alt_names JSONB,
    braid_updated_at TIMESTAMP WITH TIME ZONE,
    braid_tenant_id VARCHAR(100),
    braid_status VARCHAR(20) DEFAULT 'ACTIVE',
    last_screened_at TIMESTAMP WITH TIME ZONE,
    last_screening_result VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index for type-based queries
CREATE INDEX IF NOT EXISTS idx_entities_type ON entities(entity_type);

-- Index for status filtering (active entities)
CREATE INDEX IF NOT EXISTS idx_entities_status ON entities(braid_status);

-- Index for incremental fetch (updated_after queries)
CREATE INDEX IF NOT EXISTS idx_entities_updated ON entities(braid_updated_at);

-- GIN index for JSONB address queries (if needed for address-based filtering)
CREATE INDEX IF NOT EXISTS idx_entities_addresses ON entities USING GIN(addresses);

-- Table: runs (audit trail for daily screening runs - replaces DynamoDB)
CREATE TABLE IF NOT EXISTS runs (
    run_id VARCHAR(50) PRIMARY KEY,
    run_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    start_time TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP WITH TIME ZONE,
    entities_fetched_from_braid INTEGER DEFAULT 0,
    entities_written_to_db INTEGER DEFAULT 0,
    entities_in_ndjson INTEGER DEFAULT 0,
    total_entities_screened INTEGER DEFAULT 0,
    fetch_breakdown JSONB,
    has_discrepancy BOOLEAN DEFAULT FALSE,
    s3_input_path VARCHAR(500),
    s3_output_path VARCHAR(500),
    ecs_task_arn VARCHAR(500),
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index for date-based queries
CREATE INDEX IF NOT EXISTS idx_runs_date ON runs(run_date);

-- Index for status queries
CREATE INDEX IF NOT EXISTS idx_runs_status ON runs(status);

-- Trigger to auto-update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_entities_updated_at BEFORE UPDATE ON entities
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_runs_updated_at BEFORE UPDATE ON runs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- View for active entities count by type (convenience)
CREATE OR REPLACE VIEW active_entities_summary AS
SELECT 
    entity_type,
    COUNT(*) as count,
    MAX(braid_updated_at) as last_updated
FROM entities
WHERE braid_status = 'ACTIVE'
GROUP BY entity_type;

COMMENT ON TABLE entities IS 'Master entity list synced incrementally from Braid API';
COMMENT ON TABLE runs IS 'Audit trail for daily OFAC screening runs';
COMMENT ON COLUMN entities.addresses IS 'JSONB array of address objects from Braid';
COMMENT ON COLUMN entities.alt_names IS 'JSONB array of alternate names';
COMMENT ON COLUMN runs.fetch_breakdown IS 'JSONB map of entity counts by type fetched from Braid';
COMMENT ON COLUMN runs.write_breakdown IS 'JSONB map of entity counts by type written to database';
