-- V162: Add FILE_SIZE column to OVRS_PYMN_SUMR
-- Mirrors ScdulPymnSumr.FILE_SIZE (NUMERIC(20,0) / BigDecimal) — same pattern
-- as V131 (internal-transfer schema). Consumed by OverseasPaymentExcelService
-- which stores file.getSize() on upload and reads it back for Content-Length
-- on the download endpoint.

ALTER TABLE OVRS_PYMN_SUMR
    ADD COLUMN IF NOT EXISTS FILE_SIZE NUMERIC(20, 0);
