package swp.se1889.g1.rice_store.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface DebtRecordDTO {
    Long getId();

    BigDecimal getAmount();

    LocalDateTime getCreateOn();
}