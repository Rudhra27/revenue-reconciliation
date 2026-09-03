package com.reconciler.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {

	private final UserRepository users = mock(UserRepository.class);
	private final PasswordEncoder encoder = new BCryptPasswordEncoder();
	private final UserService service = new UserService(users, encoder);

	@Test
	void normalisesEmailAndStoresAHash() {
		when(users.existsByEmail("bob@example.com")).thenReturn(false);
		when(users.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

		AppUser saved = service.register("  Bob@Example.com ", "s3cret-password");

		assertThat(saved.getEmail()).isEqualTo("bob@example.com");
		assertThat(saved.getPasswordHash()).isNotEqualTo("s3cret-password");
		assertThat(encoder.matches("s3cret-password", saved.getPasswordHash())).isTrue();
	}

	@Test
	void rejectsAnEmailThatIsAlreadyRegistered() {
		when(users.existsByEmail("taken@example.com")).thenReturn(true);

		assertThatThrownBy(() -> service.register("taken@example.com", "whatever1"))
				.isInstanceOf(EmailAlreadyUsedException.class);
	}
}
