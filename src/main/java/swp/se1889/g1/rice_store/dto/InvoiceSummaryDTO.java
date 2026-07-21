package swp.se1889.g1.rice_store.dto;


import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Interface Projection: Spring Data JPA tự động map dữ liệu vào đây.
 * Nhanh, gọn, nhẹ hơn Entity rất nhiều.
 */
public interface InvoiceSummaryDTO {
    Long getId();

    BigDecimal getFinalAmount();

    LocalDateTime getCreatedAt();

}
