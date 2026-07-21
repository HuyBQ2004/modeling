package swp.se1889.g1.rice_store.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import swp.se1889.g1.rice_store.entity.Invoices;
import swp.se1889.g1.rice_store.entity.Store;
import swp.se1889.g1.rice_store.entity.User;
import swp.se1889.g1.rice_store.dto.InvoiceSummaryDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoicesRepository extends JpaRepository<Invoices, Long>, JpaSpecificationExecutor<Invoices> {
    List<Invoices> findByType(Invoices.InvoiceType type);

    Page<Invoices> findByStoreAndType(Store store, Invoices.InvoiceType type, Pageable pageable);

    List<Invoices> findByStoreId(Long storeID);

    Page<Invoices> findById(Long id, Pageable pageable);

    Page<Invoices> findInvoicesByStore(Store store, Pageable pageable);


    @Query("SELECT COUNT(i) FROM Invoices i WHERE i.store.id = :storeId AND i.type = 'Sale' AND i.isDeleted = false AND i.createdAt BETWEEN :start AND :end")
    long countTodayInvoices(@Param("storeId") Long storeId,
                            @Param("start") LocalDateTime start,
                            @Param("end") LocalDateTime end);


    @Query("SELECT COALESCE(SUM(i.finalAmount), 0) FROM Invoices i WHERE i.store.id = :storeId AND i.type = 'Sale' AND i.isDeleted = false AND i.createdAt BETWEEN :start AND :end")
    BigDecimal sumTodayRevenue(@Param("storeId") Long storeId,
                               @Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);


    @Query(value = "SELECT DATEPART(WEEKDAY, i.created_at) AS weekday, SUM(i.final_amount) AS revenue FROM invoices i WHERE i.store_id = :storeId AND i.type = 'Sale' AND i.is_deleted = 0 AND i.created_at BETWEEN :start AND :end GROUP BY DATEPART(WEEKDAY, i.created_at) ORDER BY weekday", nativeQuery = true)
    List<Object[]> getRevenueByWeekday(@Param("storeId") Long storeId,
                                       @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);


    @Query(value = "SELECT MONTH(i.created_at) AS month, SUM(i.final_amount) AS revenue FROM invoices i WHERE i.store_id = :storeId AND i.type = 'Sale' AND i.is_deleted = 0 AND YEAR(i.created_at) = :year GROUP BY MONTH(i.created_at) ORDER BY month", nativeQuery = true)
    List<Object[]> getRevenueByMonth(@Param("storeId") Long storeId, @Param("year") int year);
    // =================================================================================
    // SCENARIO 1: SIMPLE READ

    // =================================================================================

    // 1.1 JPQL Approach
    @Query("SELECT i FROM Invoices i WHERE i.id = :id")
    Optional<Invoices> findByIdJPQL(@Param("id") Long id);

    // 1.2 Native SQL Approach
    @Query(value = "SELECT * FROM invoices WHERE id = :id", nativeQuery = true)
    Optional<Invoices> findByIdNative(@Param("id") Long id);


    // =================================================================================
    // SCENARIO 2: COMPLEX AGGREGATION
    // =================================================================================

    // 2.1 JPQL Approach
    @Query("SELECT MONTH(i.createdAt) as month, SUM(i.finalAmount) as revenue " +
            "FROM Invoices i " +
            "WHERE i.store.id = :storeId " +
            "AND i.type = 'Sale' " +
            "AND i.isDeleted = false " +
            "AND YEAR(i.createdAt) = :year " +
            "GROUP BY MONTH(i.createdAt) " +
            "ORDER BY month")
    List<Object[]> getRevenueByMonthJPQL(@Param("storeId") Long storeId, @Param("year") int year);

    // 2.2 Native SQL Approach
    @Query(value = "SELECT MONTH(i.created_at) AS month, SUM(i.final_amount) AS revenue " +
            "FROM invoices i " +
            "WHERE i.store_id = :storeId " +
            "AND i.type = 'Sale' " +
            "AND i.is_deleted = 0 " +
            "AND YEAR(i.created_at) = :year " +
            "GROUP BY MONTH(i.created_at) " +
            "ORDER BY month", nativeQuery = true)
    List<Object[]> getRevenueByMonthNative(@Param("storeId") Long storeId, @Param("year") int year);


    // =================================================================================
    // SCENARIO 3: N+1 PROBLEM SIMULATION
    // =================================================================================

    // 3.1 JPQL Approach
    @Query("SELECT i FROM Invoices i WHERE i.store.id = :storeId ORDER BY i.createdAt DESC ")
    List<Invoices> findTop5000ByStoreJPQL(@Param("storeId") Long storeId, Pageable pageable);

    // 3.2 Native SQL Approach
    @Query(value = "SELECT  top 5000 * " +
            "FROM invoices i " +
            "WHERE i.store_id = :storeId " +
            "ORDER BY i.created_at DESC", nativeQuery = true)
    List<Invoices> findTop5000ByStoreNative(@Param("storeId") Long storeId);

    // [NEW] 3.3 JPQL Optimized

    @Query("SELECT i FROM Invoices i " +
            "JOIN FETCH i.store " +
            "JOIN FETCH i.customer " +
            "JOIN FETCH i.createdBy " +
            "WHERE i.store.id = :storeId " +
            "ORDER BY i.createdAt DESC "
    )
    List<Invoices> findTop5000ByStoreJPQLOptimized(@Param("storeId") Long storeId, Pageable pageable);
// =================================================================================
    // [NEW] SCENARIO 4: DTO PROJECTION
    // =================================================================================

    // 4.1 JPQL  DTO
    @Query("""
            SELECT i.id as id, i.finalAmount as finalAmount, i.createdAt as createdAt 
            FROM Invoices i 
            WHERE i.store.id = :storeId 
            ORDER BY i.createdAt DESC
            """)
    List<InvoiceSummaryDTO> findTop5000JPQLDTO(@Param("storeId") Long storeId, Pageable pageable);

    // 4.2 Native SQL  DTO
    @Query(value = """
            SELECT TOP 5000 id, final_amount as finalAmount, created_at as createdAt 
            FROM invoices 
            WHERE store_id = :storeId 
            ORDER BY created_at DESC
            """, nativeQuery = true)
    List<InvoiceSummaryDTO> findTop5000NativeDTO(@Param("storeId") Long storeId);
}
