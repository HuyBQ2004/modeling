package swp.se1889.g1.rice_store.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import swp.se1889.g1.rice_store.entity.Invoice;
import swp.se1889.g1.rice_store.entity.Store;
import swp.se1889.g1.rice_store.repository.InvoiceSaleRepository;

import java.util.List;
import java.util.Optional;

@Service
public class InvoiceSaleService {

    @Autowired
    private InvoiceSaleRepository invoiceSaleRepository;

    public Page<Invoice> findInvoicesByStoreId(Long storeId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return invoiceSaleRepository.findByStoreIdAndTypeAndIsDeletedFalse(storeId, "Sale", pageable);
    }

    public void deleteInvoice(Long invoiceId) {
        Invoice invoice = invoiceSaleRepository.findById(invoiceId).orElse(null);
        if (invoice != null) {
            invoice.setDeleted(true);
            invoiceSaleRepository.save(invoice);
        }
    }

    public Invoice findById(Long id) {
        Optional<Invoice> invoice = invoiceSaleRepository.findById(id);
        return invoice.orElse(null);
    }
}
