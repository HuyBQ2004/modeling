package swp.se1889.g1.rice_store.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UserDTO {
    @Pattern(regexp = "^[a-zA-Z0-9]+@[a-zA-Z0-9]+\\.[a-zA-Z]{2,}$", message = "Định dạng email không hợp lệ")
    private String email;

    @Size(max = 50, message = "Tên không được dài quá 50 ký tự")
    private String name;

    @Size(max = 255, message = "Địa chỉ không được dài quá 255 ký tự")
    private String address;

    @Size(min = 10, max = 15, message = "Số điện thoại phải có từ 10 đến 15 ký tự")
    private String phone;

    @Size(max = 500, message = "Ghi chú không được dài quá 500 ký tự")
    private String note;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
