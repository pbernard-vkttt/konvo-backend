package com.vulkantechtt.konvo.channels;

import com.vulkantechtt.konvo.channels.dto.ChannelResponse;
import com.vulkantechtt.konvo.channels.dto.ConnectWhatsAppRequest;
import com.vulkantechtt.konvo.channels.dto.UpdateChannelRequest;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/channels")
@PreAuthorize("isAuthenticated()")
public class ChannelController {

    private final ChannelService channels;

    public ChannelController(ChannelService channels) {
        this.channels = channels;
    }

    @GetMapping
    public List<ChannelResponse> list(@AuthenticationPrincipal KonvoPrincipal principal) {
        return channels.list(principal.tenantId());
    }

    @GetMapping("/{id}")
    public ChannelResponse get(@AuthenticationPrincipal KonvoPrincipal principal,
                               @PathVariable UUID id) {
        return channels.get(principal.tenantId(), id);
    }

    @PostMapping("/whatsapp")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ChannelResponse connectWhatsApp(@AuthenticationPrincipal KonvoPrincipal principal,
                                           @Valid @RequestBody ConnectWhatsAppRequest req) {
        return channels.connectWhatsApp(principal, req);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ChannelResponse update(@AuthenticationPrincipal KonvoPrincipal principal,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody UpdateChannelRequest req) {
        return channels.update(principal, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public void disconnect(@AuthenticationPrincipal KonvoPrincipal principal,
                           @PathVariable UUID id) {
        channels.disconnect(principal, id);
    }
}
