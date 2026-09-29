package com.skilllink.api.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skilllink.api.github.GithubClient;
import com.skilllink.api.github.GithubOAuthService;
import com.skilllink.api.github.GithubRepositoryJdbcRepository;
import com.skilllink.api.project.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositorySnapshotServiceTest {
    @Mock GithubClient github;
    @Mock GithubOAuthService githubOAuth;
    @Mock ProjectService projects;
    @Mock JdbcTemplate jdbc;
    private RepositorySnapshotService snapshots;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        snapshots = new RepositorySnapshotService(github, githubOAuth, projects, jdbc, mapper, new RepositoryFilePolicy(100_000, 1_000_000, 10, 100), new SecretRedactor());
    }

    @Test
    void fetchesOnlyRelevantFilesAndRedactsSecretsFromMockedGithubTree() throws Exception {
        UUID candidateId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID repositoryId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        var repository = new GithubRepositoryJdbcRepository.RepositoryRow(repositoryId, projectId, "acme/foodbridge", "main", "acme", "Java", "PRIVATE");
        var job = new AnalysisJobRepository.JobRecord(UUID.randomUUID(), candidateId, projectId, repositoryId, null, "QUEUED", 0, "Queued", 0, null, null, null, null, null);
        when(projects.repository(candidateId, projectId)).thenReturn(repository);
        when(githubOAuth.accessToken(candidateId)).thenReturn("mock-token");
        when(github.commit("acme/foodbridge", "main", "mock-token")).thenReturn(mapper.readTree("{\"sha\":\"commit-12345678\",\"commit\":{\"tree\":{\"sha\":\"tree-123\"}}}"));
        when(jdbc.queryForObject(contains("SELECT id, analysis_status"), any(org.springframework.jdbc.core.RowMapper.class), eq(repositoryId), eq("commit-12345678"))).thenThrow(new EmptyResultDataAccessException(1));
        when(jdbc.queryForObject(contains("INSERT INTO repository_snapshot"), eq(UUID.class), eq(repositoryId), eq("commit-12345678"), eq("main"), eq("pending:commit-12345678"), eq("tree-123"))).thenReturn(snapshotId);
        when(github.tree("acme/foodbridge", "tree-123", "mock-token")).thenReturn(mapper.readTree("""
            {"truncated":false,"tree":[
              {"path":"pom.xml","type":"blob","sha":"blob-pom","size":120},
              {"path":"src/main/java/App.java","type":"blob","sha":"blob-java","size":180},
              {"path":".env","type":"blob","sha":"blob-env","size":30},
              {"path":"target/App.class","type":"blob","sha":"blob-class","size":20}
            ]}
            """));
        when(github.blob("acme/foodbridge", "blob-pom", "mock-token")).thenReturn(encoded("<artifactId>spring-boot-starter-web</artifactId>"));
        when(github.blob("acme/foodbridge", "blob-java", "mock-token")).thenReturn(encoded("String token = api_key=super-secret-value;"));

        AnalysisModels.FetchedSnapshot fetched = snapshots.fetch(job);

        assertThat(fetched.snapshotId()).isEqualTo(snapshotId);
        assertThat(fetched.files()).extracting(AnalysisModels.SourceFile::path).containsExactly("pom.xml", "src/main/java/App.java");
        assertThat(fetched.files().get(1).content()).contains("[REDACTED_SECRET]").doesNotContain("super-secret-value");
        assertThat(fetched.redactedValues()).isEqualTo(1);
        assertThat(fetched.treeEntries()).isEqualTo(2);
    }

    private String encoded(String content) { return Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)); }
}
