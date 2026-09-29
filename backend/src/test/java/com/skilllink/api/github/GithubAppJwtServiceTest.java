package com.skilllink.api.github;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GitHub App JWT generation. The PEM below is a throwaway 2048-bit RSA key generated for these
 * tests; it is not a real credential. These checks also assert that key material never leaks
 * through exception messages.
 */
class GithubAppJwtServiceTest {
    private static final String APP_ID = "123456";

    private static final String PKCS8_PEM = """
-----BEGIN PRIVATE KEY-----
MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDQHZlRUhlP6oP3
SSqfBrpXR3NHeh5UX6E4z2ypHDJg0SF/gNwBqf2JhlxFe0TFOrIJbF28wOFSd6b9
dLx/bgxYj8TKlNlT8qUxM/eF1aFGuPTne/WQn680VjN95Hzlbe84Dcdkm5l4++Fj
bV6eKw+xiXOX3DbrhDHd3aatk1bQTguq+ogaSZWfW+v44wdCByBrxjGqNZ23s1OH
TjtiqZ7amnzt+va3HAVa449t5FIrJvXL5fbk52fNhYBeQszMhY1TgzMbLnSi9PuB
2YX9X0nWTuZT9Tmc8UnKoytzhO0mnxC9TgRxd+q4g1lJdFw4pI8aoLXzrf4zs4b/
VqRrup6dAgMBAAECggEAH7LlHOVP31kdk5yh23SyDO7gSK/5jv69Nu16eVbxohyI
fnwOm/RGnrFdPDoL3gH+3H+UgYEGxkGyU42kfmuxLAtt59tR2j8DA/e3M7vieOay
wCV54Ew/lU2slyLRFpMEP6SdonMu0PuF4i07nwTOZaDFf152llsyyz6ND4gkeM1R
Z0VVGrQIdpUHymYErJ2rmje6+ABkQjcgJLVQkrL1VxwWtZPJarSkYbnnRTbxY62q
y9Yj8IfqMxHyT2JID4zsGND1zQxc1Q8Hr1/FIlSXqYPI4CjPEUsjsOXwP9Ih8geE
luKL812RAt2Vawj0o8FAGly7UEoXkQDsI4K36KFm8wKBgQDt2Uzb/9uvKIEYS4r8
GSOj7/N//+xVkMy0Begmf5grkQTK2ymiz4/TLlZa5e6SZrU5JCZgVLByK/vaD/UY
GclOqte0tFnb1YjSqRWqgx0nsRrSVmNx2TTrRq7IGgVkJYkjNb2vuhTTfO64Ccy1
F1yAnRL/ccQrRKl8PqTUCqUd8wKBgQDf/2uKFmhu9fNxBRHvUV2oLaejeiYFVWHo
9qBhRKdV+P2WOxX7ReR7xJFisu2d4cgUBqKM0nYbMIfBQnYe5JdQvdWvwJ3CxPiz
UsabAYIUyimVT4C6iHV9MxlPVKq//SMcaZWvwbab99+X+Gw+ipKmn1DuqrSQ1FRY
zHYKRRIlLwKBgQCF9k4D2yewj++l72ZodwBL5WoQPeSujM++1VTb2iGq6BL67lWV
DbLEDuU0bSzh6FdJx8KVnx2CMKO5PiOdX2iylibH8Ixr9OkLPZCmaRKSuH2S2nVI
Oj5EBZuLuJwwu7Nx0WL1BqmUNFl/7oUNugqvpch9d2Y1uIZ9JAtSImy9YQKBgFiB
jNVQC02kot8KWM7NwIreFznx3qoG1Zv+LtqgDNpcep5slD/nmuMIhUWRW3AhsTOw
d3PbCM2vfERxZUjJm7xMde1u1ycJOxdn4o+GpgZe5tVXR47ssjeZBCwjUSBw/fmR
ApMioGu6Ij/i6apAiLeLhaf4DUaYjwdTKmTea9ADAoGBAOF62ubVKt3tve6ta8NB
ExWMmWSg1cOSbfSbpESzdQAFHhe0UG2Jnv7Ch2Uv7ilf92ub5C6/VSGzVREiQdJ6
QLn/bGUkVVKwa9LC3g88EibbCLKSwtAgZf/2QibgwQlcgEZ+axPznckktMYb0Elo
nB9YUtd6dQtvyI4qU2XVwCyj
-----END PRIVATE KEY-----""";

    private static final String PKCS1_PEM = """
-----BEGIN RSA PRIVATE KEY-----
MIIEpAIBAAKCAQEA0B2ZUVIZT+qD90kqnwa6V0dzR3oeVF+hOM9sqRwyYNEhf4Dc
Aan9iYZcRXtExTqyCWxdvMDhUnem/XS8f24MWI/EypTZU/KlMTP3hdWhRrj053v1
kJ+vNFYzfeR85W3vOA3HZJuZePvhY21enisPsYlzl9w264Qx3d2mrZNW0E4LqvqI
GkmVn1vr+OMHQgcga8YxqjWdt7NTh047Yqme2pp87fr2txwFWuOPbeRSKyb1y+X2
5OdnzYWAXkLMzIWNU4MzGy50ovT7gdmF/V9J1k7mU/U5nPFJyqMrc4TtJp8QvU4E
cXfquINZSXRcOKSPGqC1863+M7OG/1aka7qenQIDAQABAoIBAB+y5RzlT99ZHZOc
odt0sgzu4Eiv+Y7+vTbtenlW8aIciH58Dpv0Rp6xXTw6C94B/tx/lIGBBsZBslON
pH5rsSwLbefbUdo/AwP3tzO74njmssAleeBMP5VNrJci0RaTBD+knaJzLtD7heIt
O58EzmWgxX9edpZbMss+jQ+IJHjNUWdFVRq0CHaVB8pmBKydq5o3uvgAZEI3ICS1
UJKy9VccFrWTyWq0pGG550U28WOtqsvWI/CH6jMR8k9iSA+M7BjQ9c0MXNUPB69f
xSJUl6mDyOAozxFLI7Dl8D/SIfIHhJbii/NdkQLdlWsI9KPBQBpcu1BKF5EA7COC
t+ihZvMCgYEA7dlM2//bryiBGEuK/Bkjo+/zf//sVZDMtAXoJn+YK5EEytspos+P
0y5WWuXukma1OSQmYFSwciv72g/1GBnJTqrXtLRZ29WI0qkVqoMdJ7Ea0lZjcdk0
60auyBoFZCWJIzW9r7oU03zuuAnMtRdcgJ0S/3HEK0SpfD6k1AqlHfMCgYEA3/9r
ihZobvXzcQUR71FdqC2no3omBVVh6PagYUSnVfj9ljsV+0Xke8SRYrLtneHIFAai
jNJ2GzCHwUJ2HuSXUL3Vr8CdwsT4s1LGmwGCFMoplU+Auoh1fTMZT1Sqv/0jHGmV
r8G2m/ffl/hsPoqSpp9Q7qq0kNRUWMx2CkUSJS8CgYEAhfZOA9snsI/vpe9maHcA
S+VqED3krozPvtVU29ohqugS+u5VlQ2yxA7lNG0s4ehXScfClZ8dgjCjuT4jnV9o
spYmx/CMa/TpCz2QpmkSkrh9ktp1SDo+RAWbi7icMLuzcdFi9QaplDRZf+6FDboK
r6XIfXdmNbiGfSQLUiJsvWECgYBYgYzVUAtNpKLfCljOzcCK3hc58d6qBtWb/i7a
oAzaXHqebJQ/55rjCIVFkVtwIbEzsHdz2wjNr3xEcWVIyZu8THXtbtcnCTsXZ+KP
hqYGXubVV0eO7LI3mQQsI1EgcP35kQKTIqBruiI/4umqQIi3i4Wn+A1GmI8HUypk
3mvQAwKBgQDhetrm1Srd7b3urWvDQRMVjJlkoNXDkm30m6REs3UABR4XtFBtiZ7+
wodlL+4pX/drm+Quv1Uhs1URIkHSekC5/2xlJFVSsGvSwt4PPBIm2wiyksLQIGX/
9kIm4MEJXIBGfmsT853JJLTGG9BJaJwfWFLXenULb8iOKlNl1cAsow==
-----END RSA PRIVATE KEY-----""";

    private static final String PUBLIC_PEM = """
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApmyr+qRZ7WAZdeirVRVp
0cV/KmixpoZnploQNxHu9A7+WuGp3x39QEkTStimFbtMu3jYBlRFjAcjeQ3AZ/m9
Jvoj5rSjNh2H0L5w0sQUgOTn7ihQJU48revrnzALK7ywou5vMwWq3F0YX8wmUXlc
FnEoSL2aXyyPoqi1yJuM7Goqkq+EK7ImPkFcZU0Pf1jY/xfa5tR4fkPhQNoYXaOB
mLS7Lni4n9uZrC25u43bqntDMgFXbD/V2viVpcqUMDxOmvX6FhcJtet80XFK+vY/
ER+sdZvy5uSTSciTtKzDVutAhMLyP714MFmnO2eY3x+9H36w8hXleMWW/9xpERc8
xQIDAQAB
-----END PUBLIC KEY-----""";

    private static final String SINGLE_LINE_PKCS8 = "-----BEGIN PRIVATE KEY-----\\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDQHZlRUhlP6oP3\\nSSqfBrpXR3NHeh5UX6E4z2ypHDJg0SF/gNwBqf2JhlxFe0TFOrIJbF28wOFSd6b9\\ndLx/bgxYj8TKlNlT8qUxM/eF1aFGuPTne/WQn680VjN95Hzlbe84Dcdkm5l4++Fj\\nbV6eKw+xiXOX3DbrhDHd3aatk1bQTguq+ogaSZWfW+v44wdCByBrxjGqNZ23s1OH\\nTjtiqZ7amnzt+va3HAVa449t5FIrJvXL5fbk52fNhYBeQszMhY1TgzMbLnSi9PuB\\n2YX9X0nWTuZT9Tmc8UnKoytzhO0mnxC9TgRxd+q4g1lJdFw4pI8aoLXzrf4zs4b/\\nVqRrup6dAgMBAAECggEAH7LlHOVP31kdk5yh23SyDO7gSK/5jv69Nu16eVbxohyI\\nfnwOm/RGnrFdPDoL3gH+3H+UgYEGxkGyU42kfmuxLAtt59tR2j8DA/e3M7vieOay\\nwCV54Ew/lU2slyLRFpMEP6SdonMu0PuF4i07nwTOZaDFf152llsyyz6ND4gkeM1R\\nZ0VVGrQIdpUHymYErJ2rmje6+ABkQjcgJLVQkrL1VxwWtZPJarSkYbnnRTbxY62q\\ny9Yj8IfqMxHyT2JID4zsGND1zQxc1Q8Hr1/FIlSXqYPI4CjPEUsjsOXwP9Ih8geE\\nluKL812RAt2Vawj0o8FAGly7UEoXkQDsI4K36KFm8wKBgQDt2Uzb/9uvKIEYS4r8\\nGSOj7/N//+xVkMy0Begmf5grkQTK2ymiz4/TLlZa5e6SZrU5JCZgVLByK/vaD/UY\\nGclOqte0tFnb1YjSqRWqgx0nsRrSVmNx2TTrRq7IGgVkJYkjNb2vuhTTfO64Ccy1\\nF1yAnRL/ccQrRKl8PqTUCqUd8wKBgQDf/2uKFmhu9fNxBRHvUV2oLaejeiYFVWHo\\n9qBhRKdV+P2WOxX7ReR7xJFisu2d4cgUBqKM0nYbMIfBQnYe5JdQvdWvwJ3CxPiz\\nUsabAYIUyimVT4C6iHV9MxlPVKq//SMcaZWvwbab99+X+Gw+ipKmn1DuqrSQ1FRY\\nzHYKRRIlLwKBgQCF9k4D2yewj++l72ZodwBL5WoQPeSujM++1VTb2iGq6BL67lWV\\nDbLEDuU0bSzh6FdJx8KVnx2CMKO5PiOdX2iylibH8Ixr9OkLPZCmaRKSuH2S2nVI\\nOj5EBZuLuJwwu7Nx0WL1BqmUNFl/7oUNugqvpch9d2Y1uIZ9JAtSImy9YQKBgFiB\\njNVQC02kot8KWM7NwIreFznx3qoG1Zv+LtqgDNpcep5slD/nmuMIhUWRW3AhsTOw\\nd3PbCM2vfERxZUjJm7xMde1u1ycJOxdn4o+GpgZe5tVXR47ssjeZBCwjUSBw/fmR\\nApMioGu6Ij/i6apAiLeLhaf4DUaYjwdTKmTea9ADAoGBAOF62ubVKt3tve6ta8NB\\nExWMmWSg1cOSbfSbpESzdQAFHhe0UG2Jnv7Ch2Uv7ilf92ub5C6/VSGzVREiQdJ6\\nQLn/bGUkVVKwa9LC3g88EibbCLKSwtAgZf/2QibgwQlcgEZ+axPznckktMYb0Elo\\nnB9YUtd6dQtvyI4qU2XVwCyj\\n-----END PRIVATE KEY-----";

    @Test
    void createsVerifiableRs256AppJwtFromPkcs8Key() {
        GithubAppJwtService service = new GithubAppJwtService(properties(PKCS8_PEM));
        String jwt = service.createAppJwt();
        Claims claims = parse(jwt);
        assertEquals(APP_ID, claims.getIssuer());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        long lifetime = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertTrue(lifetime <= 600, "GitHub app JWTs must not exceed ten minutes, was " + lifetime);
    }

    @Test
    void loadsPkcs1TraditionalPemFormat() {
        GithubAppJwtService service = new GithubAppJwtService(properties(PKCS1_PEM));
        assertEquals(APP_ID, parse(service.createAppJwt()).getIssuer());
    }

    @Test
    void loadsSingleLineEnvironmentStylePem() {
        GithubAppJwtService service = new GithubAppJwtService(properties(SINGLE_LINE_PKCS8));
        assertEquals(APP_ID, parse(service.createAppJwt()).getIssuer());
    }

    @Test
    void invalidKeyFailsWithoutLeakingKeyMaterial() {
        GithubAppJwtService service = new GithubAppJwtService(properties("-----BEGIN PRIVATE KEY-----\nnot-base64!!!\n-----END PRIVATE KEY-----"));
        GithubClient.GithubException ex = assertThrows(GithubClient.GithubException.class, service::createAppJwt);
        assertEquals("GITHUB_APP_KEY_INVALID", ex.code());
        assertFalse(ex.getMessage().contains("not-base64"), "exception messages must not echo key material");
    }

    @Test
    void unconfiguredAppIsRejectedExplicitly() {
        GithubAppJwtService service = new GithubAppJwtService(new GithubProperties(null, null, null, null, null, null, null, null, null, null, null));
        GithubClient.GithubException ex = assertThrows(GithubClient.GithubException.class, service::createAppJwt);
        assertEquals("GITHUB_APP_NOT_CONFIGURED", ex.code());
    }

    private Claims parse(String jwt) {
        try {
            String base64Body = PUBLIC_PEM.replaceAll("-----(BEGIN|END) PUBLIC KEY-----", "").replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64Body);
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
            return Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(jwt).getPayload();
        } catch (Exception ex) {
            throw new AssertionError("app JWT could not be verified with the matching public key", ex);
        }
    }

    private GithubProperties properties(String privateKey) {
        return new GithubProperties(APP_ID, "Iv1.test", "secret", privateKey, "skilllink-test-app", "http://localhost:8080/api/v1/github/callback", "http://localhost:8080/api/v1/github/install/callback", "webhook-secret", "https://api.github.com", "https://github.com/login/oauth", "2026-03-10");
    }
}
