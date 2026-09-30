package com.skilllink.api.analysis.detector;

import com.skilllink.api.analysis.AnalysisObservationRepository;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DatabaseDetector {
    public static final String DETECTOR = "DATABASE_DETECTOR";
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

            // Flyway migrations
            if (lower.contains("db/migration/") && (lower.endsWith(".sql"))) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "FLYWAY_MIGRATION", "DATABASE", "flyway_migration", path,
                    "SQL", "Flyway", path, null, null, path, file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
                // Try to extract table creation
                for (String line : content.split("\n")) {
                    String trimmed = line.trim().toLowerCase();
                    if (trimmed.startsWith("create table")) {
                        String tableName = trimmed.replace("create table", "").trim().split("\\s+")[0].replaceAll("[\"'`]", "");
                        obs.add(new AnalysisObservationRepository.ObservationRow(
                            UUID.randomUUID(), runId, snapshotId,
                            "DATABASE_TABLE", "DATABASE", tableName, "created",
                            "SQL", "Flyway", path, null, null, tableName, file.contentHash(),
                            DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                        ));
                    }
                }
            }

            // SQL files
            if (lower.endsWith(".sql") && !lower.contains("db/migration/")) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "SQL_FILE", "DATABASE", "sql_file", path,
                    "SQL", null, path, null, null, path, file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
            }

            // Liquibase
            if (lower.contains("liquibase") || lower.contains("db/changelog")) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "LIQUIBASE_FILE", "DATABASE", "liquibase", path,
                    "XML", "Liquibase", path, null, null, path, file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
            }

            // JPA repository interfaces (already covered in Java AST, but also detect via file name)
            if (lower.endsWith("repository.java") || lower.endsWith("repo.java")) {
                if (content.contains("extends JpaRepository") || content.contains("extends CrudRepository") || content.contains("extends PagingAndSortingRepository")) {
                    String repoName = path.substring(path.lastIndexOf('/') + 1).replace(".java", "");
                    obs.add(new AnalysisObservationRepository.ObservationRow(
                        UUID.randomUUID(), runId, snapshotId,
                        "JPA_REPOSITORY", "DATABASE", repoName, "JpaRepository",
                        "Java", "Spring Data JPA", path, null, null, repoName, file.contentHash(),
                        DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                    ));
                }
            }
        }
        return obs;
    }
}
