package com.reconciler.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public AppUser register(String email, String rawPassword) {
		String normalized = normalize(email);
		if (users.existsByEmail(normalized)) {
			throw new EmailAlreadyUsedException(normalized);
		}
		return users.save(new AppUser(normalized, passwordEncoder.encode(rawPassword)));
	}

	static String normalize(String email) {
		return email == null ? null : email.trim().toLowerCase();
	}
}
