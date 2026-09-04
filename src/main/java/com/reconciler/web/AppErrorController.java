package com.reconciler.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Renders the error page for every failure instead of Spring's default JSON. A direct hit
 * on /error (a stale tab, an address-bar guess) has no error to report, so it just goes home.
 */
@Controller
public class AppErrorController implements ErrorController {

	@RequestMapping("/error")
	public String handleError(HttpServletRequest request, Model model) {
		Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		if (status == null) {
			return "redirect:/";
		}
		int code = Integer.parseInt(status.toString());
		HttpStatus resolved = HttpStatus.resolve(code);
		model.addAttribute("status", code);
		model.addAttribute("error", resolved != null ? resolved.getReasonPhrase() : "Error");
		return "error";
	}
}
