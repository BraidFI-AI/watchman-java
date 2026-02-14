-- Add original_data column to store full Braid entity JSON
ALTER TABLE entities 
ADD COLUMN original_data JSONB;

-- Add GIN index for JSONB queries (optional but recommended)
CREATE INDEX idx_entities_original_data ON entities USING GIN (original_data);
