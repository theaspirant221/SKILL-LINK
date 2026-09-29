package com.skilllink.api.passport;

import com.skilllink.api.audit.AuditService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PassportService {
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();
    public PassportService(JdbcTemplate jdbc, AuditService audit) { this.jdbc = jdbc; this.audit = audit; }

    public PassportDtos.PassportResponse get(UUID candidateId) { PassportRow passport = findOrCreate(candidateId); return response(passport, candidateId); }

    @Transactional
    public PassportDtos.PassportResponse issue(UUID candidateId, String requestId) {
        PassportRow passport = findOrCreate(candidateId);
        long verified = jdbc.queryForObject("SELECT count(*) FROM verification_result WHERE candidate_id = ? AND status = 'VERIFIED'", Long.class, candidateId);
        if (verified == 0) throw new PassportInputException("VERIFICATION_REQUIRED", "A Proof Passport can only be issued after at least one skill is verified by policy.");
        refreshItems(passport.id(), candidateId);
        audit.record(candidateId, "PASSPORT_ISSUED", "PROOF_PASSPORT", passport.id(), requestId, Map.of("itemCount", verified));
        return response(findOrCreate(candidateId), candidateId);
    }

    @Transactional
    public PassportDtos.PassportResponse visibility(UUID candidateId, String value, String requestId) {
        if (!List.of("PRIVATE", "PUBLIC_SUMMARY", "PUBLIC").contains(value)) throw new PassportInputException("PASSPORT_VISIBILITY_INVALID", "Passport visibility must be PRIVATE, PUBLIC_SUMMARY, or PUBLIC.");
        PassportRow passport = findOrCreate(candidateId);
        jdbc.update("UPDATE proof_passport SET visibility = ?, updated_at = now() WHERE id = ?", value, passport.id());
        audit.record(candidateId, "PASSPORT_VISIBILITY_UPDATED", "PROOF_PASSPORT", passport.id(), requestId, Map.of("visibility", value));
        return response(findOrCreate(candidateId), candidateId);
    }

    public PassportDtos.PublicPassportResponse publicPassport(String identifier) { try { PassportRow passport = jdbc.queryForObject("SELECT pp.id, pp.public_identifier, pp.candidate_id, u.display_name, pp.visibility, pp.issued_at, pp.expires_at, pp.revoked_at FROM proof_passport pp JOIN app_user u ON u.id = pp.candidate_id WHERE pp.public_identifier = ? AND pp.visibility <> 'PRIVATE' AND pp.revoked_at IS NULL AND (pp.expires_at IS NULL OR pp.expires_at > now())", (rs, rowNum) -> new PassportRow(rs.getObject("id", UUID.class), rs.getString("public_identifier"), rs.getObject("candidate_id", UUID.class), rs.getString("display_name"), rs.getString("visibility"), instant(rs, "issued_at"), instant(rs, "expires_at"), rs.getObject("revoked_at", OffsetDateTime.class) != null), identifier); List<PassportDtos.PassportItem> items = items(passport.id()); return new PassportDtos.PublicPassportResponse(passport.publicIdentifier(), passport.displayName(), passport.visibility(), passport.issuedAt(), passport.expiresAt(), items); } catch (org.springframework.dao.EmptyResultDataAccessException ex) { throw new PassportNotFoundException(); } }

    private void refreshItems(UUID passportId, UUID candidateId) {
        List<VerifiedItem> verified = jdbc.query("""
            SELECT DISTINCT ON (vr.skill_id) vr.skill_id, s.name, s.category, vr.status, vp.version policy_version, vr.explanation, vr.evaluated_at
            FROM verification_result vr JOIN skill s ON s.id = vr.skill_id JOIN verification_policy vp ON vp.id = vr.policy_id
            WHERE vr.candidate_id = ? AND vr.status = 'VERIFIED' ORDER BY vr.skill_id, vr.evaluated_at DESC
            """, (rs, rowNum) -> new VerifiedItem(rs.getObject("skill_id", UUID.class), rs.getString("name"), rs.getString("category"), rs.getString("status"), rs.getString("policy_version"), rs.getString("explanation"), instant(rs, "evaluated_at")), candidateId);
        int order = 0;
        for (VerifiedItem item : verified) {
            String summary = "Verified under policy " + item.policyVersion() + ". " + item.explanation();
            jdbc.update("""
                INSERT INTO proof_passport_item(passport_id, skill_id, verification_result_id, visibility, display_summary, sort_order)
                VALUES (?, ?, (SELECT vr.id FROM verification_result vr WHERE vr.skill_id = ? AND vr.candidate_id = ? AND vr.status = 'VERIFIED' ORDER BY vr.evaluated_at DESC LIMIT 1), 'PUBLIC_SUMMARY', ?, ?)
                ON CONFLICT (passport_id, skill_id) DO UPDATE SET verification_result_id = EXCLUDED.verification_result_id, display_summary = EXCLUDED.display_summary, sort_order = EXCLUDED.sort_order
                """, passportId, item.skillId(), item.skillId(), candidateId, summary, order++);
        }
    }

    private PassportDtos.PassportResponse response(PassportRow passport, UUID candidateId) { return new PassportDtos.PassportResponse(passport.id(), passport.publicIdentifier(), candidateId, passport.visibility(), passport.issuedAt(), passport.expiresAt(), passport.revoked(), items(passport.id())); }
    private List<PassportDtos.PassportItem> items(UUID passportId) { return jdbc.query("SELECT ppi.skill_id, s.name, s.category, vr.status, vp.version policy_version, ppi.display_summary, vr.evaluated_at FROM proof_passport_item ppi JOIN skill s ON s.id = ppi.skill_id LEFT JOIN verification_result vr ON vr.id = ppi.verification_result_id LEFT JOIN verification_policy vp ON vp.id = vr.policy_id WHERE ppi.passport_id = ? ORDER BY ppi.sort_order, s.name", (rs, rowNum) -> new PassportDtos.PassportItem(rs.getObject("skill_id", UUID.class), rs.getString("name"), rs.getString("category"), rs.getString("status"), rs.getString("policy_version"), rs.getString("display_summary"), instant(rs, "evaluated_at")), passportId); }
    private PassportRow findOrCreate(UUID candidateId) { try { return jdbc.queryForObject("SELECT pp.id, pp.public_identifier, pp.candidate_id, u.display_name, pp.visibility, pp.issued_at, pp.expires_at, pp.revoked_at IS NOT NULL revoked FROM proof_passport pp JOIN app_user u ON u.id = pp.candidate_id WHERE pp.candidate_id = ? ORDER BY pp.issued_at DESC LIMIT 1", (rs, rowNum) -> new PassportRow(rs.getObject("id", UUID.class), rs.getString("public_identifier"), rs.getObject("candidate_id", UUID.class), rs.getString("display_name"), rs.getString("visibility"), instant(rs, "issued_at"), instant(rs, "expires_at"), rs.getBoolean("revoked")), candidateId); } catch (org.springframework.dao.EmptyResultDataAccessException ex) { return jdbc.queryForObject("INSERT INTO proof_passport(candidate_id, public_identifier, visibility) VALUES (?, ?, 'PRIVATE') RETURNING id, public_identifier, candidate_id, visibility, issued_at, expires_at, revoked_at IS NOT NULL revoked", (rs, rowNum) -> new PassportRow(rs.getObject("id", UUID.class), rs.getString("public_identifier"), rs.getObject("candidate_id", UUID.class), null, rs.getString("visibility"), instant(rs, "issued_at"), instant(rs, "expires_at"), rs.getBoolean("revoked")), candidateId, publicIdentifier()); } }
    private String publicIdentifier() { byte[] bytes = new byte[18]; random.nextBytes(bytes); return "slp_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException { OffsetDateTime value = rs.getObject(column, OffsetDateTime.class); return value == null ? null : value.toInstant(); }
    private record PassportRow(UUID id, String publicIdentifier, UUID candidateId, String displayName, String visibility, Instant issuedAt, Instant expiresAt, boolean revoked) {}
    private record VerifiedItem(UUID skillId, String name, String category, String status, String policyVersion, String explanation, Instant evaluatedAt) {}
    public static class PassportNotFoundException extends RuntimeException {}
    public static class PassportInputException extends RuntimeException { private final String code; public PassportInputException(String code, String message) { super(message); this.code = code; } public String code() { return code; } }
}
