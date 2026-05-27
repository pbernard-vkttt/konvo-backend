package com.vulkantechtt.konvo.customers;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByTenantIdAndPhone(UUID tenantId, String phone);

    @Query("""
            select c from Customer c
            where c.tenantId = :tenantId
              and (
                :search is null
                or :search = ''
                or lower(c.displayName) like concat('%', lower(cast(:search as string)), '%')
                or lower(c.profileName) like concat('%', lower(cast(:search as string)), '%')
                or c.phone like concat('%', cast(:search as string), '%')
              )
            order by c.updatedAt desc
            """)
    Page<Customer> search(@Param("tenantId") UUID tenantId,
                          @Param("search") String search,
                          Pageable pageable);
}
