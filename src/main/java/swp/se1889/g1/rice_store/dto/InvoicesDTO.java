package swp.se1889.g1.rice_store.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class InvoicesDTO {
    private Long storeId;
    private String customerPhone;
    private String customerName;
    private String customerAddress;
    private BigDecimal customerBalance;
    private String paymentMethod;
    private BigDecimal totalPrice;
    @PositiveOrZero(message = "Thanh toán không được âm")
    private BigDecimal paidAmount;
    private double remainingAmount;
    @NotBlank(message = "Ghi chú không được để trống")
    private String note;
    @Valid
    private List<InvoiceDetailDTO> details;
    private BigDecimal discount;
    private BigDecimal finalAmount;

    @AssertTrue(message = " Số tiền thanh toán phải bằng tổng giá trị hóa đơn!")
    public boolean isValidTienHangPayment() {
        if (paymentMethod == null || paidAmount == null || totalPrice == null) {
            return true; // Các ràng buộc @NotNull sẽ bắt lỗi nếu thiếu dữ liệu
        }
        if (paymentMethod.equalsIgnoreCase("onlyProduct")) {
            return paidAmount.compareTo(totalPrice) <= 0;
        }
        return true;
    }

    /**
     * Khi chọn thanh toán "TIEN_HANG_NO" (tiền hàng + nợ), số tiền thanh toán không được vượt quá tổng giá trị hóa đơn cộng số dư nợ của khách hàng.
     */
    @AssertTrue(message = "Số tiền thanh toán không được vượt quá tổng giá trị hóa đơn cộng số dư nợ của khách hàng!")
    public boolean isValidTienHangNoPayment() {
        if (paymentMethod == null || paidAmount == null || totalPrice == null || customerBalance == null) {
            return true; // Các ràng buộc @NotNull sẽ xử lý trường hợp thiếu dữ liệu
        }
        if (paymentMethod.equalsIgnoreCase("productAndDebt")) {
            BigDecimal allowedAmount = totalPrice.add(customerBalance);
            return paidAmount.compareTo(allowedAmount) <= 0;
        }
        return true;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }

    public BigDecimal getFinalAmount() {
        return finalAmount;
    }

    public void setFinalAmount(BigDecimal finalAmount) {
        this.finalAmount = finalAmount;
    }

    public Long getStoreId() {
        return storeId;
    }

    public void setStoreId(Long storeId) {
        this.storeId = storeId;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerAddress() {
        return customerAddress;
    }

    public void setCustomerAddress(String customerAddress) {
        this.customerAddress = customerAddress;
    }

    public BigDecimal getCustomerBalance() {
        return customerBalance;
    }

    public void setCustomerBalance(BigDecimal customerBalance) {
        this.customerBalance = customerBalance;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public double getRemainingAmount() {
        return remainingAmount;
    }

    public void setRemainingAmount(double remainingAmount) {
        this.remainingAmount = remainingAmount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public List<InvoiceDetailDTO> getDetails() {
        return details;
    }

    public void setDetails(List<InvoiceDetailDTO> details) {
        this.details = details;
    }
}
