package com.skilllink.api.examination;

import com.skilllink.api.auth.SkillLinkPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ExaminationController {
    private final ExaminationService examinations;
    public ExaminationController(ExaminationService examinations) { this.examinations = examinations; }

    @PostMapping("/projects/{projectId}/examinations")
    public ExaminationDtos.ExaminationResponse start(@AuthenticationPrincipal SkillLinkPrincipal principal, @PathVariable UUID projectId, @RequestHeader(value = "X-Request-Id", required = false) String requestId) { return examinations.start(principal.id(), projectId, requestId); }

    @GetMapping("/examinations/{examinationId}")
    public ExaminationDtos.ExaminationResponse get(@AuthenticationPrincipal SkillLinkPrincipal principal, @PathVariable UUID examinationId) { return examinations.get(principal.id(), examinationId); }

    @PostMapping("/examinations/{examinationId}/questions/{questionId}/answers")
    public ExaminationDtos.ExaminationResponse answer(@AuthenticationPrincipal SkillLinkPrincipal principal, @PathVariable UUID examinationId, @PathVariable UUID questionId, @Valid @RequestBody ExaminationDtos.AnswerRequest request, @RequestHeader(value = "X-Request-Id", required = false) String requestId) { return examinations.answer(principal.id(), examinationId, questionId, request.text(), requestId); }
}
