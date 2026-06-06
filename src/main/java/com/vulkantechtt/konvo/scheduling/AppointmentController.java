package com.vulkantechtt.konvo.scheduling;

import com.vulkantechtt.konvo.scheduling.dto.AppointmentResponse;
import com.vulkantechtt.konvo.scheduling.dto.AvailabilityResponse;
import com.vulkantechtt.konvo.scheduling.dto.CreateAppointmentRequest;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Appointments + availability. Any authenticated agent can read availability,
 * list, book, and cancel (these are inbox actions). Settings/connect live in
 * {@link SchedulingSettingsController} / the Google OAuth controller.
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("isAuthenticated()")
public class AppointmentController {

    private final SchedulingService scheduling;

    public AppointmentController(SchedulingService scheduling) {
        this.scheduling = scheduling;
    }

    @GetMapping("/scheduling/availability")
    public AvailabilityResponse availability(@AuthenticationPrincipal KonvoPrincipal principal) {
        return scheduling.availability(principal.tenantId());
    }

    @GetMapping("/appointments")
    public List<AppointmentResponse> list(@AuthenticationPrincipal KonvoPrincipal principal,
                                          @RequestParam(required = false) UUID customerId,
                                          @RequestParam(required = false) AppointmentStatus status) {
        return scheduling.list(principal.tenantId(), customerId, status);
    }

    @PostMapping("/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse book(@AuthenticationPrincipal KonvoPrincipal principal,
                                    @Valid @RequestBody CreateAppointmentRequest req) {
        return scheduling.book(principal.tenantId(), req.customerId(), req.conversationId(),
                req.startsAt(), req.durationMinutes(), req.title(), req.notes(),
                AppointmentSource.agent, principal);
    }

    @DeleteMapping("/appointments/{id}")
    public AppointmentResponse cancel(@AuthenticationPrincipal KonvoPrincipal principal,
                                      @PathVariable UUID id) {
        return scheduling.cancel(principal, id);
    }
}
