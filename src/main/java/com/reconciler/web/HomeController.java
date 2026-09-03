package com.reconciler.web;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

	// Placeholder landing page until the dataset list and dashboard land.
	@GetMapping("/")
	public String home(Authentication authentication, Model model) {
		model.addAttribute("email", authentication.getName());
		return "home";
	}
}
