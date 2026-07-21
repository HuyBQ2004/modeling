package swp.se1889.g1.rice_store.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import swp.se1889.g1.rice_store.entity.Invoices;
import swp.se1889.g1.rice_store.entity.InvoicesDetails;
import swp.se1889.g1.rice_store.entity.Store;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceDetailRepository extends JpaRepository<InvoicesDetails, Long> {
    Page<InvoicesDetails> findAllByIsDeletedFalse(Pageable pageable);

    Optional<InvoicesDetails> findById(Long id);
    List<InvoicesDetails> findByInvoice(Invoices invoice);
    @Query("SELECT i FROM InvoicesDetails i WHERE i.invoice.id = :invoiceId AND i.zone.isDeleted = false")
    List<InvoicesDetails> findByInvoiceIdAndZoneNotDeleted(@Param("invoiceId") Long invoiceId);
    // Chỉ lấy chi tiết hóa đơn có Zone chưa bị xóa
    @Query("SELECT d FROM InvoicesDetails d WHERE d.invoice = :invoice AND d.zone.isDeleted = false")
    List<InvoicesDetails> findActiveInvoiceDetails(@Param("invoice") Invoices invoice);
    @Query("SELECT d.product.name, SUM(d.quantity) " +
            "FROM InvoicesDetails d " +
            "WHERE d.invoice.type = 'Sale' AND d.isDeleted = false " +
            "GROUP BY d.product.name " +
            "ORDER BY SUM(d.quantity) DESC")
    List<Object[]> findTop5ProductsSold(Pageable pageable);
}
