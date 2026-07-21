package swp.se1889.g1.rice_store.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import swp.se1889.g1.rice_store.entity.User;

public interface UsersRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);


    Page<User> findUserByRole(String role, Pageable pageable);
}
