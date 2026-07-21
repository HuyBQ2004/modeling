package swp.se1889.g1.rice_store.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import swp.se1889.g1.rice_store.dto.request.UpdateUserRequest;
import swp.se1889.g1.rice_store.dto.request.UserRequest;
import swp.se1889.g1.rice_store.entity.User;
import swp.se1889.g1.rice_store.service.OwnerService;

@RestController()
@RequestMapping("/api/owner")
public class OwnerApiController {

    @Autowired
    OwnerService ownerService;

    @GetMapping("get-all")
    public Page<User> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ownerService.findAllOwner(pageable);
    }

    @PostMapping("create")
    public User createOwner(@Valid @RequestBody UserRequest userRequest) {
        return ownerService.createOwner(userRequest);
    }

    @PutMapping("update/{id}")
    public User updateOwner(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return ownerService.updateOwner(id, request);
    }


}
