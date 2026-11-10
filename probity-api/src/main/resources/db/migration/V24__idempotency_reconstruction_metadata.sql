ALTER TABLE idempotency_record
    ADD COLUMN IF NOT EXISTS response_reconstruction_metadata TEXT;
