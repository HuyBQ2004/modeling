package swp.se1889.g1.rice_store.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import swp.se1889.g1.rice_store.entity.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    List<User> findByCreatedByAndRole(long createdBy, String role);
    List<User> findAllByCreatedByAndRoleAndIsDeleted(long createdBy, String role, boolean isDelete);
    Page<User> findByCreatedByAndRole(Long createdBy, String role, Pageable pageable);
    Page<User> findByCreatedByAndRoleAndIsDeleted(Long createdBy, String role, boolean isDeleted, Pageable pageable);


    @Query("SELECT u FROM User u WHERE u.createdBy = :storeId AND u.role = :role AND u.isDeleted = :isDeleted " +
            "AND (COALESCE(:name, '') = '' OR u.name LIKE CONCAT('%', :name, '%')) " +
            "AND (COALESCE(:email, '') = '' OR u.email LIKE CONCAT('%', :email, '%')) " +
            "AND (COALESCE(:phone, '') = '' OR u.phone LIKE CONCAT('%', :phone, '%')) " +
            "AND (COALESCE(:address, '') = '' OR u.address LIKE CONCAT('%', :address, '%'))")
    Page<User> advancedSearchEmployees(
            @Param("storeId") Long storeId,
            @Param("role") String role,
            @Param("isDeleted") boolean isDeleted,
            @Param("name") String name,
            @Param("email") String email,
            @Param("phone") String phone,
            @Param("address") String address,
            Pageable pageable
    );
}
