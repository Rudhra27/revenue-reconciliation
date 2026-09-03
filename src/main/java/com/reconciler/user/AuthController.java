package com.reconciler.user;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {

	private final UserService userService;

	public AuthController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/login")
	public String login() {
		return "login";
	}

	@GetMapping("/signup")
	public String signupForm(Model model) {
		model.addAttribute("form", new SignupForm());
		return "signup";
	}

	@PostMapping("/signup")
	public String signup(@Valid @ModelAttribute("form") SignupForm form, BindingResult result, Model model) {
		if (result.hasErrors()) {
			return "signup";
		}
		try {
			userService.register(form.getEmail(), form.getPassword());
		} catch (EmailAlreadyUsedException e) {
			model.addAttribute("signupError", "That email is already registered.");
			return "signup";
		}
		return "redirect:/login?registered";
	}
}
