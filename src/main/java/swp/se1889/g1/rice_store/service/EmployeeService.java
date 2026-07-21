package swp.se1889.g1.rice_store.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import swp.se1889.g1.rice_store.dto.EmployeeDTO;
import swp.se1889.g1.rice_store.entity.User;
import swp.se1889.g1.rice_store.repository.EmployeeRepository;
import swp.se1889.g1.rice_store.service.Iservice.UserService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Service
public class EmployeeService {
    @Autowired
    private UserService userService;

    @Autowired
    private EmployeeRepository employeeRepository;

    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    //lấy danh sách nhân viên
    public List<User> getEmployees(Long storeId, String role) {
        return employeeRepository.findByCreatedByAndRole(storeId, role);
    }

    public List<User> getEmployeesActive(Long storeId, String role, boolean isDeleted) {
        return employeeRepository.findAllByCreatedByAndRoleAndIsDeleted(storeId, role, isDeleted);
    }

    // New pagination methods
    public Page<User> getEmployeesPage(Long storeId, String role, Pageable pageable) {
        return employeeRepository.findByCreatedByAndRole(storeId, role, pageable);
    }

    public Page<User> getEmployeesActivePage(Long storeId, String role, boolean isDeleted, Pageable pageable) {
        return employeeRepository.findByCreatedByAndRoleAndIsDeleted(storeId, role, isDeleted, pageable);
    }

    //thêm tài khoản và thông tin employee
    public User addNewEmployee(Long storeId, EmployeeDTO employeeDTO, RedirectAttributes redirectAttributes) {
        if (employeeRepository.findByUsername(employeeDTO.getUserName()).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Tên đăng nhập đã tồn tại");
            return null;
        }
        if (employeeRepository.findByEmail(employeeDTO.getEmail()).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Email đã tồn tại");
            return null;
        }
        if (employeeRepository.findByPhone(employeeDTO.getPhone()).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "SĐT đã tồn tại");
            return null;
        }

        User user = new User();
        user.setUsername(employeeDTO.getUserName());
        user.setPassword(passwordEncoder.encode(employeeDTO.getPassword()));
        user.setEmail(employeeDTO.getEmail());
        user.setRole("ROLE_EMPLOYEE");
        user.setName(employeeDTO.getName());
        user.setAddress(employeeDTO.getAddress());
        user.setPhone(employeeDTO.getPhone());
        user.setNote(employeeDTO.getNote());
        user.setCreatedBy(storeId);

        try {
            employeeRepository.save(user);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi hệ thống: " + e.getMessage());
            return null;
        }
        return user;
    }

    public User updateEmployee(Long storeId, EmployeeDTO employeeDTO, RedirectAttributes redirectAttributes) {
        User user = new User();
        return user;
    }

    //lấy nhân viên theo id
    public User getEmployeeById(Long id) {
        return employeeRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
    }

    //xóa và khôi phục
    public void deleteAndRestoreEmployee(Long id) {
        User user = getEmployeeById(id);
        user.setDeleted(!user.isDeleted());
        employeeRepository.saveAndFlush(user);
    }

    public boolean updateEmployee(Long employeeId, EmployeeDTO employeeDTO) {
        User user = employeeRepository.findById(employeeId).orElse(null);

        if (user != null) {
            // Update user information
            user.setName(employeeDTO.getName());
            user.setEmail(employeeDTO.getEmail());
            user.setAddress(employeeDTO.getAddress());
            user.setPhone(employeeDTO.getPhone());
            user.setNote(employeeDTO.getNote());
            user.setUpdatedAt(LocalDateTime.now());

            employeeRepository.save(user);
            return true;
        }

        return false;
    }

    public Page<User> advancedSearchEmployees(Long storeId, String role, boolean isDeleted, String name, String email, String phone, String address, Pageable pageable) {
        return employeeRepository.advancedSearchEmployees(storeId, role, isDeleted, name, email, phone, address, pageable);
    }
}
