package swp.se1889.g1.rice_store.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import swp.se1889.g1.rice_store.dto.ShiftDTO;
import swp.se1889.g1.rice_store.entity.*;
import swp.se1889.g1.rice_store.service.EmployeeService;
import swp.se1889.g1.rice_store.service.ShiftService;
import swp.se1889.g1.rice_store.service.UserServiceIpml;
import swp.se1889.g1.rice_store.service.WorkShiftService;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;


@Controller
@RequestMapping("/shifts")
public class ShiftManagementController {

    private final ShiftService shiftService;
    private final UserServiceIpml userService;
    private final WorkShiftService workShiftService;
    private final EmployeeService employeeService;

    @Autowired
    public ShiftManagementController(ShiftService shiftService, UserServiceIpml userService, WorkShiftService workShiftService, EmployeeService employeeService) {
        this.shiftService = shiftService;
        this.userService = userService;
        this.workShiftService = workShiftService;
        this.employeeService = employeeService;
    }

    @GetMapping("/schedule")
    public String viewSchedule(Model model,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate searchDate,
                               @RequestParam(required = false) Integer weekNumber,
                               @RequestParam(required = false) Integer year,
                               HttpSession session) {

        Store store = (Store) session.getAttribute("store");
        if (store == null) {
            return "redirect:/store";
        }

        LocalDate currentDate = LocalDate.now();
        if (weekNumber == null) {
            weekNumber = currentDate.get(WeekFields.of(Locale.getDefault()).weekOfYear());
        }

        if (year == null) {
            year = currentDate.getYear();
        }

        LocalDate weekStart = LocalDate.now()
                .with(WeekFields.of(Locale.getDefault()).weekOfYear(), weekNumber)
                .with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1)
                .withYear(year);

        LocalDate weekEnd = weekStart.plusDays(6);

        List<Shift> allShifts = shiftService.getStoreByCreatedBy(store.getId());
        List<User> allUsers = employeeService.getEmployees(store.getId(), "ROLE_EMPLOYEE");

        List<WorkShift> weekWorkShifts = workShiftService.getWorkShiftsByDateRange(weekStart, weekEnd);

        Map<String, Map<String, List<User>>> scheduleData = prepareScheduleData(weekWorkShifts, allShifts, allUsers, weekStart);

        // Add empty ShiftDTO for the create form
        model.addAttribute("newShift", new ShiftDTO());
        model.addAttribute("searchDate", searchDate);
        model.addAttribute("weekNumber", weekNumber);
        model.addAttribute("year", year);
        model.addAttribute("weekStart", weekStart);
        model.addAttribute("weekEnd", weekEnd);
        model.addAttribute("scheduleData", scheduleData);
        model.addAttribute("shifts", allShifts);
        model.addAttribute("users", allUsers);
        model.addAttribute("store", store);

        // Ensure user details are available in the Thymeleaf template
        model.addAttribute("userDetails", allUsers.stream().collect(Collectors.toMap(User::getId, user -> user)));

        User user = userService.getCurrentUser();
        model.addAttribute("user", user);

        return "weeklySchedule";
    }

    @GetMapping("/currentWeek")
    public String viewCurrentWeek(Model model, HttpSession session) {
        Store store = (Store) session.getAttribute("store");
        if (store == null) {
            return "redirect:/store";
        }

        User user = userService.getCurrentUser();
        model.addAttribute("store", store);
        model.addAttribute("user", user);
        return "redirect:/shifts/schedule";
    }

    @PostMapping("/assign")
    public String assignEmployee(@ModelAttribute WorkShiftForm workShiftForm, RedirectAttributes redirectAttributes, Model model, HttpSession session) {
        Store store = (Store) session.getAttribute("store");

        if (store == null) {
            return "redirect:/store";
        }
        User user = userService.getCurrentUser();
        model.addAttribute("store", store);
        model.addAttribute("user", user);

        try {
            workShiftService.assignEmployeeToShift(
                    workShiftForm.getUserId(),
                    workShiftForm.getShiftId(),
                    workShiftForm.getWorkDate(),
                    store.getId()
            );
            redirectAttributes.addFlashAttribute("success", "Đã phân công nhân viên thành công");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        // Redirect back to the same week
        return "redirect:/shifts/schedule?weekNumber=" + workShiftForm.getWeekNumber() + "&year=" + workShiftForm.getYear();
    }

    @PostMapping("/remove")
    public String removeAssignment(@ModelAttribute WorkShiftForm workShiftForm, RedirectAttributes redirectAttributes, Model model, HttpSession session) {
        Store store = (Store) session.getAttribute("store");

        if (store == null) {
            return "redirect:/store";
        }
        User user = userService.getCurrentUser();
        model.addAttribute("store", store);
        model.addAttribute("user", user);
        try {
            workShiftService.removeEmployeeFromShift(
                    workShiftForm.getUserId(),
                    workShiftForm.getShiftId(),
                    workShiftForm.getWorkDate()
            );
            redirectAttributes.addFlashAttribute("success", "Đã xóa phân công thành công");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/shifts/schedule?weekNumber=" + workShiftForm.getWeekNumber() + "&year=" + workShiftForm.getYear();
    }

    @GetMapping("/add")
    public String showAddShiftForm(Model model, HttpSession session) {
        Store store = (Store) session.getAttribute("store");
        if (store == null) {
            return "redirect:/store";
        }
        User user = userService.getCurrentUser();
        model.addAttribute("user", user);

        model.addAttribute("newShift", new ShiftDTO());
        model.addAttribute("store", store);
        return "addShift";
    }

    @PostMapping("/add")
    public String createShift(@ModelAttribute ShiftDTO shiftDTO, Model model, BindingResult bindingResult, RedirectAttributes redirectAttributes, HttpSession session) {
        Store store = (Store) session.getAttribute("store");
        if (store == null) {
            return "redirect:/store";
        }
        User user = userService.getCurrentUser();
        model.addAttribute("store", store);
        model.addAttribute("user", user);

        if (bindingResult.hasErrors()) {
            model.addAttribute("newShift", shiftDTO);
            model.addAttribute("store", store);
            return "addShift";
        }

        try {
            shiftService.createShift(shiftDTO, store.getId());
            redirectAttributes.addFlashAttribute("success", "Đã tạo ca làm việc mới thành công");
            return "redirect:/shifts/add";
        } catch (Exception e) {
            // Khi có lỗi, hiển thị form lại với dữ liệu đã nhập và thông báo lỗi
            model.addAttribute("newShift", shiftDTO);
            model.addAttribute("store", store);
            model.addAttribute("error", e.getMessage());
            return "addShift";
        }
    }
    @PostMapping("/delete/{id}")
    public String deleteShift(@PathVariable Long id,Model model, RedirectAttributes redirectAttributes, HttpSession session) {
        Store store = (Store) session.getAttribute("store");
        if (store == null) {
            return "redirect:/store";
        }
        User user = userService.getCurrentUser();
        model.addAttribute("store", store);
        model.addAttribute("user", user);

        try {
            boolean deleted = shiftService.deleteShift(id, store.getId());
            if (deleted) {
                redirectAttributes.addFlashAttribute("success", "Đã xóa ca làm việc thành công");
            } else {
                redirectAttributes.addFlashAttribute("error", "Không thể xóa ca làm việc này");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/shifts/schedule";
    }

    private Map<String, Map<String, List<User>>> prepareScheduleData(
            List<WorkShift> workShifts,
            List<Shift> shifts,
            List<User> users,
            LocalDate weekStart) {

        Map<String, Map<String, List<User>>> scheduleData = new HashMap<>();

        // Initialize the structure
        for (Shift shift : shifts) {
            Map<String, List<User>> shiftData = new HashMap<>();

            // For each day of the week
            for (int i = 0; i < 7; i++) {
                LocalDate date = weekStart.plusDays(i);
                shiftData.put(date.toString(), new ArrayList<>());
            }

            scheduleData.put(shift.getShiftCode(), shiftData);
        }

        // Fill in the data
        for (WorkShift workShift : workShifts) {
            Shift shift = shifts.stream()
                    .filter(s -> s.getId().equals(workShift.getShift()))
                    .findFirst()
                    .orElse(null);

            if (shift != null) {
                User employee = users.stream()
                        .filter(u -> u.getId() == workShift.getEmployee())
                        .findFirst()
                        .orElse(null);

                if (employee != null) {
                    String dateKey = workShift.getWorkDate().toString();
                    scheduleData.get(shift.getShiftCode()).get(dateKey).add(employee);
                }
            }
        }

        return scheduleData;
    }
}