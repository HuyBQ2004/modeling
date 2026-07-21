package swp.se1889.g1.rice_store.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoice_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InvoicesDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @JsonIgnore
    @ManyToOne
     @JoinColumn(name = "product_id" , nullable = false)
     private Product product;
     @ManyToOne
     @JoinColumn(name = "zone_id" , nullable = true)
     private Zone zone;
     @ManyToOne
     @JoinColumn(name = "customer_id" , nullable = false)
     private Customer customer;
    @ManyToOne
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoices invoice;
    @Column(name = "quantity", nullable = false)
    private int quantity;
    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;
    @Column(name = "total_price", nullable = false)
    private BigDecimal totalPrice;
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;
    @Column(name = "created_at", nullable = false)
    private java.time.LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at", nullable = false)
    private java.time.LocalDateTime updatedAt = LocalDateTime.now();
    @ManyToOne
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
    @Column(name = "updated_by", nullable = false)
    private String updatedBy;
    @Transient
    private String zoneName;

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Zone getZone() {
        return zone;
    }

    public void setZone(Zone zone) {
        this.zone = zone;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Invoices getInvoice() {
        return invoice;
    }

    public void setInvoice(Invoices invoice) {
        this.invoice = invoice;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(boolean deleted) {
        isDeleted = deleted;
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
}
