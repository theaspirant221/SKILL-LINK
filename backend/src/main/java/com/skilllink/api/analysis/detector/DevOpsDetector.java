package com.skilllink.api.analysis.detector;

import com.skilllink.api.analysis.AnalysisObservationRepository;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DevOpsDetector {
    public static final String DETECTOR = "DEVOPS_DETECTOR";
    public static final String VERSION = "deterministic-v1";

    public List<AnalysisObservationRepository.ObservationRow> detect(
        UUID runId,
        UUID snapshotId,
        List<LanguageDetector.SnapshotFile> files
    ) {
        List<AnalysisObservationRepository.ObservationRow> obs = new ArrayList<>();
        for (LanguageDetector.SnapshotFile file : files) {
            String path = file.path();
            String lower = path.toLowerCase();
            String content = file.content();
            if (content == null) continue;

            if (lower.equals("dockerfile") || lower.endsWith("/dockerfile")) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "DOCKERFILE_PRESENT", "DEVOPS", "Dockerfile", "present",
                    null, "Docker", path, null, null, "Dockerfile", file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
            }
            if (lower.equals("docker-compose.yml") || lower.equals("docker-compose.yaml") || lower.endsWith("/docker-compose.yml") || lower.endsWith("/docker-compose.yaml")) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "DOCKER_COMPOSE_PRESENT", "DEVOPS", "docker-compose", "present",
                    "YAML", "Docker Compose", path, null, null, "docker-compose", file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
            }
            if (lower.contains(".github/workflows/") && (lower.endsWith(".yml") || lower.endsWith(".yaml"))) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "CI_CONFIGURATION", "DEVOPS", "github_actions", path,
                    "YAML", "GitHub Actions", path, null, null, path, file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
                // Detect workflow tools
                if (content.contains("actions/checkout")) {
                    obs.add(new AnalysisObservationRepository.ObservationRow(
                        UUID.randomUUID(), runId, snapshotId,
                        "CI_TOOL", "DEVOPS", "actions/checkout", "present",
                        "YAML", "GitHub Actions", path, null, null, "checkout", file.contentHash(),
                        DETECTOR, VERSION, "MEDIUM", "DETERMINISTIC", null
                    ));
                }
                if (content.contains("setup-java")) {
                    obs.add(new AnalysisObservationRepository.ObservationRow(
                        UUID.randomUUID(), runId, snapshotId,
                        "CI_TOOL", "DEVOPS", "setup-java", "present",
                        "YAML", "GitHub Actions", path, null, null, "setup-java", file.contentHash(),
                        DETECTOR, VERSION, "MEDIUM", "DETERMINISTIC", null
                    ));
                }
                if (content.contains("docker")) {
                    obs.add(new AnalysisObservationRepository.ObservationRow(
                        UUID.randomUUID(), runId, snapshotId,
                        "CI_TOOL", "DEVOPS", "docker", "present",
                        "YAML", "Docker", path, null, null, "docker", file.contentHash(),
                        DETECTOR, VERSION, "MEDIUM", "DETERMINISTIC", null
                    ));
                }
            }
            if (lower.endsWith(".env.example") || lower.endsWith(".env.template")) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "ENV_TEMPLATE", "DEVOPS", "env_template", path,
                    null, null, path, null, null, path, file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
            }
        }
        return obs;
    }
}
