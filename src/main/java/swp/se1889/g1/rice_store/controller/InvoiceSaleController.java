package swp.se1889.g1.rice_store.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import swp.se1889.g1.rice_store.dto.CustomerDTO;
import swp.se1889.g1.rice_store.dto.CustomerInvoiceDTO;
import swp.se1889.g1.rice_store.dto.InvoiceSaleDetailDTO;
import swp.se1889.g1.rice_store.dto.StoreDTO;
import swp.se1889.g1.rice_store.entity.*;
import swp.se1889.g1.rice_store.repository.DebtRecordRepository;
import swp.se1889.g1.rice_store.repository.InvoiceSaleDetailRepository;
import swp.se1889.g1.rice_store.repository.InvoiceSaleRepository;
import swp.se1889.g1.rice_store.repository.ZoneRepository;
import swp.se1889.g1.rice_store.service.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Controller
public class InvoiceSaleController {

    private final InvoiceSaleDetailService invoiceSaleDetailService;

    private final InvoiceSaleRepository invoiceSaleRepository;

    private final InvoiceSaleService invoiceSaleService;

    private final UserServiceIpml userService;

    private final CustomerService customerService;

    private final DebtRecordRepository debtRecordRepository;

    private final DebtRecordService debtRecordService;

    private final ProductService productService;

    private final InvoiceSaleDetailRepository invoiceSaleDetailRepository;

    private final ZoneService zoneService;
    private final ZoneRepository zoneRepository;

    public InvoiceSaleController(InvoiceSaleRepository invoiceSaleRepository, InvoiceSaleService invoiceSaleService, UserServiceIpml userService, CustomerService customerService, DebtRecordRepository debtRecordRepository, ProductService productService, InvoiceSaleDetailRepository invoiceSaleDetailRepository, InvoiceSaleDetailService invoiceSaleDetailService, ZoneService zoneService, ZoneRepository zoneRepository, DebtRecordService debtRecordService) {
        this.invoiceSaleRepository = invoiceSaleRepository;
        this.invoiceSaleService = invoiceSaleService;
        this.userService = userService;
        this.customerService = customerService;
        this.debtRecordRepository = debtRecordRepository;
        this.productService = productService;
        this.invoiceSaleDetailRepository = invoiceSaleDetailRepository;
        this.invoiceSaleDetailService = invoiceSaleDetailService;
        this.zoneService = zoneService;
        this.zoneRepository = zoneRepository;
        this.debtRecordService = debtRecordService;
    }

    @GetMapping("invoiceSale")
    public String getinvoiceSales(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "5") int size,
                                  Model model,
                                  HttpSession session) {
        Store store = (Store) session.getAttribute("store");
        model.addAttribute("store", store);

        Page<Invoice> invoicePage = invoiceSaleService.findInvoicesByStoreId(store.getId(), page, size);

        for (Invoice invoice : invoicePage.getContent()) {
            CustomerDTO customer = customerService.getCustomerById(invoice.getCustomerId());
            User user = userService.getUserById(invoice.getCreatedBy());
            invoice.setCustomer(customer);
            invoice.setUser(user);
        }

        User user = userService.getCurrentUser();
        model.addAttribute("user", user);

        model.addAttribute("invoiceSales", invoicePage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", invoicePage.getTotalPages());
        model.addAttribute("totalItems", invoicePage.getTotalElements());
        model.addAttribute("recordsPerPage", size);

        return "invoiceSale";
    }

    @PostMapping("order")
    public String createOrder(@RequestParam("customerId") Long customerId,
                              @RequestParam("totalAmount") BigDecimal totalAmount,
                              @RequestParam("selectedProducts") String selectedProducts,
                              @RequestParam("paymentOption") String paymentOption,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) throws JsonProcessingException {
        if (customerId == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng chọn khách hàng!");
            return "redirect:/createOrder";
        }
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng chọn sản phẩm!");
            return "redirect:/createOrder";
        }

        Store store = (Store) session.getAttribute("store");
        User user = userService.getCurrentUser();

        Invoice invoice = new Invoice();
        invoice.setStoreId(store.getId());
        invoice.setCustomerId(customerId);
        invoice.setFinalAmount(totalAmount);

        invoice.setPaymentStatus(paymentOption);
        Customer customer = customerService.findCustomerById(invoice.getCustomerId());
        if (paymentOption.equals("In_debt")) {
            DebtRecords debtRecords = new DebtRecords();
            debtRecords.setCustomerId(customer.getId());
            debtRecords.setType(DebtRecords.DebtType.valueOf("Customer_debt_shop"));
            debtRecords.setAmount(totalAmount);
            debtRecords.setCreatedBy(user);
            debtRecordRepository.save(debtRecords);
            debtRecordService.updateDebtBalances(customer, user);
        }

        invoice.setType("Sale");
        invoice.setCreatedBy(user.getId());
        invoiceSaleRepository.save(invoice);

        ObjectMapper objectMapper = new ObjectMapper();
        List<InvoiceSaleDetailDTO> productOrders = objectMapper.readValue(
                selectedProducts, new TypeReference<List<InvoiceSaleDetailDTO>>() {
                });

        for (InvoiceSaleDetailDTO product : productOrders) {
            InvoiceDetail invoiceDetail = new InvoiceDetail();
            invoiceDetail.setInvoiceId(invoice.getId());
            invoiceDetail.setProductId(product.getProductId());
            invoiceDetail.setQuantity(product.getQuantity());

            Zone zone = zoneService.getZoneById(product.getZoneId());
            zone.setQuantity(zone.getQuantity() - product.getQuantity());
            zoneRepository.save(zone);

            Product selectProduct = productService.findProductById(product.getProductId());
            invoiceDetail.setUnitPrice(selectProduct.getPrice());
            BigDecimal totalPrice = selectProduct.getPrice().multiply(BigDecimal.valueOf(product.getQuantity()));
            invoiceDetail.setTotalPrice(totalPrice);

            invoiceDetail.setZoneId(product.getZoneId());
            invoiceDetail.setCustomerId(customerId);

            invoiceSaleDetailRepository.save(invoiceDetail);
        }

        return "redirect:/invoiceSale";
    }

    @GetMapping("/invoiceDetail/{invoiceId}")
    public String invoiceDetail(@PathVariable Long invoiceId,
                                Model model, HttpSession session) {

        List<InvoiceDetail> invoiceDetail = invoiceSaleDetailService.findByInvoiceDetailId(invoiceId);
        model.addAttribute("invoiceDetail", invoiceDetail);

        Customer customer = customerService.findCustomerById(invoiceDetail.get(0).getCustomerId());
        model.addAttribute("customer", customer);

        Invoice invoice = invoiceSaleService.findById(invoiceDetail.get(0).getInvoiceId());
        model.addAttribute("invoice", invoice);

        List<String> productNameList = new ArrayList<>();
        List<String> zoneNameList = new ArrayList<>();

        for (InvoiceDetail iD : invoiceDetail) {
            Product product = productService.findProductById(iD.getProductId());
            Zone zone = zoneService.getZoneById(iD.getZoneId());

            productNameList.add(product.getName());
            zoneNameList.add(zone.getName());
        }

        model.addAttribute("productNameList", productNameList);
        model.addAttribute("zoneNameList", zoneNameList);

        User user = userService.getCurrentUser();
        model.addAttribute("user", user);

        Store store = (Store) session.getAttribute("store");
        model.addAttribute("store", store);

        return "invoiceDetail";
    }

    @PostMapping("/updateInvoiceStatus")
    public String updatePaymentStatus(@RequestParam Long id,
                                      @RequestParam String paymentStatus,
                                      RedirectAttributes redirectAttributes) {
        Invoice invoice = invoiceSaleService.findById(id);

        if (invoice != null) {
            Customer customer = customerService.findCustomerById(invoice.getCustomerId());
            BigDecimal totalAmount = invoice.getFinalAmount();
            String currentStatus = invoice.getPaymentStatus();
            User user = userService.getCurrentUser();

            if (currentStatus.equals("Unpaid") || currentStatus.equals("Paid")) {
                if (paymentStatus.equals("In_debt")) {
                    DebtRecords debtRecords = new DebtRecords();
                    debtRecords.setCustomerId(customer.getId());
                    debtRecords.setType(DebtRecords.DebtType.valueOf("GHI_NO"));
                    debtRecords.setAmount(totalAmount);
                    debtRecords.setCreatedBy(user);
                    debtRecordRepository.save(debtRecords);

                }
            } else if (currentStatus.equals("In_debt")) {
                if (paymentStatus.equals("Unpaid") || paymentStatus.equals("Paid")) {
                    DebtRecords debtRecords = new DebtRecords();
                    debtRecords.setCustomerId(customer.getId());
                    debtRecords.setType(DebtRecords.DebtType.valueOf("TRA_NO"));
                    debtRecords.setAmount(totalAmount);
                    debtRecords.setCreatedBy(user);
                    debtRecordRepository.save(debtRecords);
                }
            }

            invoice.setPaymentStatus(paymentStatus);
            invoiceSaleRepository.save(invoice);
            customerService.saveCustomer(customer);

            redirectAttributes.addFlashAttribute("success", "Cập nhật trạng thái hóa đơn thành công");
        } else {
            redirectAttributes.addFlashAttribute("error", "Có lỗi xảy ra vui lòng thử lại!");
        }

        return "redirect:/invoiceSale";
    }

    @PostMapping("/customerInvoice")
    public String createCustomer(@Valid CustomerInvoiceDTO customerInvoiceDTO,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            for (FieldError fieldError : bindingResult.getFieldErrors()) {
                redirectAttributes.addFlashAttribute("error", fieldError.getDefaultMessage());
            }
            redirectAttributes.addFlashAttribute("showCustomerForm", true);
            return "redirect:/createOrder";
        }

        try {
            Customer customer = customerService.createCustomerInvoice(customerInvoiceDTO, redirectAttributes);
            if (customer == null) {
                redirectAttributes.addFlashAttribute("showCustomerForm", true);
                return "redirect:/createOrder";
            }
            redirectAttributes.addFlashAttribute("customer", customer);
            redirectAttributes.addFlashAttribute("success", "Thêm khách hàng thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Có lỗi xảy ra, vui lòng thử lại!");
        }
        return "redirect:/createOrder";
    }

}
