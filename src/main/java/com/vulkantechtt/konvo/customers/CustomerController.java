package com.vulkantechtt.konvo.customers;

import com.vulkantechtt.konvo.common.PageResponse;
import com.vulkantechtt.konvo.customers.dto.CustomerResponse;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
@PreAuthorize("isAuthenticated()")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<CustomerResponse> list(
            @AuthenticationPrincipal KonvoPrincipal principal,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return service.list(principal, search, pageable);
    }

    @GetMapping("/{id}")
    public CustomerResponse get(
            @AuthenticationPrincipal KonvoPrincipal principal,
            @PathVariable UUID id) {
        return service.get(principal, id);
    }
}
