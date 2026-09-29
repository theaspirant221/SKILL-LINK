package com.skilllink.api.github;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for GitHub App installations. An installation is deliberately separate from the
 * candidate's GitHub user identity: it records which GitHub account/repositories SkillLink is
 * authorized to access through the GitHub App.
 */
@Repository
public class GithubInstallationRepository {
    private final JdbcTemplate jdbc;
    public GithubInstallationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REMOVED = "REMOVED";
    public static final String STATUS_SUSPENDED = "SUSPENDED";

    public record InstallationRow(UUID id, UUID candidateId, long installationId, long accountId, String accountLogin, String accountType, String repositorySelection, String status) {}

    public Optional<InstallationRow> findActiveByCandidate(UUID candidateId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT id, candidate_id, installation_id, account_id, account_login, account_type, repository_selection, status FROM github_installation WHERE candidate_id = ? AND status = 'ACTIVE' ORDER BY installed_at DESC LIMIT 1", this::map, candidateId));
        } catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
    }

    public Optional<InstallationRow> findByInstallationId(long installationId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT id, candidate_id, installation_id, account_id, account_login, account_type, repository_selection, status FROM github_installation WHERE installation_id = ?", this::map, installationId));
        } catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
    }

    public List<InstallationRow> listByCandidate(UUID candidateId) {
        return jdbc.query("SELECT id, candidate_id, installation_id, account_id, account_login, account_type, repository_selection, status FROM github_installation WHERE candidate_id = ? ORDER BY installed_at DESC", this::map, candidateId);
    }

    /** Creates or refreshes the local installation record for a candidate. */
    public void upsertForCandidate(UUID candidateId, GithubAppClient.Installation installation) {
        Optional<InstallationRow> existing = findByInstallationId(installation.installationId());
        if (existing.isPresent() && !existing.get().candidateId().equals(candidateId)) {
            throw new GithubOAuthService.GithubConfigurationException("INSTALLATION_ALREADY_LINKED", "That GitHub App installation is already linked to another SkillLink account.");
        }
        jdbc.update("""
            INSERT INTO github_installation(candidate_id, installation_id, account_id, account_login, account_type, repository_selection, status, installed_at, last_validated_at)
            VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', now(), now())
            ON CONFLICT (installation_id) DO UPDATE SET candidate_id = EXCLUDED.candidate_id, account_id = EXCLUDED.account_id,
              account_login = EXCLUDED.account_login, account_type = EXCLUDED.account_type, repository_selection = EXCLUDED.repository_selection,
              status = 'ACTIVE', last_validated_at = now(), updated_at = now()
            """, candidateId, installation.installationId(), installation.accountId(), installation.accountLogin(), installation.accountType(), installation.repositorySelection());
    }

    public void updateStatus(long installationId, String status) {
        jdbc.update("UPDATE github_installation SET status = ?, last_validated_at = now(), updated_at = now() WHERE installation_id = ?", status, installationId);
    }

    public void deleteForCandidate(UUID candidateId) { jdbc.update("DELETE FROM github_installation WHERE candidate_id = ?", candidateId); }

    /** Marks repository references as revoked when access is removed, without deleting history. */
    public int markRepositoriesRevoked(List<String> fullNames) {
        if (fullNames == null || fullNames.isEmpty()) return 0;
        int updated = 0;
        for (String fullName : fullNames) updated += jdbc.update("UPDATE repository SET connection_status = 'REVOKED', updated_at = now() WHERE provider = 'GITHUB' AND full_name = ?", fullName);
        return updated;
    }

    public int markRepositoriesAvailable(List<String> fullNames) {
        if (fullNames == null || fullNames.isEmpty()) return 0;
        int updated = 0;
        for (String fullName : fullNames) updated += jdbc.update("UPDATE repository SET connection_status = 'CONNECTED', updated_at = now() WHERE provider = 'GITHUB' AND full_name = ?", fullName);
        return updated;
    }

    private InstallationRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new InstallationRow(rs.getObject("id", UUID.class), rs.getObject("candidate_id", UUID.class), rs.getLong("installation_id"), rs.getLong("account_id"), rs.getString("account_login"), rs.getString("account_type"), rs.getString("repository_selection"), rs.getString("status"));
    }
}
