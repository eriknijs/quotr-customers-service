package com.quotr.customers.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<CustomerEntity, UUID> {

    Optional<CustomerEntity> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, String ownerId);

    List<CustomerEntity> findByOwnerIdAndDeletedAtIsNullOrderByNameAscCreatedAtAsc(String ownerId);

    Page<CustomerEntity> findByOwnerIdAndDeletedAtIsNull(String ownerId, Pageable pageable);

    @Query("""
            select c from CustomerEntity c
            where c.ownerId = :ownerId
              and c.deletedAt is null
              and (
                lower(c.name) like lower(concat('%', :query, '%'))
                or lower(coalesce(c.email, '')) like lower(concat('%', :query, '%'))
                or lower(coalesce(c.addressLine1, '')) like lower(concat('%', :query, '%'))
                or lower(coalesce(c.addressLine2, '')) like lower(concat('%', :query, '%'))
                or lower(coalesce(c.postalCode, '')) like lower(concat('%', :query, '%'))
                or lower(coalesce(c.city, '')) like lower(concat('%', :query, '%'))
                or lower(coalesce(c.country, '')) like lower(concat('%', :query, '%'))
              )
            order by c.name asc, c.createdAt asc
            """)
    List<CustomerEntity> searchActiveOwned(@Param("ownerId") String ownerId, @Param("query") String query);

    @Query("""
            select c from CustomerEntity c
            where c.ownerId = :ownerId
              and c.deletedAt is null
              and (
                lower(c.name) like lower(concat('%', :query, '%'))
                or lower(coalesce(c.email, '')) like lower(concat('%', :query, '%'))
                or lower(coalesce(c.addressLine1, '')) like lower(concat('%', :query, '%'))
                or lower(coalesce(c.addressLine2, '')) like lower(concat('%', :query, '%'))
                or lower(coalesce(c.postalCode, '')) like lower(concat('%', :query, '%'))
                or lower(coalesce(c.city, '')) like lower(concat('%', :query, '%'))
                or lower(coalesce(c.country, '')) like lower(concat('%', :query, '%'))
              )
            """)
    Page<CustomerEntity> searchActiveOwned(@Param("ownerId") String ownerId,
                                           @Param("query") String query,
                                           Pageable pageable);
}
