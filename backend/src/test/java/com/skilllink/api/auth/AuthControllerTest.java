package com.skilllink.api.auth;

import com.skilllink.api.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class AuthControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean AuthService authService;
    @MockBean JwtService jwtService;

    @Test
    void refreshWithoutSessionReturnsStructuredUnauthorizedError() throws Exception {
        when(authService.refresh(isNull(), any(), any())).thenThrow(new AuthService.AuthException("REFRESH_TOKEN_REQUIRED", "A refresh session is required."));

        mockMvc.perform(post("/api/v1/auth/refresh"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REQUIRED"))
            .andExpect(jsonPath("$.message").value("A refresh session is required."))
            .andExpect(jsonPath("$.requestId").isNotEmpty())
            .andExpect(jsonPath("$.details").isMap());
    }

    @Test
    void revokedRefreshReturnsStructuredUnauthorizedError() throws Exception {
        when(authService.refresh(eq("revoked-token"), any(), any())).thenThrow(new AuthService.AuthException("INVALID_REFRESH_TOKEN", "Refresh session is invalid or expired."));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new jakarta.servlet.http.Cookie("skilllink_refresh", "revoked-token")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logoutClearsRefreshCookieAndReturnsSuccess() throws Exception {
        doNothing().when(authService).logout("refresh-token");

        mockMvc.perform(post("/api/v1/auth/logout").cookie(new jakarta.servlet.http.Cookie("skilllink_refresh", "refresh-token")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Signed out."))
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("skilllink_refresh=")))
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        verify(authService).logout("refresh-token");
    }
}
