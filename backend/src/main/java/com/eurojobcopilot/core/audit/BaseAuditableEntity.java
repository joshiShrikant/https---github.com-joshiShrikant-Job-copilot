package com.eurojobcopilot.core.audit;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;

/**
 * Base mapped superclass for mutable entities tracking creation and last-modification timestamps.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseAuditableEntity extends BaseCreatedEntity {

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

