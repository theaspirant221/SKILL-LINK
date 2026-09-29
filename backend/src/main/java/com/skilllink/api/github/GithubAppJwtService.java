package com.skilllink.api.github;

import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Creates the short-lived GitHub App JWT used for server-to-server GitHub App endpoints such as
 * generating an installation access token. This token is completely separate from the SkillLink
 * application JWT used for candidate authentication, and it never leaves the server.
 */
@Service
public class GithubAppJwtService {
    private static final long ISSUED_AT_SKEW_SECONDS = 60;
    private static final long LIFETIME_SECONDS = 540; // GitHub caps app JWTs at 10 minutes.

    private final GithubProperties properties;
    private volatile PrivateKey cachedKey;

    public GithubAppJwtService(GithubProperties properties) { this.properties = properties; }

    public String createAppJwt() {
        Instant now = Instant.now();
        return Jwts.builder()
            .issuer(properties.appId())
            .issuedAt(Date.from(now.minusSeconds(ISSUED_AT_SKEW_SECONDS)))
            .expiration(Date.from(now.plusSeconds(LIFETIME_SECONDS)))
            .signWith(privateKey(), Jwts.SIG.RS256)
            .compact();
    }

    private PrivateKey privateKey() {
        PrivateKey key = cachedKey;
        if (key != null) return key;
        synchronized (this) {
            if (cachedKey == null) cachedKey = loadPrivateKey();
            return cachedKey;
        }
    }

    /**
     * Loads the GitHub App private key from configuration. Supports multi-line PEM and the
     * single-line form with literal "\n" escapes used by environment variables. Both PKCS#8
     * ("BEGIN PRIVATE KEY") and PKCS#1 ("BEGIN RSA PRIVATE KEY") formats are accepted. Key
     * material is never included in exception messages or logs.
     */
    private PrivateKey loadPrivateKey() {
        if (!properties.appFlowConfigured()) throw new GithubClient.GithubException("GITHUB_APP_NOT_CONFIGURED", "GitHub App credentials are not configured on this environment.", 503);
        String pem = properties.privateKey().replace("\\n", "\n").trim();
        boolean pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
        boolean pkcs8 = pem.contains("BEGIN PRIVATE KEY");
        if (!pkcs1 && !pkcs8) throw new GithubClient.GithubException("GITHUB_APP_KEY_INVALID", "The GitHub App private key is not a supported PEM format.", 503);
        String base64Body = pem.replaceAll("-----(BEGIN|END)( RSA)? PRIVATE KEY-----", "").replaceAll("\\s", "");
        byte[] der;
        try {
            der = Base64.getDecoder().decode(base64Body);
        } catch (IllegalArgumentException ex) {
            throw new GithubClient.GithubException("GITHUB_APP_KEY_INVALID", "The GitHub App private key could not be decoded.", 503);
        }
        byte[] pkcs8Der = pkcs1 ? wrapPkcs1InPkcs8(der) : der;
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8Der));
        } catch (Exception ex) {
            throw new GithubClient.GithubException("GITHUB_APP_KEY_INVALID", "The GitHub App private key could not be loaded.", 503);
        }
    }

    /** Wraps an RSAPrivateKey (PKCS#1) DER body in a PKCS#8 PrivateKeyInfo structure. */
    private static byte[] wrapPkcs1InPkcs8(byte[] pkcs1) {
        // PrivateKeyInfo ::= SEQUENCE { INTEGER 0, AlgorithmIdentifier(rsaEncryption), OCTET STRING pkcs1 }
        byte[] algorithmIdentifier = {0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
        byte[] version = {0x02, 0x01, 0x00};
        byte[] octetStringHeader = derLength(0x04, pkcs1.length);
        int innerLength = version.length + algorithmIdentifier.length + octetStringHeader.length + pkcs1.length;
        byte[] outerHeader = derLength(0x30, innerLength);
        byte[] out = new byte[outerHeader.length + innerLength];
        int pos = 0;
        pos = append(out, pos, outerHeader);
        pos = append(out, pos, version);
        pos = append(out, pos, algorithmIdentifier);
        pos = append(out, pos, octetStringHeader);
        append(out, pos, pkcs1);
        return out;
    }

    private static byte[] derLength(int tag, int length) {
        if (length < 128) return new byte[]{(byte) tag, (byte) length};
        if (length <= 0xff) return new byte[]{(byte) tag, (byte) 0x81, (byte) length};
        return new byte[]{(byte) tag, (byte) 0x82, (byte) (length >> 8), (byte) length};
    }

    private static int append(byte[] target, int pos, byte[] source) {
        System.arraycopy(source, 0, target, pos, source.length);
        return pos + source.length;
    }
}
