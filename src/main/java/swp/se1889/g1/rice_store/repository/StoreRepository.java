package swp.se1889.g1.rice_store.repository;

import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import swp.se1889.g1.rice_store.entity.Store;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {
    @Modifying
    @Transactional
    @Query("UPDATE Store s SET s.isDeleted = :isDeleted WHERE s.id = :storeId")
    int updateStoreStatus(@Param("storeId") Long storeId, @Param("isDeleted") boolean isDeleted);

    List<Store> findByCreatedBy(String username);

    Store findByNameAndCreatedBy(String name, String createdBy);

    Store findByEmail(String email);

    Store findByPhone(String phone);

    Store findByAddressAndCreatedBy(String address, String createdBy);

    Page<Store> findByCreatedByAndIsDeletedFalse(String createdBy, Pageable pageable);

    @Query("SELECT s FROM Store s WHERE s.isDeleted = false " +
            "AND s.createdBy = :username " +
            "AND (:startId IS NULL OR :endId IS NULL OR s.id BETWEEN :startId AND :endId) " +
            "AND (:storeName IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :storeName, '%'))) " +
            "AND (:storeAddress IS NULL OR LOWER(s.address) LIKE LOWER(CONCAT('%', :storeAddress, '%'))) " +
            "AND (:storePhone IS NULL OR LOWER(s.phone) LIKE LOWER(CONCAT('%', :storePhone, '%'))) " +
            "AND (:storeEmail IS NULL OR LOWER(s.email) LIKE LOWER(CONCAT('%', :storeEmail, '%'))) " +
            "AND (:startCreatedAt IS NULL OR :endCreatedAt IS NULL OR s.createdAt BETWEEN :startCreatedAt AND :endCreatedAt) " +
            "AND (:startUpdatedAt IS NULL OR :endUpdatedAt IS NULL OR s.updatedAt BETWEEN :startUpdatedAt AND :endUpdatedAt)")
    Page<Store> searchStore(@Param("username") String username,
                            @Param("startId") Long startId,
                            @Param("endId") Long endId,
                            @Param("storeName") String storeName,
                            @Param("storeAddress") String storeAddress,
                            @Param("storePhone") String storePhone,
                            @Param("storeEmail") String storeEmail,
                            @Param("startCreatedAt") LocalDateTime startCreatedAt,
                            @Param("endCreatedAt") LocalDateTime endCreatedAt,
                            @Param("startUpdatedAt") LocalDateTime startUpdatedAt,
                            @Param("endUpdatedAt") LocalDateTime endUpdatedAt,
                            Pageable pageable);


}

