package swp.se1889.g1.rice_store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CustomerInvoiceDTO {

    @NotBlank(message = "Tên khách hàng không được để trống")
    @Size(max = 50, message = "Tên khách hàng không được dài quá 50 ký tự")
    private String customerInvoiceName;

    @Pattern(regexp = "^[a-zA-Z0-9]+@[a-zA-Z0-9]+\\.[a-zA-Z]{2,}$", message = "Định dạng email không hợp lệ")
    private String customerInvoiceEmail;

    @Size(min = 10, max = 15, message = "Số điện thoại phải có từ 10 đến 15 ký tự")
    private String customerInvoicePhone;

    @NotBlank(message = "Địa chỉ không được để trống")
    @Size(max = 255, message = "Địa chỉ không được dài quá 255 ký tự")
    private String customerInvoiceAddress;

    public String getCustomerInvoiceName() {
        return customerInvoiceName;
    }

    public void setCustomerInvoiceName(String customerInvoiceName) {
        this.customerInvoiceName = customerInvoiceName;
    }

    public String getCustomerInvoiceEmail() {
        return customerInvoiceEmail;
    }

    public void setCustomerInvoiceEmail(String customerInvoiceEmail) {
        this.customerInvoiceEmail = customerInvoiceEmail;
    }

    public String getCustomerInvoicePhone() {
        return customerInvoicePhone;
    }

    public void setCustomerInvoicePhone(String customerInvoicePhone) {
        this.customerInvoicePhone = customerInvoicePhone;
    }

    public String getCustomerInvoiceAddress() {
        return customerInvoiceAddress;
    }

    public void setCustomerInvoiceAddress(String customerInvoiceAddress) {
        this.customerInvoiceAddress = customerInvoiceAddress;
    }
}
