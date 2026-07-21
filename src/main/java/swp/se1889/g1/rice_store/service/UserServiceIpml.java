package swp.se1889.g1.rice_store.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import swp.se1889.g1.rice_store.dto.ChangePasswordDTO;
import swp.se1889.g1.rice_store.dto.UserDTO;
import swp.se1889.g1.rice_store.entity.User;
import swp.se1889.g1.rice_store.repository.UserRepository;
import swp.se1889.g1.rice_store.service.Iservice.UserService;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Service
public class UserServiceIpml implements UserService {

    private UserRepository userRepository;

    @Autowired
    public UserServiceIpml(UserRepository userRepository) {
        this.userRepository = userRepository;
    }


    @Override
    public User findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public void save(User user) {
        userRepository.save(user);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("Invalid username or password.");
        }

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                rolesToAuthorities(user)
        );
    }

    private Collection<? extends GrantedAuthority> rolesToAuthorities(User role) {
        return Collections.singletonList(new SimpleGrantedAuthority(role.getRole()));
    }

    public User getUserById(long id) {
        return userRepository.findById(id);
    }

    public User getCurrentUser() {
        String username = getCurrentUsername();
        return userRepository.findByUsername(username);
    }

    public Long getCurrentCreatedBy() {
        User currentUser = getCurrentUser();
        return currentUser != null ? currentUser.getCreatedBy() : null;
    }

    public boolean validateUserInfor(UserDTO userDTO, RedirectAttributes redirectAttributes) {
        boolean hasError = false;
        User currentUser = getCurrentUser();

        if (currentUser != null && !userDTO.getEmail().equals(currentUser.getEmail())) {
            if (userRepository.findByEmail(userDTO.getEmail()) != null) {
                redirectAttributes.addFlashAttribute("error", "Email đã tồn tại");
                hasError = true;
            }
        }

        if (currentUser != null && !userDTO.getPhone().equals(currentUser.getPhone())) {
            if (userRepository.findByPhone(userDTO.getPhone()) != null) {
                redirectAttributes.addFlashAttribute("error", "Số điện thoại đã tồn tại");
                hasError = true;
            }
        }
        return hasError;
    }

    public boolean validateUpdatePassword(ChangePasswordDTO changePasswordDTO, RedirectAttributes redirectAttributes) {
        boolean hasError = false;
        User user = getCurrentUser();
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

        if (!passwordEncoder.matches(changePasswordDTO.getCurrentPassword(), user.getPassword())) {
            redirectAttributes.addFlashAttribute("error", "Mật khẩu hiện tại không đúng");
            hasError = true;
        }

        if (!changePasswordDTO.getNewPassword().equals(changePasswordDTO.getConfirmNewPassword())) {
            redirectAttributes.addFlashAttribute("error", "Mật khẩu nhập lại không khớp");
            hasError = true;
        }

        return hasError;
    }

    public void updateUserInfor(UserDTO userDTO) {
        User currentUser = getCurrentUser();
        currentUser.setEmail(userDTO.getEmail());
        currentUser.setPhone(userDTO.getPhone());
        currentUser.setName(userDTO.getName());
        currentUser.setAddress(userDTO.getAddress());
        currentUser.setNote(userDTO.getNote());

        userRepository.save(currentUser);
    }

    public void updateUserPassword(ChangePasswordDTO changePasswordDTO) {
        User currentUser = getCurrentUser();
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        currentUser.setPassword(passwordEncoder.encode(changePasswordDTO.getNewPassword()));
        userRepository.save(currentUser);
    }

    public String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "guest";
    }


    public List<User> getAllActiveUsers() {
        return userRepository.findByIsDeletedFalse();
    }
}