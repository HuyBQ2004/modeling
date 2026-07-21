package swp.se1889.g1.rice_store.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import swp.se1889.g1.rice_store.dto.request.UpdateUserRequest;
import swp.se1889.g1.rice_store.dto.request.UserRequest;
import swp.se1889.g1.rice_store.entity.Store;
import swp.se1889.g1.rice_store.entity.User;
import swp.se1889.g1.rice_store.service.Iservice.UserService;
import swp.se1889.g1.rice_store.service.UserServiceIpml;
import swp.se1889.g1.rice_store.service.UsersService;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    UsersService usersService;

    @Autowired
    private UserServiceIpml userService;


    @GetMapping("get-all")
    public Page<User> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model,
            HttpSession session) {
        Pageable pageable = PageRequest.of(page, size);

        User user = userService.getCurrentUser();
        model.addAttribute("user", user);

        Store store = (Store) session.getAttribute("store");
        model.addAttribute("store", store);

        return usersService.findAllUser(pageable);
    }

    @PostMapping("create")
    public User createUser(@Valid @RequestBody UserRequest userRequest) {
        return usersService.createUser(userRequest);
    }

    @PutMapping("update/{id}")
    public User updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return usersService.updateUser(id, request);
    }


}
