package swp.se1889.g1.rice_store.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import swp.se1889.g1.rice_store.dto.request.UpdateUserRequest;
import swp.se1889.g1.rice_store.dto.request.UserRequest;
import swp.se1889.g1.rice_store.entity.User;
import swp.se1889.g1.rice_store.mapper.UserMapper;
import swp.se1889.g1.rice_store.repository.UserRepository;



@Service
public class OwnerService {
    @Autowired
    UserRepository userRepository;

    @Autowired
    UserMapper userMapper;


    @Autowired
    private PasswordEncoder passwordEncoder;

    public Page<User> findAllOwner(Pageable pageable) {
        return userRepository.findByRole("ROLE_OWNER", pageable);
    }

    public User createOwner(UserRequest request) {

        var userExist = userRepository.existsByEmail(request.getEmail());

        if (userExist) {
            throw new RuntimeException("Email đã tồn tại!");
        }

        User user = userMapper.toUser(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setDeleted(false);

        return userRepository.save(user);
    }

    public User updateOwner(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));
        userMapper.updateUserFromRequest(request, user);

        return userRepository.save(user);
    }

}
