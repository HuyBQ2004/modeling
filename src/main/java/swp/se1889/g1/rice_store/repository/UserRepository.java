package swp.se1889.g1.rice_store.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import swp.se1889.g1.rice_store.entity.User;


import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);

    Page<User> findByRole(String role, Pageable pageable);

    User findByUsername(String username);

    User findByEmail(String email);

    User findByPhone(String phone);

    User findById(long id);

    List<User> findByIsDeletedFalse();

    @Transactional
    @Modifying
    @Query("update User u set u.password = ?2 where u.email = ?1")
    void updatePassword(String email, String password);
}

