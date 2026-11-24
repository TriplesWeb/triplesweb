CREATE INDEX IF NOT EXISTS idx_archive_cast_mask ON archive (media_type, cast_member_mask);
CREATE INDEX IF NOT EXISTS idx_archive_cast_size ON archive (media_type, cast_member_count);
CREATE INDEX IF NOT EXISTS idx_archive_cast_rank ON archive (media_type, cast_member_rank, id);
