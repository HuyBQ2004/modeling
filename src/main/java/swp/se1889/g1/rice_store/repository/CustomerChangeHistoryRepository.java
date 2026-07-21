package swp.se1889.g1.rice_store.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import swp.se1889.g1.rice_store.entity.Customer;
import swp.se1889.g1.rice_store.entity.CustomerChangeHistory;
import swp.se1889.g1.rice_store.entity.User;

import java.time.LocalDateTime;


@Repository
public interface CustomerChangeHistoryRepository extends JpaRepository<CustomerChangeHistory, Long> {
    Page<CustomerChangeHistory> findByCustomerOrderByChangedAtDesc(Customer customer, Pageable pageable);

    Page<CustomerChangeHistory> findAll(Specification<CustomerChangeHistory> spec, Pageable pageable);

    @Query("SELECT c FROM CustomerChangeHistory c WHERE " +
            "(COALESCE(:customerName, '') = '' OR c.customer.name LIKE CONCAT('%', :customerName, '%')) " +
            "AND (COALESCE(:changedField, '') = '' OR c.changedField LIKE CONCAT('%', :changedField, '%')) " +
            "AND (COALESCE(:oldValue, '') = '' OR c.oldValue LIKE CONCAT('%', :oldValue, '%')) " +
            "AND (COALESCE(:changedBy, '') = '' OR c.changedBy.name LIKE CONCAT('%', :changedBy, '%')) " +
            "AND (COALESCE(:addInfo, '') = '' OR c.additionalInfo LIKE CONCAT('%', :addInfo, '%')) " +
            "AND (COALESCE(:startDate, null) IS NULL OR c.changedAt >= :startDate) " +
            "AND (COALESCE(:endDate, null) IS NULL OR c.changedAt <= :endDate)")
    Page<CustomerChangeHistory> advancedSearchCustomerChangeHistory(
            @Param("customerName") String customerName,
            @Param("changedField") String changedField,
            @Param("oldValue") String oldValue,
            @Param("changedBy") String changedBy,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("addInfo") String addInfo,
            Pageable pageable
    );
}