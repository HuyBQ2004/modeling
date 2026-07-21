package swp.se1889.g1.rice_store.controller;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import swp.se1889.g1.rice_store.dto.MailBodyDTO;
import swp.se1889.g1.rice_store.entity.ForgotPassword;
import swp.se1889.g1.rice_store.entity.User;
import swp.se1889.g1.rice_store.repository.ForgotPasswordRepository;
import swp.se1889.g1.rice_store.repository.UserRepository;
import swp.se1889.g1.rice_store.service.EmailService;

import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

@Controller
@RequestMapping("/forgotPassword")
public class ForgotPasswordController {
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final ForgotPasswordRepository forgotPasswordRepository;
    private final PasswordEncoder passwordEncoder;

    public ForgotPasswordController(UserRepository userRepository, EmailService emailService, ForgotPasswordRepository forgotPasswordRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.forgotPasswordRepository = forgotPasswordRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Show forgot password page
    @GetMapping
    public String showForgotPasswordPage() {
        return "forgotPassword/forgotPassword";
    }

    // Handle form submission for sending OTP
    @PostMapping("/sendOtp")
    public String sendOtp(@RequestParam String email, RedirectAttributes redirectAttributes) {
        User user = userRepository.findByEmail(email);

        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Email không tồn tại");
            return "redirect:/forgotPassword";
        }

        int otp = otpGenerator();
        MailBodyDTO mailBodyDTO = new MailBodyDTO(email, "OTP cho yêu cầu Quên mật khẩu", "OTP tồn tại trong vòng 1 phút! Đây là OTP cho yêu cầu Quên mật khẩu của bạn: " + otp );

        ForgotPassword fp = new ForgotPassword(otp, new Date(System.currentTimeMillis() + 60 * 1000), user.getId());
        emailService.sendSimpleMessage(mailBodyDTO);
        forgotPasswordRepository.save(fp);

        return "redirect:/forgotPassword/verify?email=" + email;
    }

    // Show OTP verification page
    @GetMapping("/verify")
    public String showVerifyOtpPage(@RequestParam String email, Model model) {
        model.addAttribute("email", email);
        return "forgotPassword/verifyOTP";
    }

    // Handle form submission for verifying OTP
    @PostMapping("/verify")
    public String validateOtp(@RequestParam Integer otp, @RequestParam String email, RedirectAttributes redirectAttributes) {
        User user = userRepository.findByEmail(email);

        if (user == null) {
            redirectAttributes.addAttribute("error", "Kông tìm thấy người dùng");
            return "redirect:/forgotPassword/verify?email=" + email;
        }

        Optional<ForgotPassword> fpOptional = forgotPasswordRepository.findByOtpAndUser(otp, user.getId());

        if (fpOptional.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "OTP không hợp lệ");
            return "redirect:/forgotPassword/verify?email=" + email;
        }

        ForgotPassword fp = fpOptional.get();
        if (fp.getExpirationTime().before(Date.from(Instant.now()))) {
            forgotPasswordRepository.deleteById(fp.getFpid());
            redirectAttributes.addFlashAttribute("error", "OTP đã hết hạn");
            return "redirect:/forgotPassword/verify?email=" + email;
        }

        // OTP is valid, redirect to change password page
        return "redirect:/forgotPassword/changePassword?email=" + email;
    }

    // Show change password page
    @GetMapping("/changePassword")
    public String showChangePasswordPage(@RequestParam String email, Model model) {
        model.addAttribute("email", email);
        return "forgotPassword/changePassword";
    }

    // Handle form submission for changing password
    @PostMapping("/updatePassword")
    public String updatePassword(@RequestParam String email, @RequestParam String password, @RequestParam String repeatPassword, RedirectAttributes redirectAttributes) {
        if (!Objects.equals(password, repeatPassword)) {
            redirectAttributes.addFlashAttribute("error", "Mật khẩu không khớp");
            return "redirect:/forgotPassword/changePassword?email=" + email;
        }

        User user = userRepository.findByEmail(email);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Người dùng không tồn tại");
            return "redirect:/forgotPassword/changePassword?email=" + email;
        }

        userRepository.updatePassword(email, passwordEncoder.encode(password));
        forgotPasswordRepository.deleteByUserId(user.getId());

        redirectAttributes.addFlashAttribute("success", "Mật khẩu đã được thay đổi thành công");
        return "redirect:/login";
    }

    // Handle resending OTP
    @PostMapping("/resendOtp")
    public String resendOtp(@RequestParam String email, RedirectAttributes redirectAttributes) {
        return sendOtp(email, redirectAttributes);
    }

    private Integer otpGenerator() {
        Random random = new Random();
        return random.nextInt(100_000, 1_000_000);
    }
}
