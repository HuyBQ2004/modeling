package swp.se1889.g1.rice_store.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import swp.se1889.g1.rice_store.entity.InvoiceDetail;

import java.util.List;

@Repository
public interface InvoiceSaleDetailRepository extends JpaRepository<InvoiceDetail, Long> {

    List<InvoiceDetail> findInvoiceDetailByInvoiceId(Long invoiceDetailId);
}
