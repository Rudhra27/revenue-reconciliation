package com.reconciler.user;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

	private final UserRepository users;

	public AppUserDetailsService(UserRepository users) {
		this.users = users;
	}

	@Override
	public UserDetails loadUserByUsername(String username) {
		String email = UserService.normalize(username);
		AppUser user = users.findByEmail(email)
				.orElseThrow(() -> new UsernameNotFoundException("No account for " + username));
		return new AppUserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash());
	}
}
