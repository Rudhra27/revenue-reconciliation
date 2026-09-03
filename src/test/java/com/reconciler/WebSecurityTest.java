package com.reconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reconciler.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class WebSecurityTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserRepository users;

	@Test
	void anonymousUserIsSentToLogin() throws Exception {
		mvc.perform(get("/"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	void healthEndpointIsPublic() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void loginAndSignupPagesArePublic() throws Exception {
		mvc.perform(get("/login")).andExpect(status().isOk());
		mvc.perform(get("/signup")).andExpect(status().isOk());
	}

	@Test
	void signupCreatesAccountThatCanLogIn() throws Exception {
		mvc.perform(post("/signup").with(csrf())
						.param("email", "alice@example.com")
						.param("password", "password123"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login?registered"));

		assertThat(users.existsByEmail("alice@example.com")).isTrue();

		mvc.perform(formLogin("/login").user("alice@example.com").password("password123"))
				.andExpect(authenticated());
	}

	@Test
	void wrongPasswordIsRejected() throws Exception {
		mvc.perform(formLogin("/login").user("nobody@example.com").password("wrong"))
				.andExpect(unauthenticated())
				.andExpect(redirectedUrl("/login?error"));
	}
}
