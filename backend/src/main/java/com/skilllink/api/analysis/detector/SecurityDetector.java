package com.skilllink.api.analysis.detector;

import com.skilllink.api.analysis.AnalysisObservationRepository;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SecurityDetector {
    public static final String DETECTOR = "SECURITY_DETECTOR";
    public static final String VERSION = "deterministic-v1";

    public List<AnalysisObservationRepository.ObservationRow> detect(
        UUID runId,
        UUID snapshotId,
        List<LanguageDetector.SnapshotFile> files
    ) {
        List<AnalysisObservationRepository.ObservationRow> obs = new ArrayList<>();
        for (LanguageDetector.SnapshotFile file : files) {
            String path = file.path();
            String content = file.content();
            if (content == null) continue;
            String lower = content.toLowerCase();

            if (lower.contains("securityfilterchain")) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "SECURITY_CONFIGURATION", "SECURITY", "SecurityFilterChain", path,
                    "Java", "Spring Security", path, null, null, "SecurityFilterChain", file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
            }
            if (lower.contains("passwordencoder")) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "SECURITY_CONFIGURATION", "SECURITY", "PasswordEncoder", path,
                    "Java", "Spring Security", path, null, null, "PasswordEncoder", file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
            }
            if (lower.contains("jwt") && (lower.contains("service") || lower.contains("filter") || lower.contains("provider") || lower.contains("token"))) {
                if (path.toLowerCase().contains("jwt") || lower.contains("class") && lower.contains("jwt")) {
                    obs.add(new AnalysisObservationRepository.ObservationRow(
                        UUID.randomUUID(), runId, snapshotId,
                        "SECURITY_CONFIGURATION", "SECURITY", "JWT", path,
                        "Java", "JWT", path, null, null, "JWT", file.contentHash(),
                        DETECTOR, VERSION, "MEDIUM", "DETERMINISTIC", null
                    ));
                }
            }
            if (lower.contains("cors") && (lower.contains("configuration") || lower.contains("corsregistry") || lower.contains("corsconfiguration"))) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "SECURITY_CONFIGURATION", "SECURITY", "CORS", path,
                    "Java", "Spring Security", path, null, null, "CORS", file.contentHash(),
                    DETECTOR, VERSION, "MEDIUM", "DETERMINISTIC", null
                ));
            }
            if (lower.contains("csrf")) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "SECURITY_CONFIGURATION", "SECURITY", "CSRF", path,
                    "Java", "Spring Security", path, null, null, "CSRF", file.contentHash(),
                    DETECTOR, VERSION, "MEDIUM", "DETERMINISTIC", null
                ));
            }
            if (lower.contains("authenticationfilter") || lower.contains("onceperrequestfilter") && lower.contains("auth")) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "SECURITY_CONFIGURATION", "SECURITY", "AuthenticationFilter", path,
                    "Java", "Spring Security", path, null, null, "AuthenticationFilter", file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
            }
            if (lower.contains("preauthorize") || lower.contains("secured") || lower.contains("rolesallowed")) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "SECURITY_ANNOTATION", "SECURITY", "authorization_annotation", path,
                    "Java", "Spring Security", path, null, null, "authorization", file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
            }
            if (lower.contains("token") && (lower.contains("bearer") || lower.contains("authorization") || lower.contains("jwt"))) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "TOKEN_HANDLING", "SECURITY", "token_handling", path,
                    "Java", "Security", path, null, null, "token", file.contentHash(),
                    DETECTOR, VERSION, "MEDIUM", "DETERMINISTIC", null
                ));
            }
        }
        return obs;
    }
}
