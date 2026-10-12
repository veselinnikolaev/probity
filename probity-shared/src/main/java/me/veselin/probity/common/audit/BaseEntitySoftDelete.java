package me.veselin.probity.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntitySoftDelete extends BaseEntity {

    @Column(nullable = false, name = "deleted")
    private Boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public void softDelete() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }
}