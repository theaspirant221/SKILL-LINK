package com.skilllink.api.recruiter;

import com.skilllink.api.ApiExceptionHandler;
import com.skilllink.api.auth.JwtService;
import com.skilllink.api.auth.SkillLinkPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = RecruiterController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, RecruiterExceptionHandler.class})
class RecruiterControllerTest {
    private static final UUID CANDIDATE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Autowired MockMvc mockMvc;
    @MockBean RecruiterService recruiterService;
    @MockBean JwtService jwtService;

    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    private static RequestPostProcessor candidatePrincipal() {
        return request -> {
            SkillLinkPrincipal principal = new SkillLinkPrincipal(CANDIDATE_ID, "candidate@example.com", "Candidate One", "CANDIDATE");
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_CANDIDATE"))));
            SecurityContextHolder.setContext(context);
            return request;
        };
    }

    @Test
    void candidateContractsReturnsSharedContractsAndScopesToPrincipal() throws Exception {
        when(recruiterService.candidateContracts(CANDIDATE_ID)).thenReturn(List.of(new RecruiterDtos.CandidateProofContractResponse(
            UUID.fromString("00000000-0000-0000-0000-0000000000c1"),
            UUID.fromString("00000000-0000-0000-0000-0000000000b1"),
            "Backend Engineer", "Acme Proof Labs", 2, "SHARED", Instant.parse("2026-09-28T10:15:00Z"),
            List.of(new RecruiterDtos.CandidateContractRequirementResponse(
                UUID.fromString("00000000-0000-0000-0000-0000000000d1"),
                UUID.fromString("00000000-0000-0000-0000-0000000000a1"),
                "Java", "REQUIRED", "SUPPORTED", "Policy-backed verification is present.", 3, Instant.parse("2026-09-28T11:00:00Z"))))));

        mockMvc.perform(get("/api/v1/candidates/me/proof-contracts").with(candidatePrincipal()))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].contractId").value("00000000-0000-0000-0000-0000000000c1"))
            .andExpect(jsonPath("$[0].jobTitle").value("Backend Engineer"))
            .andExpect(jsonPath("$[0].company").value("Acme Proof Labs"))
            .andExpect(jsonPath("$[0].requirements[0].skillName").value("Java"))
            .andExpect(jsonPath("$[0].requirements[0].outcome").value("SUPPORTED"))
            .andExpect(jsonPath("$[0].requirements[0].visibleEvidenceCount").value(3));

        verify(recruiterService).candidateContracts(CANDIDATE_ID);
    }

    @Test
    void candidateContractsNeverExposeRecruiterReviewerNotes() throws Exception {
        when(recruiterService.candidateContracts(CANDIDATE_ID)).thenReturn(List.of(new RecruiterDtos.CandidateProofContractResponse(
            UUID.fromString("00000000-0000-0000-0000-0000000000c2"),
            UUID.fromString("00000000-0000-0000-0000-0000000000b2"),
            "Platform Engineer", "Proof-first Systems", 1, "REVIEWED", Instant.parse("2026-09-27T09:00:00Z"),
            List.of(new RecruiterDtos.CandidateContractRequirementResponse(
                UUID.fromString("00000000-0000-0000-0000-0000000000d2"),
                UUID.fromString("00000000-0000-0000-0000-0000000000a2"),
                "PostgreSQL", "REQUIRED", "PARTIAL", "1 recruiter-visible evidence item is shared.", 1, null)))));

        mockMvc.perform(get("/api/v1/candidates/me/proof-contracts").with(candidatePrincipal()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].requirements[0].reviewerNote").doesNotExist())
            .andExpect(jsonPath("$[0].requirements[0].reviewedBy").doesNotExist())
            .andExpect(jsonPath("$[0].requirements[0].verificationResultId").doesNotExist());
    }

    @Test
    void candidateContractsWithoutSharedContractsReturnEmptyList() throws Exception {
        when(recruiterService.candidateContracts(CANDIDATE_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/candidates/me/proof-contracts").with(candidatePrincipal()))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }
}
