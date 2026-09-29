package com.skilllink.api.github;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class GithubRepositoryJdbcRepository {
    private final JdbcTemplate jdbc;
    public GithubRepositoryJdbcRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<RepositoryRow> findByCandidateAndGithubId(UUID candidateId, String githubId) {
        try { return Optional.ofNullable(jdbc.queryForObject("""
            SELECT r.id, p.id project_id, r.full_name, r.default_branch, r.owner_login, r.primary_language, r.visibility
            FROM repository r JOIN project p ON p.id = r.project_id
            WHERE p.candidate_id = ? AND r.provider = 'GITHUB' AND r.external_id = ?
            """, (rs, rowNum) -> new RepositoryRow(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class), rs.getString("full_name"), rs.getString("default_branch"), rs.getString("owner_login"), rs.getString("primary_language"), rs.getString("visibility")), candidateId, githubId)); }
        catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
    }
    public Optional<RepositoryRow> findByProjectAndCandidate(UUID candidateId, UUID projectId) {
        try { return Optional.ofNullable(jdbc.queryForObject("""
            SELECT r.id, p.id project_id, r.full_name, r.default_branch, r.owner_login, r.primary_language, r.visibility
            FROM repository r JOIN project p ON p.id = r.project_id
            WHERE p.candidate_id = ? AND p.id = ?
            """, (rs, rowNum) -> new RepositoryRow(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class), rs.getString("full_name"), rs.getString("default_branch"), rs.getString("owner_login"), rs.getString("primary_language"), rs.getString("visibility")), candidateId, projectId)); }
        catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
    }
    public RepositoryRow insert(UUID candidateId, GithubDtos.RepositorySummary remote) {
        UUID projectId = jdbc.queryForObject("INSERT INTO project(candidate_id, name, kind, summary, visibility) VALUES (?, ?, 'Repository', ?, ?) RETURNING id", UUID.class, candidateId, remote.name(), remote.description(), remote.privateRepository() ? "PRIVATE" : "PUBLIC");
        UUID repositoryId = jdbc.queryForObject("""
            INSERT INTO repository(project_id, provider, external_id, full_name, visibility, default_branch, owner_login, primary_language, github_updated_at, last_synced_at)
            VALUES (?, 'GITHUB', ?, ?, ?, ?, ?, ?, ?, now()) RETURNING id
            """, UUID.class, projectId, remote.id(), remote.fullName(), remote.privateRepository() ? "PRIVATE" : "PUBLIC", remote.defaultBranch(), remote.owner(), remote.primaryLanguage(), remote.updatedAt() == null ? null : java.sql.Timestamp.from(remote.updatedAt()));
        return new RepositoryRow(repositoryId, projectId, remote.fullName(), remote.defaultBranch(), remote.owner(), remote.primaryLanguage(), remote.privateRepository() ? "PRIVATE" : "PUBLIC");
    }
    public java.util.List<RepositoryRow> listByCandidate(UUID candidateId) { return jdbc.query("""
        SELECT r.id, p.id project_id, r.full_name, r.default_branch, r.owner_login, r.primary_language, r.visibility
        FROM repository r JOIN project p ON p.id = r.project_id WHERE p.candidate_id = ? ORDER BY p.updated_at DESC
        """, (rs, rowNum) -> new RepositoryRow(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class), rs.getString("full_name"), rs.getString("default_branch"), rs.getString("owner_login"), rs.getString("primary_language"), rs.getString("visibility")), candidateId); }
    public record RepositoryRow(UUID id, UUID projectId, String fullName, String defaultBranch, String owner, String primaryLanguage, String visibility) {}
}
