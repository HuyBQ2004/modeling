    package swp.se1889.g1.rice_store.controller;

    import jakarta.servlet.http.HttpSession;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.data.domain.Page;
    import org.springframework.http.ResponseEntity;
    import org.springframework.stereotype.Controller;
    import org.springframework.ui.Model;
    import org.springframework.validation.BindingResult;
    import org.springframework.web.bind.annotation.*;
    import swp.se1889.g1.rice_store.dto.CustomerChangeHistoryDTO;
    import swp.se1889.g1.rice_store.dto.CustomerDTO;
    import swp.se1889.g1.rice_store.entity.Store;
    import swp.se1889.g1.rice_store.entity.User;
    import swp.se1889.g1.rice_store.service.CustomerChangeHistoryService;
    import swp.se1889.g1.rice_store.service.CustomerService;

    import jakarta.validation.Valid;
    import swp.se1889.g1.rice_store.service.UserServiceIpml;

    import java.util.Collections;
    import java.util.HashMap;
    import java.util.List;
    import java.util.Map;

    @Controller
    public class CustomerController {

        @Autowired
        private CustomerService customerService;

        @Autowired
        private UserServiceIpml userService;

        @GetMapping("/customers")
        public String getCustomers(
                @RequestParam(defaultValue = "0") int page,
                @RequestParam(defaultValue = "5") int size,
                @RequestParam(required = false) String name,
                @RequestParam(required = false) String phone,
                @RequestParam(required = false) String address,
                @RequestParam(required = false) String email,
                @RequestParam(required = false) String debt,
                @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate createdDate,
                @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate updatedDate,
                Model model, HttpSession session) {

            Store store = (Store) session.getAttribute("store");
            model.addAttribute("store", store);
            model.addAttribute("user", userService.getCurrentUser());


            Page<CustomerDTO> customerPage = customerService.filterCustomersWithSpec(
                    name, phone, address, email, debt, createdDate, updatedDate, page, size);

            model.addAttribute("customers", customerPage.getContent());
            model.addAttribute("totalPages", customerPage.getTotalPages());
            model.addAttribute("currentPage", page);
            model.addAttribute("totalItems", customerPage.getTotalElements());

            model.addAttribute("name", name);
            model.addAttribute("phone", phone);
            model.addAttribute("address", address);
            model.addAttribute("email", email);
            model.addAttribute("debt", debt);
            model.addAttribute("createdDate", createdDate);
            model.addAttribute("updatedDate", updatedDate);

            model.addAttribute("newCustomer", new CustomerDTO());
            model.addAttribute("editCustomer", new CustomerDTO());

            return "customers";
        }


        @PostMapping("/customers/add")
        @ResponseBody
        public ResponseEntity<?> addCustomer(@Valid @ModelAttribute("newCustomer") CustomerDTO customerDTO,
                                             BindingResult result) {
            if (result.hasErrors()) {
                Map<String, String> errors = new HashMap<>();
                result.getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
                return ResponseEntity.badRequest().body(errors);
            }

            try {
                customerService.createCustomer(customerDTO);
                return ResponseEntity.ok().body(Collections.singletonMap("message", "Thêm khách hàng thành công!"));
            } catch (RuntimeException e) {
                return ResponseEntity.badRequest().body(Collections.singletonMap("errorMessage", e.getMessage()));
            }
        }

        @GetMapping("/edit-customer/{id}")
        @ResponseBody
        public ResponseEntity<?> getCustomerForEdit(@PathVariable Long id) {
            try {
                CustomerDTO customerDTO = customerService.getCustomerById(id);
                return ResponseEntity.ok(customerDTO);
            } catch (RuntimeException e) {
                return ResponseEntity.badRequest().body(Collections.singletonMap("errorMessage", e.getMessage()));
            }
        }


        @PostMapping("/customers/update")
        @ResponseBody
        public ResponseEntity<?> updateCustomerAjax(@Valid @ModelAttribute("editCustomer") CustomerDTO customerDTO,
                                                    BindingResult result) {
            if (result.hasErrors()) {
                Map<String, String> errors = new HashMap<>();
                result.getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
                return ResponseEntity.badRequest().body(errors);
            }

            try {
                customerService.updateCustomer(customerDTO);
                return ResponseEntity.ok(Collections.singletonMap("message", "Cập nhật khách hàng thành công!"));
            } catch (RuntimeException e) {
                return ResponseEntity.badRequest().body(Collections.singletonMap("errorMessage", e.getMessage()));
            }
        }


        @GetMapping("/manageUser")
        public String GetViewManageUser(Model model, HttpSession session) {
            User user = userService.getCurrentUser();
            model.addAttribute("user", user);

            Store store = (Store) session.getAttribute("store");
            model.addAttribute("store", store);
            return "manage-user";
        }


    }
