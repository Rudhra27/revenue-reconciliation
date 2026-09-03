package com.reconciler.dataset;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// 404, not 403: we don't confirm that someone else's dataset exists.
@ResponseStatus(HttpStatus.NOT_FOUND)
public class DatasetNotFoundException extends RuntimeException {

	public DatasetNotFoundException(UUID id) {
		super("Dataset not found: " + id);
	}
}
