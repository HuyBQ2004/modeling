package swp.se1889.g1.rice_store.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import swp.se1889.g1.rice_store.dto.ChangePasswordDTO;
import swp.se1889.g1.rice_store.dto.UserDTO;
import swp.se1889.g1.rice_store.entity.Store;
import swp.se1889.g1.rice_store.entity.User;
import swp.se1889.g1.rice_store.service.UserServiceIpml;

@Controller
public class ProfileController {

    @Autowired
    private UserServiceIpml userServiceIpml;

    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {
        User user = userServiceIpml.getCurrentUser();
        Store store = (Store) session.getAttribute("store");
        model.addAttribute("store", store);
        model.addAttribute("user", user);
        return "profile";
    }

    @PostMapping("/updateInfo")
    public String updateInfo(@Valid UserDTO userDTO,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            for (FieldError fieldError : bindingResult.getFieldErrors()) {
                redirectAttributes.addFlashAttribute("error", fieldError.getDefaultMessage());
            }
            return "redirect:/profile";
        }

        boolean hasError = userServiceIpml.validateUserInfor(userDTO, redirectAttributes);
        if (hasError) {
            return "redirect:/profile";
        }

        try {
            userServiceIpml.updateUserInfor(userDTO);
            redirectAttributes.addFlashAttribute("success", "Cập nhật thông tin thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Có lỗi xảy ra, vui lòng thử lại!");
        }
        return "redirect:/profile";
    }

    @PostMapping("/updatePassword")
    public String updatePassword(@Valid ChangePasswordDTO changePasswordDTO,
                                 BindingResult bindingResult,
                                 RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            for (FieldError fieldError : bindingResult.getFieldErrors()) {
                redirectAttributes.addFlashAttribute("error", fieldError.getDefaultMessage());
            }
            return "redirect:/profile";
        }

        boolean hasError = userServiceIpml.validateUpdatePassword(changePasswordDTO, redirectAttributes);
        if (!hasError) {
            return "redirect:/profile";
        }

        try {
            userServiceIpml.updateUserPassword(changePasswordDTO);
            redirectAttributes.addFlashAttribute("success", "Cập nhật mật khẩu thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Có lỗi xảy ra, vui lòng thử lại!");
        }
        return "redirect:/profile";
    }
}
