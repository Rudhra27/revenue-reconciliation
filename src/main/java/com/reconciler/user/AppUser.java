package com.reconciler.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "app_user")
public class AppUser {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	// Stored as citext in Postgres for case-insensitive uniqueness. JdbcTypeCode keeps
	// the String binding working over the non-standard column type; columnDefinition
	// keeps Hibernate's schema validation aligned with what the migration creates.
	@JdbcTypeCode(Types.VARCHAR)
	@Column(nullable = false, unique = true, columnDefinition = "citext")
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected AppUser() {
		// for JPA
	}

	public AppUser(String email, String passwordHash) {
		this.email = email;
		this.passwordHash = passwordHash;
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
