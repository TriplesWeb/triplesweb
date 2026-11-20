package com.example.triplesweb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "idempotency_record")
@IdClass(IdempotencyRecord.Pk.class)
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Id
    @Column(name = "scope", nullable = false, length = 64)
    private String scope;

    @Column(name = "response_location", nullable = false, length = 1000)
    private String responseLocation;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public static class Pk implements Serializable {
        private String idempotencyKey;
        private String scope;

        public Pk() {}

        public Pk(String scope, String idempotencyKey) {
            this.scope = scope;
            this.idempotencyKey = idempotencyKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(idempotencyKey, pk.idempotencyKey) && Objects.equals(scope, pk.scope);
        }

        @Override
        public int hashCode() {
            return Objects.hash(idempotencyKey, scope);
        }
    }
}
