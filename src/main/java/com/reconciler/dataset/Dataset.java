package com.reconciler.dataset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dataset")
public class Dataset {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	// Owner. Denormalised id (not a @ManyToOne) so every ownership check is a plain
	// column predicate and there's no lazy-loading surprise.
	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DatasetStatus status = DatasetStatus.CREATED;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Dataset() {
		// for JPA
	}

	public Dataset(UUID userId, String name) {
		this.userId = userId;
		this.name = name;
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public void markOrdersLoaded() {
		if (status == DatasetStatus.CREATED) {
			status = DatasetStatus.ORDERS_LOADED;
		}
	}

	public void markPaymentsLoaded() {
		status = DatasetStatus.PAYMENTS_LOADED;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getName() {
		return name;
	}

	public DatasetStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
