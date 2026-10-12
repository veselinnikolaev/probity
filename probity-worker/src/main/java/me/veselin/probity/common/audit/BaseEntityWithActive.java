package me.veselin.probity.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntityWithActive extends BaseEntity {

    @Column(nullable = false, name = "active")
    private Boolean active = true;

    @Column(name = "archived_at")
    private Instant archivedAt;

    public void archive() {
        this.active = false;
        this.archivedAt = Instant.now();
    }
}