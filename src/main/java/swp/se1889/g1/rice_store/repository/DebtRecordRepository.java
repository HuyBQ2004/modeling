package swp.se1889.g1.rice_store.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import swp.se1889.g1.rice_store.entity.DebtRecords;
import swp.se1889.g1.rice_store.entity.User;
import swp.se1889.g1.rice_store.dto.DebtRecordDTO;

import java.util.List;
import java.util.Optional;

public interface DebtRecordRepository extends JpaRepository<DebtRecords, Long>, JpaSpecificationExecutor<DebtRecords> {
    //List<DebtRecords> findByUserId(User createdBy);


    List<DebtRecords> findByCustomerId(Long customerId);

    Page<DebtRecords> findByCustomerId(Long customerId, Pageable pageable);

    // =================================================================================
    // SCENARIO 1: SIMPLE READ
    // =================================================================================

    // 1.1 JPQL/JPA Standard

    Optional<DebtRecords> findById(Long id);

    // 1.2 Native SQL Approach
    // Lưu ý: Dù Native nhưng trả về Entity class, Hibernate vẫn phải map kết quả vào Object.
    @Query(value = "SELECT * FROM debt_records WHERE id = :id", nativeQuery = true)
    Optional<DebtRecords> findByIdNative(@Param("id") Long id);
// =================================================================================
    // SCENARIO 2: COMPLEX AGGREGATION

    // =================================================================================

    // Native SQL Approach
    // Database performs aggregation before results are returned to the application.

    @Query(value = """
            SELECT 
                YEAR(d.create_on) AS year,
                MONTH(d.create_on) AS month,
                SUM(d.amount) AS total_debt
            FROM debt_records d
            WHERE d.customer_id = :customerId
              AND d.is_deleted = 0
            GROUP BY YEAR(d.create_on), MONTH(d.create_on)
            ORDER BY year, month
            """, nativeQuery = true)
    List<Object[]> getMonthlyDebtNative(@Param("customerId") Long customerId);

    // JPQL Approach
    // Aggregation is expressed using HQL temporal functions.

    @Query("""
            SELECT 
                YEAR(d.createOn) as year, 
                MONTH(d.createOn) as month, 
                SUM(d.amount) as totalDebt
            FROM DebtRecords d
            WHERE d.customerId = :customerId
              AND d.isDelete = false
            GROUP BY YEAR(d.createOn), MONTH(d.createOn)
            ORDER BY year, month
            """)
    List<Object[]> getMonthlyDebtJPQL(@Param("customerId") Long customerId);


    // =================================================================================
    // SCENARIO 3: LARGE DATA FETCH
    // =================================================================================

    // 3. Native SQL Entity
    @Query(value = """
            SELECT  TOP 5000 *
            FROM debt_records 
            WHERE customer_id = :customerId 
            ORDER BY create_on DESC
            """, nativeQuery = true)
    List<DebtRecords> getHistoryNative(@Param("customerId") Long customerId);

    // 4. JPQL (Entity Loading )

    @Query("""
            SELECT d 
            FROM DebtRecords d 
            WHERE d.customerId = :customerId 
            ORDER BY d.createOn DESC 
            
            """)
    List<DebtRecords> getHistoryJPQL(@Param("customerId") Long customerId, Pageable pageable);
    // =================================================================================
// [NEW] SCENARIO 4: DTO PROJECTION
// =================================================================================

    // 4.1 JPQL  DTO
    @Query("""
            SELECT d.id as id, d.amount as amount, d.createOn as createOn 
            FROM DebtRecords d 
            WHERE d.customerId = :customerId 
            ORDER BY d.createOn DESC
            """)
    List<DebtRecordDTO> getHistoryJPQLDTO(@Param("customerId") Long customerId, Pageable pageable);

    // 4.2 Native SQL  DTO
    @Query(value = """
            SELECT TOP 5000 id, amount, create_on as createOn 
            FROM debt_records 
            WHERE customer_id = :customerId 
            ORDER BY create_on DESC
            """, nativeQuery = true)
    List<DebtRecordDTO> getHistoryNativeDTO(@Param("customerId") Long customerId);

}
