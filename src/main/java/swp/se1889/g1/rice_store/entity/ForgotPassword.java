package swp.se1889.g1.rice_store.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;

import java.util.Date;

@Entity
@AllArgsConstructor
@Table(name = "forgotPassword")
public class ForgotPassword {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer fpid;

    @Column(name = "otp", nullable = false)
    private Integer otp;

    @Column(name = "expiration_time", nullable = false)
    private Date expirationTime;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Transient
    private User user;


    public ForgotPassword() {
    }

    // Constructor để khởi tạo đối tượng mà không cần @Builder
    public ForgotPassword(Integer otp, Date expirationTime, Long userId) {
        this.otp = otp;
        this.expirationTime = expirationTime;
        this.userId = userId;
    }

    // Setter cho user
    public void setUser(User user) {
        this.user = user;
        if (user != null) {
            this.userId = user.getId();
        }
    }


    public Integer getFpid() {
        return fpid;
    }

    public void setFpid(Integer fpid) {
        this.fpid = fpid;
    }

    public User getUser() {
        return user;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Date getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(Date expirationTime) {
        this.expirationTime = expirationTime;
    }

    public Integer getOtp() {
        return otp;
    }

    public void setOtp(Integer otp) {
        this.otp = otp;
    }
}

