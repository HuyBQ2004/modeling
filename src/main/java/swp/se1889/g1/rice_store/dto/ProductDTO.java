package swp.se1889.g1.rice_store.dto;

import jakarta.validation.constraints.*;
import swp.se1889.g1.rice_store.entity.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductDTO {

    private Long id;

    @NotBlank(message = "Tên sản phẩm không được để trống")
    @Size(max = 100, message = "Tên sản phẩm không được dài quá 100 ký tự")
    private String name;

    @Size(max = 255, message = "Mô tả không được dài quá 255 ký tự")
    private String description;

    @NotNull(message = "Giá sản phẩm không được để trống")
    @DecimalMin(value = "0.01", inclusive = true, message = "Giá sản phẩm phải lớn hơn 0")
    private BigDecimal price;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Long createdBy;  // ID của người tạo

    // ✅ Constructor không tham số
    public ProductDTO() {
    }

    // ✅ Constructor đầy đủ
    public ProductDTO(Long id, String name, String description, BigDecimal price, Integer quantity,
                      LocalDateTime createdAt, LocalDateTime updatedAt, Long createdBy) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdBy = createdBy;
    }

    // ✅ Constructor chuyển từ Entity → DTO
    public ProductDTO(Product product) {
        this.id = product.getId();
        this.name = product.getName();
        this.description = product.getDescription();
        this.price = product.getPrice();
        this.createdAt = product.getCreatedAt();
        this.updatedAt = product.getUpdatedAt();
        this.createdBy = product.getCreatedBy().getId(); // Lấy ID của User
    }

    public ProductDTO(Long id, String name, String description, BigDecimal price) {
        this.id = id;
        this.name = name;
        this.description = description;
       this.price = price;
    }

    // ✅ Getters và Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }


    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
