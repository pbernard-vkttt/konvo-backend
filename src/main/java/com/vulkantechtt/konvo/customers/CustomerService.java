package com.vulkantechtt.konvo.customers;

import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.common.PageResponse;
import com.vulkantechtt.konvo.customers.dto.CustomerResponse;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customers are read-only in M4 — the only writer is the webhook ingest.
 * Edit / merge / tagging lands later.
 */
@Service
public class CustomerService {

    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(KonvoPrincipal principal, String search, Pageable pageable) {
        return PageResponse.from(customers.search(principal.tenantId(), search, pageable)
                .map(CustomerService::toResponse));
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(KonvoPrincipal principal, UUID id) {
        Customer c = customers.findById(id)
                .orElseThrow(() -> KonvoException.notFound("Customer", id));
        if (!c.getTenantId().equals(principal.tenantId())) {
            throw KonvoException.notFound("Customer", id);
        }
        return toResponse(c);
    }

    private static CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(
                c.getId(),
                c.getDisplayName(),
                c.getProfileName(),
                c.getPhone(),
                c.getLocale(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
