package swp.se1889.g1.rice_store.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import swp.se1889.g1.rice_store.dto.CustomerChangeHistoryDTO;
import swp.se1889.g1.rice_store.entity.Store;
import swp.se1889.g1.rice_store.entity.User;
import swp.se1889.g1.rice_store.repository.StoreRepository;
import swp.se1889.g1.rice_store.repository.UserRepository;
import swp.se1889.g1.rice_store.service.CustomerChangeHistoryService;
import swp.se1889.g1.rice_store.service.Iservice.UserService;
import swp.se1889.g1.rice_store.service.UserServiceIpml;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

@Controller
@RequestMapping("/owner/history")
public class CustomerChangeHistoryController {

    @Autowired
    private CustomerChangeHistoryService customerChangeHistoryService;
    @Autowired
    private UserRepository userRepository;
    @Autowired private UserServiceIpml userService;


    @GetMapping("/customer")
    public String getCustomerChangeHistory(
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String changedField,
            @RequestParam(required = false) String oldValue,
            @RequestParam(required = false) String changedBy,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model,
            HttpSession session) {
        Store store = (Store) session.getAttribute("store");
        if (store == null) {
            return "redirect:/owner/store";
        }
        model.addAttribute("store", store);
        model.addAttribute("user", userService.getCurrentUser());
        User user2 = userRepository.findByUsername(store.getCreatedBy());


        LocalDateTime parsedStartDate = null;
        LocalDateTime parsedEndDate = null;

        if (startDate != null && !startDate.trim().isEmpty()) {
            try {
                parsedStartDate = LocalDate.parse(startDate).atStartOfDay();
            } catch (DateTimeParseException e) {
                parsedStartDate = null;
            }
        }

        if (endDate != null && !endDate.trim().isEmpty()) {
            try {
                parsedEndDate = LocalDate.parse(endDate).atTime(23, 59, 59);
            } catch (DateTimeParseException e) {
                parsedEndDate = null;
            }
        }
        if(customerName != null && !customerName.trim().isEmpty()) {
            customerName = customerName.trim();
        }
        if(changedField != null && !changedField.trim().isEmpty()) {
            changedField = changedField.trim();
        }
        if(oldValue != null && !oldValue.trim().isEmpty()) {
            oldValue = oldValue.trim();
        }
        if(changedBy != null && !changedBy.trim().isEmpty()) {
            changedBy = changedBy.trim();
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<CustomerChangeHistoryDTO> changeHistories = customerChangeHistoryService.searchCustomerChanges(
                customerName, changedField,oldValue, changedBy, parsedStartDate, parsedEndDate, user2, pageable
        );

        model.addAttribute("changeHistories", changeHistories.getContent());
        model.addAttribute("currentPage", changeHistories.getNumber());
        model.addAttribute("totalPages", changeHistories.getTotalPages());
        model.addAttribute("totalItems", changeHistories.getTotalElements());
        model.addAttribute("user", userDetails);
        model.addAttribute("changedField", changedField);

        return "CustomerChangeHistory";
    }
}