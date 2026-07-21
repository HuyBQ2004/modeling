package swp.se1889.g1.rice_store.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import swp.se1889.g1.rice_store.entity.ForgotPassword;

import java.util.Optional;

public interface ForgotPasswordRepository extends JpaRepository<ForgotPassword, Integer> {
    @Query("select fp from ForgotPassword  fp where fp.otp = ?1 and  fp.userId = ?2")
    Optional<ForgotPassword> findByOtpAndUser(Integer otp,Long userId);

    @Transactional
    void deleteByUserId(Long userId);
}
