-- Migration V3 : init driver_documents table (UC-D11, UC-D12)
-- Author : CoVoSIO
-- Date   : 2026-03-27

CREATE TABLE driver_documents (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id        UUID         NOT NULL REFERENCES drivers(user_id) ON DELETE CASCADE,
    car_id           UUID                     REFERENCES cars(id)      ON DELETE SET NULL,
    type             VARCHAR(30)  NOT NULL,
    file_path        VARCHAR(500) NOT NULL,
    mime_type        VARCHAR(100) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT,
    reviewed_by      UUID                     REFERENCES admins(user_id) ON DELETE SET NULL,
    reviewed_at      TIMESTAMP,
    uploaded_at      TIMESTAMP    DEFAULT NOW() NOT NULL
);

CREATE INDEX idx_driver_documents_driver_id ON driver_documents(driver_id);
CREATE INDEX idx_driver_documents_status    ON driver_documents(status);
