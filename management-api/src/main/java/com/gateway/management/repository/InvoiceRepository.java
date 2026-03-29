package com.gateway.management.repository;

import com.gateway.management.entity.InvoiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<InvoiceEntity, UUID> {

    List<InvoiceEntity> findByConsumerId(UUID consumerId);

    List<InvoiceEntity> findByStatus(String status);

    Optional<InvoiceEntity> findByPaystackReference(String paystackReference);
}
