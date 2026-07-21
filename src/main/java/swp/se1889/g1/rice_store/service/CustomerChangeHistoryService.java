package swp.se1889.g1.rice_store.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swp.se1889.g1.rice_store.dto.CustomerChangeHistoryDTO;
import swp.se1889.g1.rice_store.entity.Customer;
import swp.se1889.g1.rice_store.entity.CustomerChangeHistory;
import swp.se1889.g1.rice_store.entity.Store;
import swp.se1889.g1.rice_store.entity.User;
import swp.se1889.g1.rice_store.repository.CustomerChangeHistoryRepository;
import swp.se1889.g1.rice_store.repository.StoreRepository;
import swp.se1889.g1.rice_store.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CustomerChangeHistoryService {

    private CustomerChangeHistoryRepository changeHistoryRepository;
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    public CustomerChangeHistoryService(CustomerChangeHistoryRepository changeHistoryRepository, CustomerService customerService) {
        this.changeHistoryRepository = changeHistoryRepository;
    }


    private String getAdditionalInfo() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetails) {
            String username = ((UserDetails) principal).getUsername();
            User user = userRepository.findByUsername(username);
            if (user.getRole().equals("ROLE_OWNER")) {
                return user.getUsername();
            } else {
                Optional<Store> store = storeRepository.findById(user.getCreatedBy());
                User user2 = userRepository.findByUsername(store.get().getCreatedBy());
                return user2.getUsername();
            }
        }
        return null;
    }

    @Transactional
    public void trackCustomerChanges(Customer originalCustomer, Customer updatedCustomer, User changedBy) {
        List<CustomerChangeHistory> changeHistories = new ArrayList<>();

        if (!compareValues(originalCustomer.getName(), updatedCustomer.getName())) {
            CustomerChangeHistory change = createChangeHistory(
                    originalCustomer, "Họ và Tên",
                    updatedCustomer.getName(),
                    originalCustomer.getName(), changedBy);
            change.setAdditionalInfo(getAdditionalInfo());
            changeHistories.add(change);
        }

        if (!compareValues(originalCustomer.getPhone(), updatedCustomer.getPhone())) {
            CustomerChangeHistory change = createChangeHistory(
                    originalCustomer, "Số điện thoại",
                    updatedCustomer.getPhone(),
                    originalCustomer.getPhone(), changedBy);
            change.setAdditionalInfo(getAdditionalInfo());
            changeHistories.add(change);
        }

        if (!compareValues(originalCustomer.getAddress(), updatedCustomer.getAddress())) {
            CustomerChangeHistory change = createChangeHistory(
                    originalCustomer, "Địa Chỉ",
                    updatedCustomer.getAddress(),
                    originalCustomer.getAddress(), changedBy);
            change.setAdditionalInfo(getAdditionalInfo());
            changeHistories.add(change);
        }

        if (!compareValues(originalCustomer.getEmail(), updatedCustomer.getEmail())) {
            CustomerChangeHistory change = createChangeHistory(
                    originalCustomer, "email",
                    updatedCustomer.getEmail(),
                    originalCustomer.getEmail(), changedBy);
            change.setAdditionalInfo(getAdditionalInfo());
            changeHistories.add(change);
        }

        changeHistoryRepository.saveAll(changeHistories);
    }

    private CustomerChangeHistory createChangeHistory(Customer customer, String field,
                                                      String oldValue, String newValue,
                                                      User changedBy) {
        return new CustomerChangeHistory(
                customer,
                field,
                oldValue != null ? oldValue : "N/A",
                newValue != null ? newValue : "N/A",
                changedBy
        );
    }

    private boolean compareValues(Object oldValue, Object newValue) {
        if (oldValue == null && newValue == null) return true;
        if (oldValue == null || newValue == null) return false;
        return oldValue.equals(newValue);
    }

    private String calculateDebtBalanceChangeDetails(BigDecimal oldBalance, BigDecimal newBalance) {
        BigDecimal changeDifference = newBalance.subtract(oldBalance);
        String changeType = changeDifference.compareTo(BigDecimal.ZERO) > 0 ? "Tăng" : "Giảm";

        return String.format(
                "Loại thay đổi: %s, Số tiền: %s, Số dư cũ: %s, Số dư mới: %s",
                changeType,
                changeDifference.abs(),
                oldBalance,
                newBalance
        );
    }

    public Page<CustomerChangeHistoryDTO> searchCustomerChanges(
            String customerName,
            String changedField,
            String oldValue,
            String changedBy,
            LocalDateTime startDate,
            LocalDateTime endDate,
            User addInfo,
            Pageable pageable
    ) {

        Page<CustomerChangeHistory> changePage = changeHistoryRepository.advancedSearchCustomerChangeHistory(
                customerName, changedField, oldValue, changedBy, startDate, endDate, addInfo.getUsername(), pageable
        );

        return changePage.map(change -> new CustomerChangeHistoryDTO(
                change.getId(),
                change.getCustomer().getId(),
                change.getCustomer().getName(),
                change.getChangedField(),
                change.getOldValue(),
                change.getNewValue(),
                change.getChangedBy().getUsername(),
                change.getChangedAt()
        ));
    }
}