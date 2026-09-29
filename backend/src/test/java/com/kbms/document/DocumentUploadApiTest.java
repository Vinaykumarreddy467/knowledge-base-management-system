package com.kbms.document;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kbms.document.DocumentService.DocumentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Focused API-layer tests for POST /api/documents.
 *
 * <p>Two behaviours must hold together: a malformed request is a client error (400), and an
 * unauthorized viewer upload is still a 403. Both used to be reachable only through the catch-all,
 * which returned 500 for the malformed cases.
 */
@WebMvcTest(controllers = DocumentController.class)
@EnableWebSecurity
@EnableMethodSecurity
@Import(DocumentUploadApiTest.TestFilterChain.class)
class DocumentUploadApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documents;

    @MockitoBean
    private DocumentProcessor processor;

    /** Spring Boot registers Filter beans in @WebMvcTest; JwtAuthFilter needs a JwtService to construct. */
    @MockitoBean
    private com.kbms.security.JwtService jwtService;

    private static MockMultipartFile part(String name, String filename) {
        return new MockMultipartFile(name, filename, "text/plain", "hello".getBytes());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void multipartRequestWithoutFilePartIsBadRequestNotServerError() throws Exception {
        mockMvc.perform(multipart("/api/documents").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("file"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("is required"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void multipartRequestWithWrongPartNameIsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/documents").file(part("notfile", "notes.txt")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void requestWithNoBodyAtAllIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/documents").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void nonMultipartContentTypeIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/documents")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCannotUploadAndIsForbiddenNotRejectedAsMalformed() throws Exception {
        mockMvc.perform(multipart("/api/documents").file(part("file", "notes.txt")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action"));

        verify(documents, never()).upload(any());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void editorUploadStillSucceedsSoTheMalformedHandlingIsNotOverBroad() throws Exception {
        when(documents.upload(any())).thenReturn(new DocumentResponse(
                1L, "notes.txt", FileType.TXT, "text/plain", 5L, DocumentStatus.UPLOADED,
                null, 0, 1L, null, null));

        mockMvc.perform(multipart("/api/documents").file(part("file", "notes.txt")).with(csrf()))
                .andExpect(status().isCreated());

        verify(processor).processAsync(1L);
    }

    @TestConfiguration
    static class TestFilterChain {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .build();
        }
    }
}
