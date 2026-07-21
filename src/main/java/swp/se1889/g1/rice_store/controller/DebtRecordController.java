package swp.se1889.g1.rice_store.controller;

//import swp.se1889.g1.rice_store.entity.DebtsRecord;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import swp.se1889.g1.rice_store.dto.CustomerDTO;
import swp.se1889.g1.rice_store.entity.DebtRecords;
import swp.se1889.g1.rice_store.entity.Store;
import swp.se1889.g1.rice_store.entity.User;
import swp.se1889.g1.rice_store.service.DebtRecordService;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/debt")
public class DebtRecordController {

    @Autowired
    private DebtRecordService debtRecordService;
    @Autowired
    private swp.se1889.g1.rice_store.service.CustomerService customerService;
    @Autowired
    private swp.se1889.g1.rice_store.service.UserServiceIpml userService;

    // Hiển thị trang form thêm phiếu nợ cho customer cụ thể
    @GetMapping("/add")
    public String showAddDebtForm(@RequestParam("customerId") Long customerId, Model model, HttpSession session) {
        CustomerDTO customer = customerService.getCustomerById(customerId);
        if (customer == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        }
        DebtRecords debtRecord = new DebtRecords();
        debtRecord.setCustomerId(customerId);
        Store store = (Store) session.getAttribute("store");
        model.addAttribute("store", store);
        model.addAttribute("debtRecord", debtRecord);
        model.addAttribute("customer", customer);
        User user = userService.getCurrentUser();
        model.addAttribute("user", user);// Add customer to the model

        return "addDebt";
    }

    // Xử lý submit form thêm phiếu nợ
    @PostMapping("/add")
    public String addDebtRecord(@ModelAttribute("debtRecord") DebtRecords debtRecord, Model model, HttpSession session, RedirectAttributes redirectAttributes,
                                @RequestParam("createOn") String createOnStr) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", new Locale("vi", "VN"));
        LocalDateTime createOn = LocalDateTime.parse(createOnStr, formatter);
        if (debtRecord.getAmount() == null || debtRecord.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            redirectAttributes.addFlashAttribute("error", "Số tiền nợ phải lớn hơn 0");
            model.addAttribute("customer", customerService.getCustomerById(debtRecord.getCustomerId()));
            return "addDebt";
        }
        Store store = (Store) session.getAttribute("store");
        model.addAttribute("store", store);
        debtRecord.setCreateOn(createOn);
        DebtRecords debtRecords = debtRecordService.addDebt(debtRecord, false);
        if (debtRecords != null) {
            redirectAttributes.addFlashAttribute("success", "Thêm phiếu nợ thành công");
        }

        // Sau khi thêm, chuyển hướng về trang danh sách khách hàng hoặc trang chi tiết phiếu nợ của customer đó
        return "redirect:/debt/detail?customerId=" + debtRecord.getCustomerId();
    }

    // Hiển thị trang chi tiết phiếu nợ theo customer id
    @GetMapping("/detail")
    public String showDebtDetail(
            @RequestParam("customerId") Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String idMin,
            @RequestParam(required = false) String idMax,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String amountMin,
            @RequestParam(required = false) String amountMax,
            @RequestParam(required = false) String dateMin,
            @RequestParam(required = false) String dateMax,
            @RequestParam(required = false) String dateMin2,
            @RequestParam(required = false) String dateMax2,
            Model model,
            HttpSession session) {
        // Parse filter parameters
        Long parsedIdMin = parseLong(idMin);
        Long parsedIdMax = parseLong(idMax);
        BigDecimal parsedAmountMin = parseBigDecimal(amountMin);
        BigDecimal parsedAmountMax = parseBigDecimal(amountMax);
        Date parsedDateMin = parseDate(dateMin);
        Date parsedDateMax = parseDate(dateMax);
        Date parsedDateMin2 = parseDate(dateMin2);
        Date parsedDateMax2 = parseDate(dateMax2);


        // Fetch filtered debt records
        Pageable pageable = PageRequest.of(page, size);
        Page<DebtRecords> debtRecords = debtRecordService.getFilteredDebtRecords(
                customerId, pageable, parsedIdMin, parsedIdMax, note, type, parsedAmountMin, parsedAmountMax, parsedDateMin, parsedDateMax, parsedDateMin2, parsedDateMax2);

        // Add attributes to model
        CustomerDTO customer = customerService.getCustomerById(customerId);
        Store store = (Store) session.getAttribute("store");
        User user = userService.getCurrentUser();

        model.addAttribute("debtRecords", debtRecords.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", debtRecords.getTotalPages());
        model.addAttribute("totalItems", debtRecords.getTotalElements());
        model.addAttribute("recordsPerPage", size);
        model.addAttribute("customer", customer);
        model.addAttribute("store", store);
        model.addAttribute("user", user);
        model.addAttribute("customerName", customer.getName());
        model.addAttribute("customerPhone", customer.getPhone());


        // Preserve filter values in inputs
        model.addAttribute("idMin", idMin);
        model.addAttribute("idMax", idMax);
        model.addAttribute("note", note);
        model.addAttribute("type", type);
        model.addAttribute("amountMin", amountMin);
        model.addAttribute("amountMax", amountMax);
        model.addAttribute("dateMin", dateMin);
        model.addAttribute("dateMax", dateMax);
        model.addAttribute("dateMin2", dateMin2);
        model.addAttribute("dateMax2", dateMax2);


        return "debtDetail";
    }

    // Helper methods to parse parameters
    private Long parseLong(String value) {
        if (value != null && !value.isEmpty()) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return null; // Ignore invalid input
            }
        }
        return null;
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value != null && !value.isEmpty()) {
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException e) {
                return null; // Ignore invalid input
            }
        }
        return null;
    }

    private Date parseDate(String value) {
        if (value != null && !value.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                return sdf.parse(value);
            } catch (ParseException e) {
                return null; // Ignore invalid input
            }
        }
        return null;
    }
}
