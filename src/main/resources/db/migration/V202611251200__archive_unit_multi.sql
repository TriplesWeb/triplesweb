CREATE TABLE archive_unit (
    archive_id BIGINT NOT NULL,
    unit_id BIGINT NOT NULL,
    PRIMARY KEY (archive_id, unit_id),
    INDEX idx_archive_unit_unit_id (unit_id),
    CONSTRAINT fk_archive_unit_archive FOREIGN KEY (archive_id) REFERENCES archive(id) ON DELETE CASCADE
);

INSERT INTO archive_unit (archive_id, unit_id)
SELECT id, unit_id FROM archive WHERE unit_id IS NOT NULL;
