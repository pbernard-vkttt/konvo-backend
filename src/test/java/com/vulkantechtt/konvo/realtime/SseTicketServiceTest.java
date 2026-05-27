package com.vulkantechtt.konvo.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.users.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SseTicketServiceTest {

    private final SseTicketService service = new SseTicketService();

    @Test
    void issuedTicketRedeemsOnceWithMatchingTenant() {
        KonvoPrincipal p = new KonvoPrincipal(
                UUID.randomUUID(), "a@b.tt", "A", UUID.randomUUID(), Role.AGENT);
        String ticket = service.issue(p);

        var first = service.redeem(ticket);
        assertThat(first).isPresent();
        assertThat(first.get().tenantId()).isEqualTo(p.tenantId());

        // Single-use: second redeem fails.
        assertThat(service.redeem(ticket)).isEmpty();
    }

    @Test
    void unknownTicketRejected() {
        assertThat(service.redeem("not-a-real-ticket")).isEmpty();
    }

    @Test
    void nullOrBlankTicketRejected() {
        assertThat(service.redeem(null)).isEmpty();
        assertThat(service.redeem("")).isEmpty();
    }
}
