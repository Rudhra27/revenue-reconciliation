package com.reconciler.ingest;

import com.reconciler.user.AppUserPrincipal;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/datasets/{id}")
public class IngestController {

	private final IngestService ingest;

	public IngestController(IngestService ingest) {
		this.ingest = ingest;
	}

	@PostMapping("/orders")
	public String uploadOrders(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id,
			@RequestParam("file") MultipartFile file, RedirectAttributes flash) {
		upload(file, flash, in -> ingest.ingestOrders(id, user.id(), in));
		return "redirect:/datasets/" + id;
	}

	@PostMapping("/payments")
	public String uploadPayments(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id,
			@RequestParam("file") MultipartFile file, RedirectAttributes flash) {
		upload(file, flash, in -> ingest.ingestPayments(id, user.id(), in));
		return "redirect:/datasets/" + id;
	}

	// Turns the upload outcome into one of two flash attributes the detail page renders.
	// A not-found dataset is left to propagate (404); everything else lands as a message.
	private void upload(MultipartFile file, RedirectAttributes flash, Function<InputStream, UploadSummary> ingestFn) {
		if (file == null || file.isEmpty()) {
			flash.addFlashAttribute("uploadError", "Choose a file to upload.");
			return;
		}
		try (InputStream in = file.getInputStream()) {
			flash.addFlashAttribute("uploadSummary", ingestFn.apply(in));
		} catch (InvalidCsvException | OrdersRequiredException e) {
			flash.addFlashAttribute("uploadError", e.getMessage());
		} catch (IOException e) {
			flash.addFlashAttribute("uploadError", "Could not read the uploaded file.");
		}
	}
}
