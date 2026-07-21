package swp.se1889.g1.rice_store.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import swp.se1889.g1.rice_store.entity.InvoiceDetail;
import swp.se1889.g1.rice_store.repository.InvoiceSaleDetailRepository;

import java.util.List;

@Service
public class InvoiceSaleDetailService {

    @Autowired
    private InvoiceSaleDetailRepository invoiceSaleDetailRepository;

    public List<InvoiceDetail> findByInvoiceDetailId(Long invoiceDetailId) {
        return invoiceSaleDetailRepository.findInvoiceDetailByInvoiceId(invoiceDetailId);
    }
}
