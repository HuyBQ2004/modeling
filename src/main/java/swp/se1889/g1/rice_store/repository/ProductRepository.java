package swp.se1889.g1.rice_store.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import swp.se1889.g1.rice_store.dto.ProductZoneDTO;
import swp.se1889.g1.rice_store.entity.Product;
import swp.se1889.g1.rice_store.entity.Store;
import swp.se1889.g1.rice_store.entity.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import swp.se1889.g1.rice_store.entity.Zone;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    boolean existsByCreatedByAndNameAndIdNot(User createdBy, String name, Long id);

    Optional<Product> findById(Long id);

    @Query("SELECT COUNT(p) > 0 FROM Product p WHERE p.createdBy = :createdBy AND p.isDeleted = false AND LOWER(p.name) = LOWER(:name)")
    boolean existsByCreatedByAndName(@Param("createdBy") User createdBy, @Param("name") String name);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.createdBy = :createdBy AND p.isDeleted = false")
    long countByCreatedBy(@Param("createdBy") User createdBy);


    @Query("SELECT p FROM Product p WHERE p.isDeleted = false AND LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Product> searchProducts(@Param("keyword") String keyword);

}






