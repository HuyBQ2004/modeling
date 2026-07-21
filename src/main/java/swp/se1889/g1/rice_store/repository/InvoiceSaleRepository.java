package swp.se1889.g1.rice_store.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import swp.se1889.g1.rice_store.entity.Invoice;
import swp.se1889.g1.rice_store.entity.Store;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface InvoiceSaleRepository extends JpaRepository<Invoice, Long> {

    Page<Invoice> findByStoreIdAndTypeAndIsDeletedFalse(Long storeId, String type, Pageable pageable);

    Optional<Invoice> findById(Long id);
    
}
