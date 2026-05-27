package com.vulkantechtt.konvo.realtime;

import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/realtime")
public class RealtimeController {

    private final SseTicketService tickets;
    private final SseHub hub;

    public RealtimeController(SseTicketService tickets, SseHub hub) {
        this.tickets = tickets;
        this.hub = hub;
    }

    @PostMapping("/ticket")
    @PreAuthorize("isAuthenticated()")
    public TicketResponse issueTicket(@AuthenticationPrincipal KonvoPrincipal principal) {
        return new TicketResponse(tickets.issue(principal), 60);
    }

    /**
     * Long-lived SSE stream. Public path (in SecurityConfig's permit list)
     * because EventSource can't carry an Authorization header — the ticket
     * query parameter is the auth.
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String ticket) {
        var redeemed = tickets.redeem(ticket)
                .orElseThrow(() -> new KonvoException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "invalid_ticket", "Realtime ticket is missing, expired, or already used"));
        return hub.register(redeemed.tenantId());
    }

    public record TicketResponse(String ticket, int expiresInSeconds) {}
}
