package it.unipi.ase.tamer.players.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.unipi.ase.tamer.players.client.AuthClient;
import it.unipi.ase.tamer.players.client.TokenResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ServiceTokenProviderTest {

    @Test
    void cachesTokenUntilThirtySecondsBeforeExpiryAndRefreshesAfter() {
        AuthClient authClient = mock(AuthClient.class);
        when(authClient.token("client_credentials", "players", "secret"))
                .thenReturn(
                        new TokenResponse("token-1", "Bearer", 300, "service"),
                        new TokenResponse("token-2", "Bearer", 300, "service"),
                        new TokenResponse("token-3", "Bearer", 300, "service"));

        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        ServiceTokenProvider provider = new ServiceTokenProvider();
        provider.authClient = authClient;
        provider.clientId = "players";
        provider.clientSecret = "secret";
        provider.clock = clock;

        // First call fetches token-1.
        assertEquals("token-1", provider.token());
        verify(authClient, times(1)).token("client_credentials", "players", "secret");

        // 269s later (31s before expiry): still cached.
        clock.advanceSeconds(269);
        assertEquals("token-1", provider.token());
        verify(authClient, times(1)).token("client_credentials", "players", "secret");

        // 1s later (30s before expiry): refreshes to token-2.
        clock.advanceSeconds(1);
        assertEquals("token-2", provider.token());
        verify(authClient, times(2)).token("client_credentials", "players", "secret");

        // After full expiry (350s later): refreshes to token-3.
        clock.advanceSeconds(350);
        assertEquals("token-3", provider.token());
        verify(authClient, times(3)).token("client_credentials", "players", "secret");
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        MutableClock(Instant initial) {
            this.current = initial;
        }

        void advanceSeconds(long seconds) {
            this.current = this.current.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
