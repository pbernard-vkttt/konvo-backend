package com.vulkantechtt.konvo.users;

import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.users.dto.ChangeRoleRequest;
import com.vulkantechtt.konvo.users.dto.InvitationResponse;
import com.vulkantechtt.konvo.users.dto.InviteMemberRequest;
import com.vulkantechtt.konvo.users.dto.MemberResponse;
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
@RequestMapping("/api/v1/members")
@PreAuthorize("isAuthenticated()")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public List<MemberResponse> list(@AuthenticationPrincipal KonvoPrincipal principal) {
        return memberService.listMembers(principal.tenantId());
    }

    @GetMapping("/invitations")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public List<InvitationResponse> pendingInvitations(@AuthenticationPrincipal KonvoPrincipal principal) {
        return memberService.listPendingInvitations(principal.tenantId());
    }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public InvitationResponse invite(
            @AuthenticationPrincipal KonvoPrincipal principal,
            @Valid @RequestBody InviteMemberRequest req) {
        return memberService.invite(principal, req);
    }

    @DeleteMapping("/invitations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public void revokeInvitation(
            @AuthenticationPrincipal KonvoPrincipal principal,
            @PathVariable UUID id) {
        memberService.revokeInvitation(principal, id);
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public MemberResponse changeRole(
            @AuthenticationPrincipal KonvoPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody ChangeRoleRequest req) {
        return memberService.changeRole(principal, id, req.role());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public void remove(
            @AuthenticationPrincipal KonvoPrincipal principal,
            @PathVariable UUID id) {
        memberService.remove(principal, id);
    }
}
