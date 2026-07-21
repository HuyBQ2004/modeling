package swp.se1889.g1.rice_store.controller.Page;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import swp.se1889.g1.rice_store.service.UserServiceIpml;

@Controller
public class StoreManagePage {
    @Autowired
    private UserServiceIpml userService;


    @GetMapping("/admin/manage-store")
    public String GetViewManageOwner(Model model, HttpSession session) {
        model.addAttribute("user", userService.getCurrentUser());

        return "manage-store";
    }

}