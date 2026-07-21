package swp.se1889.g1.rice_store.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import swp.se1889.g1.rice_store.entity.Customer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CustomerDTO {

    private Long id;  // ID khách hàng

    @NotBlank(message = "Tên khách hàng không được để trống")
    @Size(max = 100, message = "Tên khách hàng không được dài quá 100 ký tự")
    private String name;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "0\\d{9}", message = "Số điện thoại phải có đúng 10 số và bắt đầu bằng 0")

    private String phone;

    @NotBlank(message = "Địa chỉ không được để trống")
    @Size(max = 255, message = "Địa chỉ không được dài quá 255 ký tự")
    private String address;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    private String email;

    private BigDecimal debtBalance = BigDecimal.ZERO;
    private LocalDateTime createdAt;  // Ngày tạo
    private LocalDateTime updatedAt;  // Ngày cập nhật
    private String createdBy; // Người thêm
    private String updatedBy; // Người sửa
    public CustomerDTO(Customer customer) {
        this.id = customer.getId();
        this.name = customer.getName();
        this.phone = customer.getPhone();
        this.address = customer.getAddress();
        this.email = customer.getEmail();
        this.debtBalance = customer.getDebtBalance();
        this.createdBy = customer.getCreatedBy() != null ? customer.getCreatedBy().getUsername() : null;
        this.updatedBy = customer.getUpdatedBy();
        this.createdAt = customer.getCreatedAt();
        this.updatedAt = customer.getUpdatedAt();
    }


    public CustomerDTO(Long id, String name, String phone, String address, String email, BigDecimal debtBalance, String createdBy, String updatedBy) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.email = email;
        this.debtBalance = debtBalance;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }
    public CustomerDTO(Long id, String name, String phone, String address, String email,
                       BigDecimal debtBalance, String createdBy, String updatedBy,
                       LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.email = email;
        this.debtBalance = debtBalance;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public CustomerDTO(Long id, String name, String phone, String address, String email) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.email = email;
    }
    public CustomerDTO(){

    }



    // Getters và Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public BigDecimal getDebtBalance() {
        return debtBalance;
    }

    public void setDebtBalance(BigDecimal debtBalance) {
        this.debtBalance = debtBalance;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    public String getCreatedBy() {
        return createdBy;
    }
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
    public String getUpdatedBy() {
        return updatedBy;
    }
    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

}
