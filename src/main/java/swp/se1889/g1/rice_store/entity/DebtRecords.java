package swp.se1889.g1.rice_store.entity;


import jakarta.persistence.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "debt_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebtRecords {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

//    @Column(name = "store_id", nullable = false)
//    private Long storeId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DebtType type;

    @Column(nullable = false, name = "amount")
    private BigDecimal amount;
    @Column(name = "note" , nullable = false)
    private String note;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDelete = false;

     @Column(name="create_on" , nullable = false)
     @DateTimeFormat(pattern = "dd/MM/yyyy HH:mm:ss")  // Format theo Tiếng Việt
     private LocalDateTime createOn ;
//    @Column(name ="email" , nullable = false)
//    private String email;

    public LocalDateTime getCreateOn() {
        return createOn;
    }

    public void setCreateOn(LocalDateTime createOn) {
        this.createOn = createOn;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum DebtType {
        Customer_debt_shop , Customer_return_shop , Shop_debt_customer , Shop_return_customer
    }
    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }
//    public void setStoreId(Long storeId) {
//        this.storeId = storeId;
//    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

//    public Long getStoreId() {
//        return storeId;
//    }

    public Long getCustomerId() {
        return customerId;
    }

    public DebtType getType() {
        return type;
    }

    public void setType(DebtType type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
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

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Boolean getDelete() {
        return isDelete;
    }

    public void setDelete(Boolean delete) {
        isDelete = delete;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

//    public String getEmail() {
//        return email;
//    }
//
//    public void setEmail(String email) {
//        this.email = email;
//    }
}
