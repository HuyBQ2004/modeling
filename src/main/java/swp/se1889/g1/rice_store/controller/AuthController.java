package swp.se1889.g1.rice_store.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import swp.se1889.g1.rice_store.dto.RegisterDTO;
import swp.se1889.g1.rice_store.entity.User;
import swp.se1889.g1.rice_store.repository.UserRepository;

@Controller
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping({"/", "/login"})
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid RegisterDTO registerDTO,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            for (FieldError fieldError : bindingResult.getFieldErrors()) {
                redirectAttributes.addFlashAttribute("error", fieldError.getDefaultMessage());
            }
            return "redirect:/register";
        }

        if (!registerDTO.getPassword().equals(registerDTO.getConfirmPassword())) {
            redirectAttributes.addFlashAttribute("error", "Mật khẩu nhập lại không khớp");
            return "redirect:/register";
        }
        if (userRepository.findByUsername(registerDTO.getUsername()) != null) {
            redirectAttributes.addFlashAttribute("error", "Tên đăng nhập đã tồn tại");
            return "redirect:/register";
        }
        if (userRepository.findByEmail(registerDTO.getEmail()) != null) {
            redirectAttributes.addFlashAttribute("error", "Email đã tồn tại");
            return "redirect:/register";
        }

        User newUser = new User();
        newUser.setUsername(registerDTO.getUsername());
        newUser.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        newUser.setEmail(registerDTO.getEmail());
        newUser.setRole("ROLE_OWNER");
        newUser.setName("");
        newUser.setAddress("");
        newUser.setPhone("");
        userRepository.save(newUser);

        return "redirect:/login";
    }
}
