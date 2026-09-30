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
MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC6i4Ewp5gZndL4
dkg7Y02URAYo/ItWHpJAcT8ngQ0MsYlcwcpnhJIM2WHrGq43deE82LPT4dpTGUPc
Ixq/7juutfgYEuoGeMrldwHTadsBGv4w7XZW1znLc+fyjjPlPTVcefwc/geGuIRS
gKFWaWSkd2aZw6H18i3AOApd8uynwcTD3QolPQEkUjS7YFv71jrK8qdSlir6DIDC
45VpPN4RbRJzD059HzMuT70JpF4VNkREmFv4Mm+HiUV+B0z1RdMG/3SmkB4uYLz+
pUhjDtF6HuBSNGa8ujI0qg7fb1mPjdqB5lqqsYGlQZf+F1fDveUR7XZG9WXTgwD0
u2CYE7yJAgMBAAECggEAM88R6srpFdg84y+qoP/T6IU59sEpC5qDAO3S3excDPXz
0fSqe7SYgYQQS2UGFLYi/6ypyQN1iNXAAJApWjvWjI/SXxFFuNVseG1kbux73CM8
jKcu0jCFRymiNj3jUNv+iXgBmqW8vmBZs7Yw2Nh9kKXus27fePixVyzZSD1zF6Xi
TtjQx6oGB2BTF5eLAXmU4mldnL6uhuCR2H/HxNgRezp494qiWYH7idz3BUH40Leo
iIP1yLTZS0Q+OVVP9qvnPFRPAyiHtMIjw0rGPRnNkY4z7FMqHFauaqMd25RLYzKX
Kj+TxjTDoubtbEKMbzw5mVZhwUt0PC0KOsSKqdmdvwKBgQDff0r/XOlu8MzRw26W
qg2OOIqn3h07Tk1Q1PH3j76rbsE7k8JuoF0M58pBjUaN0WlCkwIFho9MiSHX8USU
1PSinHPYkwyute8foiehQPH5EqjKXMHt0L06x1wvKY8mgRr/I6CBJZyH1P/P6QYK
QzaZ30UvGew1QEtcx6qIg5zM5wKBgQDVrH5M6ImcvoSrc9idU3ahFMFLd2v/9QJF
55sZs8YFo1cIdRpQKRjqGfw50j7/IEJuDaX5NpPQKFvU512KRK0sjyX3PM6Xq6t9
rmy2HvuQ0QGt0NXXRK784S7z2TRQdX6aWVxfxWuzklmWsr+I0PZzMn0j21Z0RFov
i+3LrSQNDwKBgQCUNLJEApXddpsr4p/8EwpoLEBuLxFNWZBVsNA/7TdwMUK8QjCl
8Xui6jYqIAgQVTSq7BdkGKYAB9PEgf6Lf2g2SO9dR64aURUf8gS7nviWxXPetYH4
NPq29qq/r5x42RZQ6Iwv+AQD4xA8C/dwaL/Va8RSBqunaFpRTSpd2oDpMwKBgB4l
LdRv4ipI/rKpr1/SzKPBJ9wbxxLfYCi4mjswD7nv53F8A/BXO/qbG+iUburThEu+
hTH5rzTo3LvWwG2nbr8gmvyciZEAWTtsBk5TZK5zrkb1dZXfTMjEhDlG5YiMawYf
oVappZ46AYWvRjJpOLvb3afXZAUXN5oJpczcan+lAoGBAMT7uZ67s9If5R5OWINp
vE0VABdkd/i7Wv+S+RbPb1tGbU9kNHsPt0SOJHWPLIeFImXKDELlFB59O66ra8FJ
XrUeVRZ36cgX0hcy0bOghaiNa9HRctX83KcWh4eXom2LWJKvjy6mUE0PSggRm/LZ
d6tbz16MYBpZ2DLGKxZdSXI/
-----END PRIVATE KEY-----""";

    private static final String PKCS1_PEM = """
-----BEGIN RSA PRIVATE KEY-----
MIIEpAIBAAKCAQEAuouBMKeYGZ3S+HZIO2NNlEQGKPyLVh6SQHE/J4ENDLGJXMHK
Z4SSDNlh6xquN3XhPNiz0+HaUxlD3CMav+47rrX4GBLqBnjK5XcB02nbARr+MO12
Vtc5y3Pn8o4z5T01XHn8HP4HhriEUoChVmlkpHdmmcOh9fItwDgKXfLsp8HEw90K
JT0BJFI0u2Bb+9Y6yvKnUpYq+gyAwuOVaTzeEW0Scw9OfR8zLk+9CaReFTZERJhb
+DJvh4lFfgdM9UXTBv90ppAeLmC8/qVIYw7Reh7gUjRmvLoyNKoO329Zj43ageZa
qrGBpUGX/hdXw73lEe12RvVl04MA9LtgmBO8iQIDAQABAoIBADPPEerK6RXYPOMv
qqD/0+iFOfbBKQuagwDt0t3sXAz189H0qnu0mIGEEEtlBhS2Iv+sqckDdYjVwACQ
KVo71oyP0l8RRbjVbHhtZG7se9wjPIynLtIwhUcpojY941Db/ol4AZqlvL5gWbO2
MNjYfZCl7rNu33j4sVcs2Ug9cxel4k7Y0MeqBgdgUxeXiwF5lOJpXZy+robgkdh/
x8TYEXs6ePeKolmB+4nc9wVB+NC3qIiD9ci02UtEPjlVT/ar5zxUTwMoh7TCI8NK
xj0ZzZGOM+xTKhxWrmqjHduUS2Mylyo/k8Y0w6Lm7WxCjG88OZlWYcFLdDwtCjrE
iqnZnb8CgYEA339K/1zpbvDM0cNulqoNjjiKp94dO05NUNTx94++q27BO5PCbqBd
DOfKQY1GjdFpQpMCBYaPTIkh1/FElNT0opxz2JMMrrXvH6InoUDx+RKoylzB7dC9
OsdcLymPJoEa/yOggSWch9T/z+kGCkM2md9FLxnsNUBLXMeqiIOczOcCgYEA1ax+
TOiJnL6Eq3PYnVN2oRTBS3dr//UCReebGbPGBaNXCHUaUCkY6hn8OdI+/yBCbg2l
+TaT0Chb1OddikStLI8l9zzOl6urfa5sth77kNEBrdDV10Su/OEu89k0UHV+mllc
X8Vrs5JZlrK/iND2czJ9I9tWdERaL4vty60kDQ8CgYEAlDSyRAKV3XabK+Kf/BMK
aCxAbi8RTVmQVbDQP+03cDFCvEIwpfF7ouo2KiAIEFU0quwXZBimAAfTxIH+i39o
NkjvXUeuGlEVH/IEu574lsVz3rWB+DT6tvaqv6+ceNkWUOiML/gEA+MQPAv3cGi/
1WvEUgarp2haUU0qXdqA6TMCgYAeJS3Ub+IqSP6yqa9f0syjwSfcG8cS32AouJo7
MA+57+dxfAPwVzv6mxvolG7q04RLvoUx+a806Ny71sBtp26/IJr8nImRAFk7bAZO
U2Suc65G9XWV30zIxIQ5RuWIjGsGH6FWqaWeOgGFr0YyaTi7292n12QFFzeaCaXM
3Gp/pQKBgQDE+7meu7PSH+UeTliDabxNFQAXZHf4u1r/kvkWz29bRm1PZDR7D7dE
jiR1jyyHhSJlygxC5RQefTuuq2vBSV61HlUWd+nIF9IXMtGzoIWojWvR0XLV/Nyn
FoeHl6Jti1iSr48uplBND0oIEZvy2XerW89ejGAaWdgyxisWXUlyPw==
-----END RSA PRIVATE KEY-----""";

    private static final String PUBLIC_PEM = """
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuouBMKeYGZ3S+HZIO2NN
lEQGKPyLVh6SQHE/J4ENDLGJXMHKZ4SSDNlh6xquN3XhPNiz0+HaUxlD3CMav+47
rrX4GBLqBnjK5XcB02nbARr+MO12Vtc5y3Pn8o4z5T01XHn8HP4HhriEUoChVmlk
pHdmmcOh9fItwDgKXfLsp8HEw90KJT0BJFI0u2Bb+9Y6yvKnUpYq+gyAwuOVaTze
EW0Scw9OfR8zLk+9CaReFTZERJhb+DJvh4lFfgdM9UXTBv90ppAeLmC8/qVIYw7R
eh7gUjRmvLoyNKoO329Zj43ageZaqrGBpUGX/hdXw73lEe12RvVl04MA9LtgmBO8
iQIDAQAB
-----END PUBLIC KEY-----""";

    private static final String SINGLE_LINE_PKCS8 = "-----BEGIN PRIVATE KEY-----\\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC6i4Ewp5gZndL4\\ndkg7Y02URAYo/ItWHpJAcT8ngQ0MsYlcwcpnhJIM2WHrGq43deE82LPT4dpTGUPc\\nIxq/7juutfgYEuoGeMrldwHTadsBGv4w7XZW1znLc+fyjjPlPTVcefwc/geGuIRS\\ngKFWaWSkd2aZw6H18i3AOApd8uynwcTD3QolPQEkUjS7YFv71jrK8qdSlir6DIDC\\n45VpPN4RbRJzD059HzMuT70JpF4VNkREmFv4Mm+HiUV+B0z1RdMG/3SmkB4uYLz+\\npUhjDtF6HuBSNGa8ujI0qg7fb1mPjdqB5lqqsYGlQZf+F1fDveUR7XZG9WXTgwD0\\nu2CYE7yJAgMBAAECggEAM88R6srpFdg84y+qoP/T6IU59sEpC5qDAO3S3excDPXz\\n0fSqe7SYgYQQS2UGFLYi/6ypyQN1iNXAAJApWjvWjI/SXxFFuNVseG1kbux73CM8\\njKcu0jCFRymiNj3jUNv+iXgBmqW8vmBZs7Yw2Nh9kKXus27fePixVyzZSD1zF6Xi\\nTtjQx6oGB2BTF5eLAXmU4mldnL6uhuCR2H/HxNgRezp494qiWYH7idz3BUH40Leo\\niIP1yLTZS0Q+OVVP9qvnPFRPAyiHtMIjw0rGPRnNkY4z7FMqHFauaqMd25RLYzKX\\nKj+TxjTDoubtbEKMbzw5mVZhwUt0PC0KOsSKqdmdvwKBgQDff0r/XOlu8MzRw26W\\nqg2OOIqn3h07Tk1Q1PH3j76rbsE7k8JuoF0M58pBjUaN0WlCkwIFho9MiSHX8USU\\n1PSinHPYkwyute8foiehQPH5EqjKXMHt0L06x1wvKY8mgRr/I6CBJZyH1P/P6QYK\\nQzaZ30UvGew1QEtcx6qIg5zM5wKBgQDVrH5M6ImcvoSrc9idU3ahFMFLd2v/9QJF\\n55sZs8YFo1cIdRpQKRjqGfw50j7/IEJuDaX5NpPQKFvU512KRK0sjyX3PM6Xq6t9\\nrmy2HvuQ0QGt0NXXRK784S7z2TRQdX6aWVxfxWuzklmWsr+I0PZzMn0j21Z0RFov\\ni+3LrSQNDwKBgQCUNLJEApXddpsr4p/8EwpoLEBuLxFNWZBVsNA/7TdwMUK8QjCl\\n8Xui6jYqIAgQVTSq7BdkGKYAB9PEgf6Lf2g2SO9dR64aURUf8gS7nviWxXPetYH4\\nNPq29qq/r5x42RZQ6Iwv+AQD4xA8C/dwaL/Va8RSBqunaFpRTSpd2oDpMwKBgB4l\\nLdRv4ipI/rKpr1/SzKPBJ9wbxxLfYCi4mjswD7nv53F8A/BXO/qbG+iUburThEu+\\nhTH5rzTo3LvWwG2nbr8gmvyciZEAWTtsBk5TZK5zrkb1dZXfTMjEhDlG5YiMawYf\\noVappZ46AYWvRjJpOLvb3afXZAUXN5oJpczcan+lAoGBAMT7uZ67s9If5R5OWINp\\nvE0VABdkd/i7Wv+S+RbPb1tGbU9kNHsPt0SOJHWPLIeFImXKDELlFB59O66ra8FJ\\nXrUeVRZ36cgX0hcy0bOghaiNa9HRctX83KcWh4eXom2LWJKvjy6mUE0PSggRm/LZ\\nd6tbz16MYBpZ2DLGKxZdSXI/\\n-----END PRIVATE KEY-----";

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
