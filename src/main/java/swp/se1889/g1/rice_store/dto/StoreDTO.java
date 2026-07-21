package swp.se1889.g1.rice_store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class StoreDTO {
    @Pattern(regexp = "^[a-zA-Z0-9]+@[a-zA-Z0-9]+\\.[a-zA-Z]{2,}$", message = "Định dạng email không hợp lệ")
    private String email;

    @NotBlank(message = "Tên của hàng không được để trống")
    @Size(max = 50, message = "Tên cửa hàng không được dài quá 50 ký tự")
    private String storeName;

    @NotBlank(message = "Địa chỉ không được để trống")
    @Size(max = 255, message = "Địa chỉ không được dài quá 255 ký tự")
    private String address;

    @Size(min = 10, max = 15, message = "Số điện thoại phải có từ 10 đến 15 ký tự")
    private String phone;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
