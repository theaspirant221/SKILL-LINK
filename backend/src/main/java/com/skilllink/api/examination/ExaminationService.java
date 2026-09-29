package com.skilllink.api.examination;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skilllink.api.audit.AuditService;
import com.skilllink.api.project.ProjectService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ExaminationService {
    private static final String POLICY_VERSION = "skill-proof-baseline:v1.0";
    private static final String PROMPT_VERSION = "project-defense:v1";
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ProjectService projects;
    private final AuditService audit;

    public ExaminationService(JdbcTemplate jdbc, ObjectMapper objectMapper, ProjectService projects, AuditService audit) { this.jdbc = jdbc; this.objectMapper = objectMapper; this.projects = projects; this.audit = audit; }

    @Transactional
    public ExaminationDtos.ExaminationResponse start(UUID candidateId, UUID projectId, String requestId) {
        projects.get(candidateId, projectId);
        List<Grounding> grounding = jdbc.query("""
            SELECT s.name skill_name, es.source_type, es.source_location, e.observation
            FROM evidence e JOIN skill s ON s.id = e.skill_id JOIN evidence_source es ON es.id = e.evidence_source_id
            WHERE e.candidate_id = ? AND e.project_id = ? AND e.status <> 'DISPUTED'
            ORDER BY CASE WHEN e.evidence_strength = 'DIRECT' THEN 0 WHEN e.evidence_strength = 'STRONG' THEN 1 ELSE 2 END, e.created_at
            LIMIT 12
            """, (rs, rowNum) -> new Grounding(rs.getString("skill_name"), rs.getString("source_type"), rs.getString("source_location"), rs.getString("observation")), candidateId, projectId);
        if (grounding.isEmpty()) throw new ExaminationInputException("EVIDENCE_REQUIRED", "Analyze the project and persist evidence before starting a project defense.");

        List<QuestionSeed> questions = buildQuestions(grounding);
        UUID examinationId = jdbc.queryForObject("""
            INSERT INTO examination(candidate_id, project_id, status, policy_version, prompt_version, model_provider, model_name, started_at)
            VALUES (?, ?, 'IN_PROGRESS', ?, ?, 'DETERMINISTIC_GROUNDED', 'rule-engine-v1', now()) RETURNING id
            """, UUID.class, candidateId, projectId, POLICY_VERSION, PROMPT_VERSION);
        for (int index = 0; index < questions.size(); index++) {
            QuestionSeed question = questions.get(index);
            jdbc.update("""
                INSERT INTO examination_question(examination_id, sequence_no, category, prompt, context_references, expected_signals)
                VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb)
                """, examinationId, index + 1, question.category(), question.prompt(), json(question.contextReferences()), json(question.expectedSignals()));
        }
        audit.record(candidateId, "EXAMINATION_STARTED", "EXAMINATION", examinationId, requestId, Map.of("projectId", projectId.toString(), "questionCount", questions.size(), "promptVersion", PROMPT_VERSION));
        return get(candidateId, examinationId);
    }

    public ExaminationDtos.ExaminationResponse get(UUID candidateId, UUID examinationId) {
        ExaminationRow exam = findExam(candidateId, examinationId);
        List<ExaminationDtos.QuestionResponse> questions = jdbc.query("""
            SELECT q.id, q.sequence_no, q.category, q.prompt, q.context_references, a.evaluation_status, a.feedback
            FROM examination_question q LEFT JOIN examination_answer a ON a.question_id = q.id
            WHERE q.examination_id = ? ORDER BY q.sequence_no
            """, (rs, rowNum) -> new ExaminationDtos.QuestionResponse(rs.getObject("id", UUID.class), rs.getInt("sequence_no"), rs.getString("category"), rs.getString("prompt"), readList(rs.getString("context_references")), rs.getString("evaluation_status"), rs.getString("feedback")), examinationId);
        return new ExaminationDtos.ExaminationResponse(exam.id(), exam.projectId(), exam.status(), exam.result(), exam.policyVersion(), exam.promptVersion(), exam.startedAt(), exam.completedAt(), questions);
    }

    @Transactional
    public ExaminationDtos.ExaminationResponse answer(UUID candidateId, UUID examinationId, UUID questionId, String text, String requestId) {
        if (text == null || text.isBlank() || text.trim().length() < 40 || text.length() > 8000) throw new ExaminationInputException("ANSWER_INVALID", "Give a grounded answer between 40 and 8000 characters.");
        ExaminationRow exam = findExam(candidateId, examinationId);
        if (!"IN_PROGRESS".equals(exam.status())) throw new ExaminationInputException("EXAMINATION_NOT_ACTIVE", "This project defense is no longer accepting answers.");
        QuestionGrounding question = jdbc.queryForObject("SELECT id, prompt, expected_signals, context_references FROM examination_question WHERE id = ? AND examination_id = ?", (rs, rowNum) -> new QuestionGrounding(rs.getObject("id", UUID.class), rs.getString("prompt"), readList(rs.getString("expected_signals")), readList(rs.getString("context_references"))), questionId, examinationId);
        Evaluation evaluation = evaluate(text.trim(), question);
        jdbc.update("""
            INSERT INTO examination_answer(question_id, answer_text_redacted, evaluation_status, feedback, evaluator_provider, evaluator_model, evaluator_prompt_version)
            VALUES (?, ?, ?, ?, 'DETERMINISTIC_GROUNDED', 'rule-engine-v1', ?)
            ON CONFLICT (question_id) DO UPDATE SET answer_text_redacted = EXCLUDED.answer_text_redacted, evaluation_status = EXCLUDED.evaluation_status, feedback = EXCLUDED.feedback, evaluator_provider = EXCLUDED.evaluator_provider, evaluator_model = EXCLUDED.evaluator_model, evaluator_prompt_version = EXCLUDED.evaluator_prompt_version, submitted_at = now()
            """, questionId, text.trim(), evaluation.status(), evaluation.feedback(), PROMPT_VERSION);
        long total = jdbc.queryForObject("SELECT count(*) FROM examination_question WHERE examination_id = ?", Long.class, examinationId);
        long answered = jdbc.queryForObject("SELECT count(*) FROM examination_answer a JOIN examination_question q ON q.id = a.question_id WHERE q.examination_id = ?", Long.class, examinationId);
        if (answered >= total) {
            long passed = jdbc.queryForObject("SELECT count(*) FROM examination_answer a JOIN examination_question q ON q.id = a.question_id WHERE q.examination_id = ? AND a.evaluation_status = 'MEETS_BAR'", Long.class, examinationId);
            String result = passed == total ? "PASSED" : passed > 0 ? "PARTIAL" : "NEEDS_REVIEW";
            jdbc.update("UPDATE examination SET status = 'COMPLETED', result = ?, completed_at = now() WHERE id = ?", result, examinationId);
        }
        audit.record(candidateId, "EXAMINATION_ANSWERED", "EXAMINATION", examinationId, requestId, Map.of("questionId", questionId.toString(), "evaluationStatus", evaluation.status()));
        return get(candidateId, examinationId);
    }

    private List<QuestionSeed> buildQuestions(List<Grounding> grounding) {
        Map<String, Grounding> distinct = new LinkedHashMap<>();
        for (Grounding item : grounding) distinct.putIfAbsent(item.skillName() + "|" + item.sourceLocation(), item);
        List<QuestionSeed> result = new ArrayList<>();
        for (Grounding item : distinct.values()) {
            if (result.size() == 3) break;
            String category = "DEPENDENCY".equals(item.sourceType()) ? "ARCHITECTURE" : "CODE_LOCATION";
            String prompt = "Using the persisted observation for " + item.skillName() + ", explain how this project uses it. Ground the answer in " + item.sourceLocation() + " and describe one trade-off, failure mode, or test you would use to validate the behavior.";
            result.add(new QuestionSeed(category, prompt, List.of(item.sourceLocation(), item.observation()), List.of(item.skillName(), item.sourceLocation())));
        }
        return result;
    }

    private Evaluation evaluate(String answer, QuestionGrounding question) {
        String lower = answer.toLowerCase(java.util.Locale.ROOT);
        boolean referencesSource = question.expectedSignals().stream().anyMatch(signal -> lower.contains(signal.toLowerCase(java.util.Locale.ROOT)));
        boolean explainsReasoning = List.of("because", "trade-off", "tradeoff", "failure", "test", "security", "latency", "consistency", "validation").stream().anyMatch(lower::contains);
        if (referencesSource && explainsReasoning && answer.length() >= 80) return new Evaluation("MEETS_BAR", "Grounded answer accepted by the deterministic rubric: it referenced the source context and gave a concrete reasoning or validation signal.");
        return new Evaluation("NEEDS_DEPTH", "Answer recorded, but it must reference the persisted source location and explain a concrete trade-off, failure mode, or test.");
    }

    private ExaminationRow findExam(UUID candidateId, UUID examinationId) { try { return jdbc.queryForObject("SELECT id, project_id, status, result, policy_version, prompt_version, started_at, completed_at FROM examination WHERE id = ? AND candidate_id = ?", (rs, rowNum) -> new ExaminationRow(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class), rs.getString("status"), rs.getString("result"), rs.getString("policy_version"), rs.getString("prompt_version"), instant(rs, "started_at"), instant(rs, "completed_at")), examinationId, candidateId); } catch (org.springframework.dao.EmptyResultDataAccessException ex) { throw new ExaminationNotFoundException(); } }
    private Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException { OffsetDateTime value = rs.getObject(column, OffsetDateTime.class); return value == null ? null : value.toInstant(); }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private List<String> readList(String value) { try { return value == null ? List.of() : objectMapper.readValue(value, new TypeReference<>() {}); } catch (Exception ex) { return List.of(); } }

    private record Grounding(String skillName, String sourceType, String sourceLocation, String observation) {}
    private record QuestionSeed(String category, String prompt, List<String> contextReferences, List<String> expectedSignals) {}
    private record QuestionGrounding(UUID id, String prompt, List<String> expectedSignals, List<String> contextReferences) {}
    private record Evaluation(String status, String feedback) {}
    private record ExaminationRow(UUID id, UUID projectId, String status, String result, String policyVersion, String promptVersion, Instant startedAt, Instant completedAt) {}
    public static class ExaminationNotFoundException extends RuntimeException {}
    public static class ExaminationInputException extends RuntimeException { private final String code; public ExaminationInputException(String code, String message) { super(message); this.code = code; } public String code() { return code; } }
}
