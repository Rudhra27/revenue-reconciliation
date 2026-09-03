package com.reconciler.dataset;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import com.reconciler.TestcontainersConfiguration;
import com.reconciler.user.AppUser;
import com.reconciler.user.AppUserPrincipal;
import com.reconciler.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class DatasetControllerTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserService users;

	@Autowired
	private DatasetService datasets;

	@Test
	void listShowsOwnDatasetsOnly() throws Exception {
		AppUserPrincipal alice = principal(users.register("alice@example.com", "password123"));
		AppUser bob = users.register("bob@example.com", "password123");
		datasets.create(alice.id(), "Alice Q1");
		datasets.create(bob.getId(), "Bob Q1");

		mvc.perform(get("/datasets").with(user(alice)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Alice Q1")))
				.andExpect(content().string(not(containsString("Bob Q1"))));
	}

	@Test
	void createRedirectsToTheNewDataset() throws Exception {
		AppUserPrincipal alice = principal(users.register("alice@example.com", "password123"));

		mvc.perform(post("/datasets").with(user(alice)).with(csrf()).param("name", "April"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("/datasets/*"));
	}

	@Test
	void cannotOpenAnotherUsersDataset() throws Exception {
		AppUserPrincipal alice = principal(users.register("alice@example.com", "password123"));
		AppUser bob = users.register("bob@example.com", "password123");
		Dataset bobs = datasets.create(bob.getId(), "Bob Q1");

		mvc.perform(get("/datasets/{id}", bobs.getId()).with(user(alice)))
				.andExpect(status().isNotFound());
	}

	private static AppUserPrincipal principal(AppUser user) {
		return new AppUserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash());
	}
}
