package com.vulkantechtt.konvo.scheduling;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    List<Appointment> findByTenantIdOrderByStartsAtDesc(UUID tenantId);

    List<Appointment> findByTenantIdAndStatusOrderByStartsAtDesc(UUID tenantId, AppointmentStatus status);

    List<Appointment> findByTenantIdAndCustomerIdOrderByStartsAtDesc(UUID tenantId, UUID customerId);
}
