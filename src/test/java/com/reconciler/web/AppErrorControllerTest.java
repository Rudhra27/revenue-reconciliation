package com.reconciler.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reconciler.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AppErrorControllerTest {

	@Autowired
	private MockMvc mvc;

	@Test
	@WithMockUser
	void aDirectHitOnErrorJustGoesHome() throws Exception {
		mvc.perform(get("/error"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/"));
	}

	@Test
	@WithMockUser
	void rendersTheErrorPageWhenThereIsAFailureToReport() throws Exception {
		mvc.perform(get("/error").requestAttr("jakarta.servlet.error.status_code", 404))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("404")))
				.andExpect(content().string(containsString("Not Found")));
	}
}
