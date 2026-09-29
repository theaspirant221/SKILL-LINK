package com.skilllink.api.examination;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ExaminationDtos {
    private ExaminationDtos() {}
    public record AnswerRequest(@NotBlank String text) {}
    public record QuestionResponse(UUID questionId, int sequence, String category, String prompt, List<String> contextReferences, String answerStatus, String feedback) {}
    public record ExaminationResponse(UUID examinationId, UUID projectId, String status, String result, String policyVersion, String promptVersion, Instant startedAt, Instant completedAt, List<QuestionResponse> questions) {}
}
