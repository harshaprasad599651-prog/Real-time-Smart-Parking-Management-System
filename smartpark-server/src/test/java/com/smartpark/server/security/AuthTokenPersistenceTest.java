package com.smartpark.server.security;

import com.smartpark.server.document.LoginSession;
import com.smartpark.server.document.UserRole;
import com.smartpark.server.support.AbstractMongoIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Sign-ins are stored (as hashes) so a server restart does not sign the terminals out. */
class AuthTokenPersistenceTest extends AbstractMongoIntegrationTest {

    @Test
    void aSignInSurvivesAServerRestart() {
        AuthTokenService beforeRestart = new AuthTokenService(Duration.ofHours(12), mongoTemplate);
        String token = beforeRestart.issue("entry1", UserRole.ENTRY_OPERATOR, "ENTRY-GATE-1").token();

        AuthTokenService afterRestart = new AuthTokenService(Duration.ofHours(12), mongoTemplate); // empty cache
        assertThat(afterRestart.validate(token)).hasValueSatisfying(s -> {
            assertThat(s.username()).isEqualTo("entry1");
            assertThat(s.role()).isEqualTo(UserRole.ENTRY_OPERATOR);
            assertThat(s.terminal()).isEqualTo("ENTRY-GATE-1");
        });
    }

    @Test
    void onlyTheHashOfTheTokenIsStored() {
        AuthTokenService service = new AuthTokenService(Duration.ofHours(12), mongoTemplate);
        String token = service.issue("exit1", UserRole.EXIT_OPERATOR, "EXIT-GATE-1").token();

        LoginSession stored = mongoTemplate.findAll(LoginSession.class).getFirst();
        assertThat(stored.getTokenHash()).hasSize(64).isNotEqualTo(token);
        assertThat(mongoTemplate.getCollection(LoginSession.COLLECTION).find().first().toJson()).doesNotContain(token);
    }

    @Test
    void logoutAndRevokeAllAlsoEndStoredSignIns() {
        AuthTokenService service = new AuthTokenService(Duration.ofHours(12), mongoTemplate);
        String a = service.issue("entry1", UserRole.ENTRY_OPERATOR, "A").token();
        String b = service.issue("entry1", UserRole.ENTRY_OPERATOR, "B").token();
        String c = service.issue("exit1", UserRole.EXIT_OPERATOR, "C").token();

        assertThat(service.revoke(a)).isTrue();
        assertThat(service.revokeAll("entry1")).isEqualTo(1);

        AuthTokenService afterRestart = new AuthTokenService(Duration.ofHours(12), mongoTemplate);
        assertThat(afterRestart.validate(a)).isEmpty();
        assertThat(afterRestart.validate(b)).isEmpty();
        assertThat(afterRestart.validate(c)).isPresent();
    }

    @Test
    void expiredSignInsAreRejectedAfterARestart() throws InterruptedException {
        AuthTokenService shortLived = new AuthTokenService(Duration.ofMillis(200), mongoTemplate);
        String token = shortLived.issue("admin", UserRole.ADMIN, "ADMIN").token();
        Thread.sleep(400);
        assertThat(new AuthTokenService(Duration.ofMillis(200), mongoTemplate).validate(token)).isEmpty();
    }
}
