package swp.se1889.g1.rice_store.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import swp.se1889.g1.rice_store.dto.InvoiceDetailDTO;
import swp.se1889.g1.rice_store.dto.InvoicesDTO;
import swp.se1889.g1.rice_store.entity.*;
import swp.se1889.g1.rice_store.repository.*;
import swp.se1889.g1.rice_store.specification.InvoiceSpecifications;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InvoicesService {
    @Autowired
    private InvoicesRepository invoiceRepository;
    @Autowired
    private InvoiceDetailRepository invoiceDetailsRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private StoreRepository storeRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ZoneRepository zoneRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DebtRecordRepository debtRecordsRepository;
    @Autowired
    private DebtRecordService debtRecordService;


    // Hàm tìm kiếm hóa đơn theo ID cửa hàng
    public List<Invoices> findByStore(Long storeId) {
        if (storeId == null) {
            throw new IllegalArgumentException("Store ID cannot be null");
        }
        return invoiceRepository.findByStoreId(storeId);
    }

    public Page<Invoices> getPage(Store store, Pageable pageable) {
        return invoiceRepository.findInvoicesByStore(store, pageable);
    }

    public InvoiceDetailDTO getInvoice(Long id) {
        Optional<InvoicesDetails> invoice = invoiceDetailsRepository.findById(id);
        if (invoice.isPresent()) {
            InvoicesDetails detail = invoice.get();
            detail.getId();
        }
        throw new RuntimeException("Không tìm thấy hóa đơn có ID: " + id);
    }


    @Transactional
    public Invoices createImportInvoice(InvoicesDTO dto, Store store) {
        // 1. Kiểm tra và lấy thông tin khách hàng
        User currentUser = getCurrentUser();
        if (dto.getCustomerPhone() == null) {
            throw new IllegalArgumentException("Số điện thoại khách hàng không được để trống");
        }
        Customer customer = customerRepository.findCustomerByPhone(dto.getCustomerPhone());
        if (customer == null) {
            throw new RuntimeException("Không tìm thấy khách hàng với số điện thoại: " + dto.getCustomerPhone());
        }
        // 2. Kiểm tra và lấy thông tin cửa hàng
        if (dto.getStoreId() == null) {
            throw new IllegalArgumentException("ID cửa hàng không được để trống");
        }
        // Lấy thông tin cửa hàng từ storeId
        Store storeFromDb = storeRepository.findById(dto.getStoreId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy cửa hàng với ID: " + dto.getStoreId()));
        // 3. Tính toán tổng giá trị hóa đơn
        BigDecimal totalPrice = calculateTotalPrice(dto.getDetails());
        BigDecimal finalAmount = totalPrice;
        // 4. Lấy thông tin về phương thức thanh toán và số tiền đã trả
        String paymentMethod = dto.getPaymentMethod();
        BigDecimal paidAmount = dto.getPaidAmount() != null ? dto.getPaidAmount() : BigDecimal.ZERO;
        // 5. Lấy nợ hiện tại của khách hàng
        BigDecimal debtBalance = customer.getDebtBalance() != null ? customer.getDebtBalance() : BigDecimal.ZERO;
        // 6. Tính toán nợ mới dựa trên phương thức thanh toán
        BigDecimal newDebtBalance;
        BigDecimal debtChange;
        DebtRecords.DebtType debtType = null;
        if ("onlyProduct".equals(paymentMethod)) {
            // Chỉ thanh toán tiền hàng
            if (paidAmount.compareTo(finalAmount) < 0) {
                // Trả thiếu: Tôi nợ nhà cung cấp
                newDebtBalance = debtBalance.add(finalAmount.subtract(paidAmount));
                debtChange = finalAmount.subtract(paidAmount);
                debtType = DebtRecords.DebtType.Shop_debt_customer;
            } else if (paidAmount.compareTo(finalAmount) > 0) {
                // Trả thừa: Nhà cung cấp nợ tôi
                newDebtBalance = debtBalance.subtract(paidAmount.subtract(finalAmount));
                debtChange = paidAmount.subtract(finalAmount);
                debtType = DebtRecords.DebtType.Customer_debt_shop;
            } else {
                // Trả đủ: Không thay đổi nợ
                newDebtBalance = debtBalance;
                debtChange = BigDecimal.ZERO;
            }
        } else if ("productAndDebt".equals(paymentMethod)) {
            // Thanh toán tiền hàng + nợ
            // Trong phần xử lý "productAndDebt":
            BigDecimal totalDue = finalAmount.add(debtBalance);
            if (paidAmount.compareTo(totalDue) < 0) {
                // Cửa hàng còn nợ khách (Shop_debt_customer)
                newDebtBalance = totalDue.subtract(paidAmount);
                debtChange = totalDue.subtract(paidAmount); // Số tiền tăng vào nợ
                debtType = DebtRecords.DebtType.Shop_return_customer;
            } else if (paidAmount.compareTo(totalDue) > 0) {
                // Khách nợ cửa hàng (Customer_debt_shop)
                newDebtBalance = paidAmount.subtract(totalDue);
                debtChange = paidAmount.subtract(totalDue); // Số tiền khách nợ cửa hàng
                debtType = DebtRecords.DebtType.Customer_debt_shop;
            } else {
                newDebtBalance = BigDecimal.ZERO;
                debtChange = debtBalance.negate(); // 288.000 nếu debtBalance = -288.000
                if (debtChange.compareTo(BigDecimal.ZERO) > 0) {
                    debtType = DebtRecords.DebtType.Shop_debt_customer; // Tăng debtBalance
                } else if (debtChange.compareTo(BigDecimal.ZERO) < 0) {
                    debtType = DebtRecords.DebtType.Customer_debt_shop; // Giảm debtBalance
                    debtChange = debtChange.negate(); // Đảm bảo amount dương
                } else {
                    debtType = null; // Không cần bản ghi nếu debtBalance đã là 0
                }
            }
        } else {
            throw new RuntimeException("Phương thức thanh toán không hợp lệ");
        }
        // 7. Cập nhật nợ mới cho khách hàng

        // 4. Tạo hóa đơn
        Invoices invoice = new Invoices();
        invoice.setStore(storeFromDb);  // Lưu trữ cửa hàng đã xác thực
        invoice.setCustomer(customer);
        invoice.setTotalPrice(totalPrice);
        invoice.setFinalAmount(finalAmount);
        invoice.setNote(dto.getNote());
        invoice.setQuantity(invoice.getQuantity());
        invoice.setType(Invoices.InvoiceType.Purchase);
        invoice.setStatus("Paid");
        invoice.setIsDeleted(false);
        invoice.setCreatedBy(currentUser);

        Invoices savedInvoice = invoiceRepository.save(invoice);
        // Trong phần tạo DebtRecords:
        if (debtType != null && debtChange.compareTo(BigDecimal.ZERO) != 0) {
            DebtRecords debtRecord = new DebtRecords();
            debtRecord.setCustomerId(customer.getId());
            debtRecord.setCreateOn(LocalDateTime.now());
            debtRecord.setUpdatedAt(LocalDateTime.now());
            debtRecord.setCreatedBy(currentUser);
            debtRecord.setType(debtType);
            debtRecord.setAmount(debtChange); // Luôn lấy giá trị dương
            debtRecord.setNote("Giao dịch từ hóa đơn nhập hàng #" + savedInvoice.getId());
            debtRecordService.addDebt(debtRecord, true);
            customer.setDebtBalance(newDebtBalance);
            customerRepository.save(customer);
        }


        // 5. Tạo chi tiết hóa đơn và cập nhật kho (zone)
        updateInventory(dto.getDetails(), savedInvoice, currentUser);
        return savedInvoice;
    }

    private void updateInventory(List<InvoiceDetailDTO> details, Invoices savedInvoice, User currentUser) {
        for (InvoiceDetailDTO dto : details) {
            if (dto.getProductId() == null || dto.getZoneId() == null) {
                throw new IllegalArgumentException("Product ID và Zone ID không được để trống");
            }

            Product newProduct = productRepository.findById(dto.getProductId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm với ID: " + dto.getProductId()));

            Zone zone = zoneRepository.findByIdAndIsDeletedFalse(dto.getZoneId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy khu vực với ID: " + dto.getZoneId() + " hoặc đã bị xóa!"));

            // Nếu khu vực đã có sản phẩm khác, thì thay thế sản phẩm mới và cập nhật số lượng mới
            if (zone.getProduct() == null || !zone.getProduct().getId().equals(newProduct.getId())) {
                zone.setProduct(newProduct);
                zone.setQuantity(dto.getQuantity()); // Ghi đè số lượng mới
            } else {
                // Nếu sản phẩm trùng với sản phẩm đang có trong kho, thì cộng dồn số lượng
                zone.setQuantity(zone.getQuantity() + dto.getQuantity());
            }

            zoneRepository.saveAndFlush(zone); // Lưu cập nhật vào DB

            // Lưu chi tiết hóa đơn
            InvoicesDetails detail = new InvoicesDetails();
            detail.setInvoice(savedInvoice);
            detail.setProduct(newProduct);
            detail.setZone(zone);
            detail.setCustomer(savedInvoice.getCustomer());
            detail.setQuantity(dto.getQuantity());
            detail.setUnitPrice(dto.getUnitPrice());
            detail.setTotalPrice(dto.getUnitPrice().multiply(BigDecimal.valueOf(dto.getQuantity())));
            detail.setCreatedBy(currentUser);
            invoiceDetailsRepository.save(detail);
        }
    }


    private BigDecimal calculateTotalPrice(List<InvoiceDetailDTO> details) {
        return details.stream()
                .map(d -> d.getUnitPrice().multiply(BigDecimal.valueOf(d.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Lấy thông tin người dùng hiện tại
    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetails) {
            String username = ((UserDetails) principal).getUsername();
            return userRepository.findByUsername(username);
        }
        return null;
    }


    public Invoices update(Long id, String newStatus) {
        Invoices invoices = invoiceRepository.findById(id).orElse(null);
        if (invoices == null) {
            throw new RuntimeException("Không tìm thấy hóa đơn với ID: " + id);
        }
        // Chuyển đổi trạng thái thành giá trị hợp lệ
        if ("Paid".equalsIgnoreCase(newStatus)) {
            newStatus = "Paid";  // Hoặc giá trị hợp lệ trong database
        } else if ("Unpaid".equalsIgnoreCase(newStatus)) {
            newStatus = "Unpaid";
        } else {
            throw new IllegalArgumentException("Trạng thái không hợp lệ: " + newStatus);
        }
        invoices.setStatus(newStatus);
        return invoiceRepository.save(invoices);
    }

    public Page<Invoices> getFilter(Store store, Long idMin, Long idMax, String note, String status, Date dateMin, Date dateMax, Pageable pageable,
                                    Date dateMin1, Date dateMax1, BigDecimal amountMin, BigDecimal amountMax, Invoices.InvoiceType type) {
        Specification<Invoices> spec = Specification.where(null);
        if (store != null) {
            spec = spec.and(InvoiceSpecifications.hasStore(store));
        }
        if (type != null) {
            spec = spec.and(InvoiceSpecifications.hasType(type));
        }
        if (idMin != null) {
            spec = spec.and(InvoiceSpecifications.idGreatThan(idMin));
        }
        if (idMax != null) {
            spec = spec.and(InvoiceSpecifications.idLessThan(idMax));
        }
        if (note != null && !note.isEmpty()) {
            spec = spec.and(InvoiceSpecifications.noteContains(note));
        }
        if (status != null && !status.isEmpty()) {
            spec = spec.and(InvoiceSpecifications.hasStatus(status));
        }
        if (amountMin != null) {
            spec = spec.and(InvoiceSpecifications.amountGreaterThanOrEqual(amountMin));
        }
        if (amountMax != null) {
            spec = spec.and(InvoiceSpecifications.amountLessThanOrEqual(amountMax));
        }
        if (dateMin != null) {
            spec = spec.and(InvoiceSpecifications.createdAtAfter(dateMin));
        }
        if (dateMax != null) {
            spec = spec.and(InvoiceSpecifications.createdAtBefore(dateMax));
        }
        if (dateMin1 != null) {
            spec = spec.and(InvoiceSpecifications.updatedAtAfter(dateMin1));
        }
        if (dateMax1 != null) {
            spec = spec.and(InvoiceSpecifications.updatedAtBefore(dateMax1));
        }
        return invoiceRepository.findAll(spec, pageable);
    }

    // --- A. Tổng hóa đơn hôm nay ---
    public long getTodayInvoiceCount(Long storeId) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return invoiceRepository.countTodayInvoices(storeId, start, end);
    }


    // --- B. Tổng doanh thu hôm nay ---
    public BigDecimal getTodayRevenue(Long storeId) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return invoiceRepository.sumTodayRevenue(storeId, start, end);
    }


    // --- C. Doanh thu theo thứ trong tuần hiện tại ---
    public Map<String, BigDecimal> getWeeklyRevenue(Long storeId) {
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.with(DayOfWeek.MONDAY);
        LocalDate endOfWeek = today.with(DayOfWeek.SUNDAY);

        List<Object[]> results = invoiceRepository.getRevenueByWeekday(
                storeId,
                startOfWeek.atStartOfDay(),
                endOfWeek.plusDays(1).atStartOfDay()
        );

        Map<String, BigDecimal> map = new LinkedHashMap<>();
        String[] weekdays = {"Chủ nhật", "Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7"};
        for (int i = 0; i < 7; i++) map.put(weekdays[i], BigDecimal.ZERO);
        for (Object[] row : results) {
            Integer dayOfWeek = (Integer) row[0];
            BigDecimal revenue = (BigDecimal) row[1];
            map.put(weekdays[dayOfWeek - 1], revenue);
        }
        return map;
    }


    // --- D. Doanh thu theo các tháng trong năm hiện tại ---
    public Map<String, BigDecimal> getMonthlyRevenue(Long storeId) {
        int year = LocalDate.now().getYear();
        List<Object[]> results = invoiceRepository.getRevenueByMonth(storeId, year);

        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (int i = 1; i <= 12; i++) {
            map.put("Tháng " + i, BigDecimal.ZERO);
        }
        for (Object[] row : results) {
            Integer month = (Integer) row[0];
            BigDecimal revenue = (BigDecimal) row[1];
            map.put("Tháng " + month, revenue);
        }
        return map;
    }


}
