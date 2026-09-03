package com.reconciler.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reconciler.TestcontainersConfiguration;
import com.reconciler.user.AppUser;
import com.reconciler.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DatasetTenancyTest {

	@Autowired
	private DatasetService datasets;

	@Autowired
	private UserService users;

	@Test
	void aUserOnlySeesTheirOwnDatasets() {
		AppUser alice = users.register("alice@example.com", "password123");
		AppUser bob = users.register("bob@example.com", "password123");
		datasets.create(alice.getId(), "Alice Q1");
		datasets.create(bob.getId(), "Bob Q1");

		assertThat(datasets.listFor(alice.getId()))
				.extracting(Dataset::getName)
				.containsExactly("Alice Q1");
	}

	@Test
	void loadingSomeoneElsesDatasetIsNotFound() {
		AppUser alice = users.register("alice@example.com", "password123");
		AppUser bob = users.register("bob@example.com", "password123");
		Dataset bobs = datasets.create(bob.getId(), "Bob Q1");

		assertThatThrownBy(() -> datasets.getOwned(bobs.getId(), alice.getId()))
				.isInstanceOf(DatasetNotFoundException.class);
	}
}
