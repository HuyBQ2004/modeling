package swp.se1889.g1.rice_store.service.Iservice;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import swp.se1889.g1.rice_store.entity.User;

@Service
public interface UserService extends UserDetailsService {
    public User findByUsername(String username);

    public void save(User user);
}