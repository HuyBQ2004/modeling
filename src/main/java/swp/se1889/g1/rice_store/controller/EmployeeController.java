package swp.se1889.g1.rice_store.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import swp.se1889.g1.rice_store.dto.EmployeeDTO;
import swp.se1889.g1.rice_store.entity.Store;
import swp.se1889.g1.rice_store.entity.User;
import swp.se1889.g1.rice_store.service.EmployeeService;
import swp.se1889.g1.rice_store.service.UserServiceIpml;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/owner/employees")
public class EmployeeController {
    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private UserServiceIpml userService;

    @GetMapping("")
    public String employee(
            @RequestParam(value = "trangthai", required = false) String trangthai,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "5") int size,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "address", required = false) String address,
            Model model, HttpSession session) {

        Store store = (Store) session.getAttribute("store");
        if (store == null) {
            return "redirect:/owner/store";
        }

        Pageable pageable = PageRequest.of(page, size);

        // Default to working status if not specified
        if (trangthai == null || trangthai.equals("working")) {
            trangthai = "working";
        }
        boolean isDeleted = !trangthai.equals("working");

        // Check if any search parameters are provided
        Page<User> employeesPage;
        if ((name != null && !name.trim().isEmpty()) ||
                (email != null && !email.trim().isEmpty()) ||
                (phone != null && !phone.trim().isEmpty()) ||
                (address != null && !address.trim().isEmpty())) {

            employeesPage = employeeService.advancedSearchEmployees(
                    store.getId(),
                    "ROLE_EMPLOYEE",
                    isDeleted,
                    name,
                    email,
                    phone,
                    address,
                    pageable
            );
        } else {
            // If no search parameters, use standard pagination
            employeesPage = employeeService.getEmployeesActivePage(
                    store.getId(), "ROLE_EMPLOYEE", isDeleted, pageable
            );
        }

        model.addAttribute("employees", employeesPage.getContent());
        model.addAttribute("currentPage", employeesPage.getNumber());
        model.addAttribute("totalPages", employeesPage.getTotalPages());
        model.addAttribute("totalItems", employeesPage.getTotalElements());
        model.addAttribute("pageSize", size);
        model.addAttribute("store", store);
        model.addAttribute("trangthai", trangthai);

        // Add search parameters to model
        model.addAttribute("name", name);
        model.addAttribute("email", email);
        model.addAttribute("phone", phone);
        model.addAttribute("address", address);

        model.addAttribute("employeeDTO", new EmployeeDTO());

        User user = userService.getCurrentUser();
        model.addAttribute("user", user);
        return "employees";
    }

    @PostMapping("")
    public String updateEmployee(@RequestParam("id") Long employeeId,
                                 @ModelAttribute @Valid EmployeeDTO employeeDTO,
                                 BindingResult bindingResult,
                                 Model model,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        Store store = (Store) session.getAttribute("store");
        if (store == null) {
            return "redirect:/owner/store";
        }

        if (bindingResult.hasErrors()) {
            for (FieldError fieldError : bindingResult.getFieldErrors()) {
                redirectAttributes.addFlashAttribute("error", fieldError.getDefaultMessage());
            }
            return "redirect:/owner/employees";
        }

        try {
            boolean result = employeeService.updateEmployee(employeeId, employeeDTO);
            if (result) {
                redirectAttributes.addFlashAttribute("success", "Cập nhật thông tin nhân viên thành công!");
            } else {
                redirectAttributes.addFlashAttribute("error", "Không tìm thấy nhân viên cần cập nhật!");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Có lỗi xảy ra: " + e.getMessage());
        }

        return "redirect:/owner/employees";
    }

    @GetMapping("/{id}")
    public String deleteOrRestoreEmployee(RedirectAttributes redirectAttributes, @PathVariable("id") Long id, Model model, HttpSession session) {
        Store store = (Store) session.getAttribute("store");

        if (store == null) {
            return "redirect:/owner/store";
        }

        boolean check = employeeService.getEmployeeById(id).isDeleted();

        employeeService.deleteAndRestoreEmployee(id);
        model.addAttribute("store", store);
        if (!check) {
            redirectAttributes.addFlashAttribute("success", "Xóa nhân viên thành công!");
        } else {
            redirectAttributes.addFlashAttribute("success", "Khôi phục nhân viên thành công!");
        }

        return "redirect:/owner/employees";
    }

    //page add employee
    @GetMapping("/addEmployee")
    public String addEmployee(Model model, HttpSession session) {
        Store store = (Store) session.getAttribute("store");
        if (store == null) {
            return "redirect:/owner/store";
        }

        model.addAttribute("store", store);
        User user = userService.getCurrentUser();
        model.addAttribute("user", user);
        if (!model.containsAttribute("employeeDTO")) {
            model.addAttribute("employeeDTO", new EmployeeDTO());
        }
        return "addEmployee";
    }

    @PostMapping("/addEmployee")
    public String addEmployee(@ModelAttribute @Valid EmployeeDTO employeeDTO, BindingResult bindingResult, Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        Store store = (Store) session.getAttribute("store");
        if (store == null) {
            return "redirect:/owner/store";
        }
        model.addAttribute("store", store);
        if (bindingResult.hasErrors()) {
            User user1 = userService.getCurrentUser();
            model.addAttribute("user", user1);
            model.addAttribute("error", bindingResult);
            model.addAttribute("employeeDTO", employeeDTO);
            return "addEmployee";
        }

        User user = employeeService.addNewEmployee(store.getId(), employeeDTO, redirectAttributes);

        if (user != null) {
            redirectAttributes.addFlashAttribute("success", "Thêm nhân viên thành công!");
            return "redirect:/owner/employees/addEmployee";
        } else {
            User user1 = userService.getCurrentUser();

            model.addAttribute("user", user1);
            model.addAttribute("employeeDTO", employeeDTO);
            return "addEmployee";
        }
    }
}
