package swp.se1889.g1.rice_store.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import swp.se1889.g1.rice_store.dto.CustomerDTO;
import swp.se1889.g1.rice_store.dto.CustomerInvoiceDTO;
import swp.se1889.g1.rice_store.entity.Customer;
import swp.se1889.g1.rice_store.entity.Store;
import swp.se1889.g1.rice_store.entity.User;
import swp.se1889.g1.rice_store.repository.CustomerRepository;
import swp.se1889.g1.rice_store.repository.UserRepository;
import swp.se1889.g1.rice_store.specification.CustomerSpecifications;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserRepository userRepository;


    private final CustomerChangeHistoryService changeHistoryService;

    public CustomerService(CustomerRepository customerRepository, @Lazy CustomerChangeHistoryService changeHistoryService) {
        this.customerRepository = customerRepository;
        this.changeHistoryService = changeHistoryService;
    }

    public CustomerDTO getCustomerById(Long id) {
        Optional<Customer> customerOpt = customerRepository.findById(id);
        if (customerOpt.isPresent()) {
            Customer customer = customerOpt.get();
            return new CustomerDTO(
                    customer.getId(),
                    customer.getName(),
                    customer.getPhone(),
                    customer.getAddress(),
                    customer.getEmail(),
                    customer.getDebtBalance(),
                    customer.getCreatedBy().getUsername(),
                    customer.getUpdatedBy()
            );
        }
        throw new RuntimeException("Không tìm thấy khách hàng có ID: " + id);
    }


    public void updateCustomer(CustomerDTO customerDTO) {
        Optional<Customer> customerOpt = customerRepository.findById(customerDTO.getId());
        if (customerOpt.isPresent()) {
            Customer customer = customerOpt.get();
            List<Customer> samePhone = customerRepository.findByPhone(customerDTO.getPhone());
            boolean phoneExists = samePhone.stream().anyMatch(c -> !c.getId().equals(customer.getId()));
            if (phoneExists) {
                throw new RuntimeException("Số điện thoại đã tồn tại, vui lòng nhập số khác.");
            }
            List<Customer> sameEmail = customerRepository.findByemail(customerDTO.getEmail());
            boolean emailExists = sameEmail.stream().anyMatch(c -> !c.getId().equals(customer.getId()));
            if (emailExists) {
                throw new RuntimeException("Email đã tồn tại, vui lòng nhập email khác.");
            }

            Customer updatedCustomer = new Customer();
            updatedCustomer.setId(customer.getId());
            updatedCustomer.setName(customer.getName());
            updatedCustomer.setPhone(customer.getPhone());
            updatedCustomer.setAddress(customer.getAddress());
            updatedCustomer.setEmail(customer.getEmail());
            updatedCustomer.setDebtBalance(customer.getDebtBalance());

            customer.setName(customerDTO.getName());
            customer.setPhone(customerDTO.getPhone());
            customer.setAddress(customerDTO.getAddress());
            customer.setEmail(customerDTO.getEmail());
            customer.setDebtBalance(customerDTO.getDebtBalance());
            customer.setUpdatedAt(LocalDateTime.now());

            User currentUser = getCurrentUser();
            if (currentUser != null) {
                customer.setUpdatedBy(currentUser.getUsername());
            }

            changeHistoryService.trackCustomerChanges(
                    customer,
                    updatedCustomer,
                    currentUser
            );

            customerRepository.save(customer);
        } else {
            throw new RuntimeException("Không tìm thấy khách hàng để cập nhật!");
        }
    }

    public void createCustomer(CustomerDTO customerDTO) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            throw new RuntimeException("Không thể xác định người dùng hiện tại.");
        }

        List<Customer> existingCustomers = customerRepository.findByPhone(customerDTO.getPhone());
        if (!existingCustomers.isEmpty()) {
            throw new RuntimeException("Số điện thoại đã tồn tại, vui lòng nhập số khác.");
        }
        List<Customer> existingCustom1 = customerRepository.findByemail(customerDTO.getEmail());
        if (!existingCustom1.isEmpty()) {
            throw new RuntimeException("Email đã tồn tại, vui lòng nhập email khác.");
        }

        Customer customer = new Customer();
        customer.setName(customerDTO.getName());
        customer.setPhone(customerDTO.getPhone());
        customer.setAddress(customerDTO.getAddress());
        customer.setEmail(customerDTO.getEmail());
        customer.setDebtBalance(customerDTO.getDebtBalance());
        customer.setCreatedBy(currentUser);
        customer.setCreatedAt(LocalDateTime.now());
        customer.setUpdatedAt(LocalDateTime.now());

//        customer.setUpdatedBy(currentUser.getUsername());
        customerRepository.save(customer);
    }

    public User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetails) {
            String username = ((UserDetails) principal).getUsername();
            return userRepository.findByUsername(username);
        }
        return null;
    }

    public Page<CustomerDTO> filterCustomersWithSpec(
            String name, String phone,
            String address, String email, String debt,
            LocalDate createdDate, LocalDate updatedDate,
            int page, int size) {

        User currentUser = getCurrentUser();
        if (currentUser == null) return Page.empty();

        BigDecimal parsedDebt = null;

        try {

            if (debt != null && !debt.isBlank()) parsedDebt = new BigDecimal(debt);
        } catch (NumberFormatException e) {
            throw new RuntimeException("ID hoặc dư nợ không hợp lệ.");
        }

        LocalDateTime createdFrom = createdDate != null ? createdDate.atStartOfDay() : null;
        LocalDateTime createdTo = createdDate != null ? createdDate.plusDays(1).atStartOfDay() : null;

        LocalDateTime updatedFrom = updatedDate != null ? updatedDate.atStartOfDay() : null;
        LocalDateTime updatedTo = updatedDate != null ? updatedDate.plusDays(1).atStartOfDay() : null;

        Specification<Customer> spec = Specification.where(CustomerSpecifications.notDeleted());

        // Lấy danh sách userId được phép truy cập theo Owner
        Long ownerId = currentUser.getRole().equals("ROLE_OWNER")
                ? currentUser.getId()
                : currentUser.getCreatedBy();

        if (ownerId == null) return Page.empty();

        List<Long> allowedCreatedByIds = userRepository.findAll().stream()
                .filter(u -> u.getId() == ownerId || u.getCreatedBy() == ownerId)
                .map(User::getId)
                .toList();

        spec = spec.and(CustomerSpecifications.createdByIn(allowedCreatedByIds));

        if (name != null && !name.isBlank()) spec = spec.and(CustomerSpecifications.nameContains(name));
        if (phone != null && !phone.isBlank()) spec = spec.and(CustomerSpecifications.phoneContains(phone));
        if (address != null && !address.isBlank()) spec = spec.and(CustomerSpecifications.addressContains(address));
        if (email != null && !email.isBlank()) spec = spec.and(CustomerSpecifications.emailContains(email));
        if (parsedDebt != null) spec = spec.and(CustomerSpecifications.debtEquals(parsedDebt));
        if (createdFrom != null && createdTo != null)
            spec = spec.and(CustomerSpecifications.createdAtBetween(createdFrom, createdTo));
        if (updatedFrom != null && updatedTo != null)
            spec = spec.and(CustomerSpecifications.updatedAtBetween(updatedFrom, updatedTo));

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return customerRepository.findAll(spec, pageable).map(CustomerDTO::new);
    }


    public List<CustomerDTO> searchCustomers(String query) {
        return customerRepository.searchCustomerDetails(query);
    }

    public Customer findCustomerById(Long id) {
        return customerRepository.findById(id).get();
    }

    public Customer saveCustomer(Customer customer) {
        return customerRepository.save(customer);
    }

    public Customer createCustomerInvoice(CustomerInvoiceDTO customerInvoiceDTO, RedirectAttributes redirectAttributes) {
        boolean hasError = false;

        if (customerRepository.findCustomerByEmail(customerInvoiceDTO.getCustomerInvoiceEmail()) != null) {
            redirectAttributes.addFlashAttribute("error", "Email khách hàng đã tồn tại");
            hasError = true;
        }

        if (customerRepository.findCustomerByPhone(customerInvoiceDTO.getCustomerInvoicePhone()) != null) {
            redirectAttributes.addFlashAttribute("error", "Số điện thoại khách hàng đã tồn tại");
            hasError = true;
        }

        if (hasError) {
            return null;
        }

        Customer customer = new Customer();
        customer.setName(customerInvoiceDTO.getCustomerInvoiceName());
        customer.setPhone(customerInvoiceDTO.getCustomerInvoicePhone());
        customer.setAddress(customerInvoiceDTO.getCustomerInvoiceAddress());
        customer.setEmail(customerInvoiceDTO.getCustomerInvoiceEmail());
        User currentUser = getCurrentUser();
        customer.setCreatedBy(currentUser);

        try {
            customerRepository.save(customer);
        } catch (Exception e) {
            throw new RuntimeException("Error while saving store: " + e.getMessage());
        }
        return customer;
    }
}
