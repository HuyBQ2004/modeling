package swp.se1889.g1.rice_store.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swp.se1889.g1.rice_store.entity.Customer;
import swp.se1889.g1.rice_store.entity.DebtRecords;
import swp.se1889.g1.rice_store.entity.User;
import swp.se1889.g1.rice_store.repository.CustomerRepository;
import swp.se1889.g1.rice_store.repository.DebtRecordRepository;
import swp.se1889.g1.rice_store.repository.UserRepository;
import swp.se1889.g1.rice_store.specification.DebtRecordsSpecifications;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class DebtRecordService {

    //    @Autowired
//    private DebtRecordRepository debtRecordRepository;
    @Autowired
    private DebtRecordRepository debtRecordRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private UserRepository userRepository;
    private CustomerService customerService;
    private final CustomerChangeHistoryService changeHistoryService;

    public DebtRecordService(CustomerChangeHistoryService changeHistoryService) {
        this.changeHistoryService = changeHistoryService;
    }


    //public Page<DebtRecords> getPage(Long customerId, int page, int size){
//        return debtRecordRepository.findDebtRecordsByPage(customerId , page, size);
//}
    public DebtRecords addDebt(DebtRecords debtRecord, boolean skipUpdate) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            throw new RuntimeException("Không tìm thấy thông tin người dùng");
        }
        debtRecord.setNote(debtRecord.getNote());
        debtRecord.setCreatedBy(currentUser);
        debtRecord.setType(debtRecord.getType());
        debtRecord.setCreateOn(debtRecord.getCreateOn());
        debtRecord.setCreatedAt(LocalDateTime.now());
        debtRecord.setUpdatedAt(LocalDateTime.now());
        debtRecordRepository.save(debtRecord);

        // Nếu không skipUpdate, cập nhật số dư nợ dựa trên toàn bộ lịch sử record
        if (!skipUpdate) {
            Optional<Customer> customerOpt = customerRepository.findById(debtRecord.getCustomerId());
            if (customerOpt.isPresent()) {
                Customer customer = customerOpt.get();
                updateDebtBalances(customer, currentUser);
            } else {
                throw new RuntimeException("Không tìm thấy khách hàng để cập nhật số dư nợ!");
            }
        }
        return debtRecord;
    }

    // Overload method để giữ tương thích nếu không sử dụng flag
//    public DebtRecords addDebt(DebtRecords debtRecord) {
//        return addDebt(debtRecord, false);
//    }
    public Page<DebtRecords> getFilteredDebtRecords(Long customerId, Pageable pageable, Long idMin, Long idMax,
                                                    String note, String type, BigDecimal amountMin, BigDecimal amountMax,
                                                    Date dateMin, Date dateMax, Date dateMin2, Date dateMax2) {
        Specification<DebtRecords> spec = Specification.where(DebtRecordsSpecifications.hasCustomerId(customerId));
        if (idMin != null) {
            spec = spec.and(DebtRecordsSpecifications.idGreaterThanOrEqual(idMin));
        }
        if (idMax != null) {
            spec = spec.and(DebtRecordsSpecifications.idLessThanOrEqual(idMax));
        }
        if (note != null && !note.isEmpty()) {
            spec = spec.and(DebtRecordsSpecifications.noteContains(note));
        }
        if (type != null && !type.isEmpty()) {
            spec = spec.and(DebtRecordsSpecifications.hasType(type));
        }
        if (amountMin != null) {
            spec = spec.and(DebtRecordsSpecifications.amountGreaterThanOrEqual(amountMin));
        }
        if (amountMax != null) {
            spec = spec.and(DebtRecordsSpecifications.amountLessThanOrEqual(amountMax));
        }
        if (dateMin != null) {
            spec = spec.and(DebtRecordsSpecifications.createdAtAfter(dateMin));
        }
        if (dateMax != null) {
            spec = spec.and(DebtRecordsSpecifications.createdAtBefore(dateMax));
        }
        if (dateMin2 != null) {
            spec = spec.and(DebtRecordsSpecifications.hasCreateOn(dateMin2));
        }
        if (dateMax2 != null) {
            spec = spec.and(DebtRecordsSpecifications.hasCreateOn2(dateMax2));
        }
        return debtRecordRepository.findAll(spec, pageable);
    }

    // Phương thức lấy danh sách chi tiết nợ theo customer id
    public List<DebtRecords> getDebtDetailsByCustomerId(Long customerId) {
        return debtRecordRepository.findByCustomerId(customerId);
    }

    public Page<DebtRecords> getCustomerPage(Long customerId, Pageable pageable) {
        return debtRecordRepository.findByCustomerId(customerId, pageable);
    }

    @Transactional
    public void updateDebtBalances(Customer customer, User currentUser) {
        // Lấy tất cả các bản ghi nợ của khách hàng, không nhất thiết đã được sắp xếp
        List<DebtRecords> debtRecords = debtRecordRepository.findByCustomerId(customer.getId());

        // Sử dụng PriorityQueue để sắp xếp các bản ghi theo thời gian tạo (createdAt)
        PriorityQueue<DebtRecords> queue = new PriorityQueue<>(
                Comparator.comparing(DebtRecords::getCreatedAt)
                        .thenComparing(DebtRecords::getId) // Xử lý trùng thời gian
        );
        queue.addAll(debtRecords);

        BigDecimal debtBalance = BigDecimal.ZERO;

        // Xử lý từng bản ghi theo thứ tự
        while (!queue.isEmpty()) {
            DebtRecords record = queue.poll();
            switch (record.getType()) {
                case Shop_debt_customer:
                    debtBalance = debtBalance.add(record.getAmount());
                    break;
                case Customer_debt_shop:
                    debtBalance = debtBalance.subtract(record.getAmount());
                    break;
                case Shop_return_customer:
                    debtBalance = debtBalance.subtract(record.getAmount());
                    break;
                case Customer_return_shop:
                    debtBalance = debtBalance.add(record.getAmount());
                    break;
            }
        }

        // Cập nhật số dư nợ cho khách hàng và lưu lại vào cơ sở dữ liệu
        customer.setDebtBalance(debtBalance);
        customerRepository.save(customer);

    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetails) {
            String username = ((UserDetails) principal).getUsername();
            return userRepository.findByUsername(username);
        }
        return null;
    }

}

