package com.reconciler.dataset;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasetRepository extends JpaRepository<Dataset, UUID> {

	List<Dataset> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

	Optional<Dataset> findByIdAndUserId(UUID id, UUID userId);
}
