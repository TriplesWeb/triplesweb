-- 배포 중 재시도 시 아카이브 업로드 중복 등록 방지.
CREATE TABLE idempotency_record (
	idempotency_key VARCHAR(128) NOT NULL,
	scope VARCHAR(64) NOT NULL,
	response_location VARCHAR(1000) NOT NULL,
	created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
	PRIMARY KEY (scope, idempotency_key),
	INDEX idempotency_record_created_ix (created_at)
) ENGINE=InnoDB;
