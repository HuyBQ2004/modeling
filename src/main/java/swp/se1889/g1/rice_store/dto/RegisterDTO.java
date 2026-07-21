package swp.se1889.g1.rice_store.dto;

import jakarta.validation.constraints.*;

public class RegisterDTO {

    @Size(min = 4, message = "Tài khoản phải có ít nhất 4 ký tự")
    private String username;

    @Pattern(regexp = "^[a-zA-Z0-9]+@[a-zA-Z0-9]+\\.[a-zA-Z]{2,}$", message = "Định dạng email không hợp lệ")
    private String email;

    @Size(min = 6, message = "Mật khẩu phải có ít nhất 6 ký tự")
    private String password;

    private String confirmPassword;

    @AssertTrue(message = "Bạn cần đồng ý với điều khoản dịch vụ.")
    private boolean acceptTerms;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    public boolean isAcceptTerms() {
        return acceptTerms;
    }

    public void setAcceptTerms(boolean acceptTerms) {
        this.acceptTerms = acceptTerms;
    }
}
