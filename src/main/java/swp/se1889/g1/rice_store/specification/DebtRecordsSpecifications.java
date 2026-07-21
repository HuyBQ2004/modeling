package swp.se1889.g1.rice_store.specification;

import org.springframework.data.jpa.domain.Specification;
import swp.se1889.g1.rice_store.entity.DebtRecords;

import java.math.BigDecimal;
import java.util.Date;

public class DebtRecordsSpecifications {
    public static Specification<DebtRecords> hasCustomerId(Long customerId) {
        return (root, query, cb) -> cb.equal(root.get("customerId"), customerId);
    }

    public static Specification<DebtRecords> idGreaterThanOrEqual(Long idMin) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("id"), idMin);
    }

    public static Specification<DebtRecords> idLessThanOrEqual(Long idMax) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("id"), idMax);
    }

    public static Specification<DebtRecords> noteContains(String note) {
        return (root, query, cb) -> cb.like(root.get("note"), "%" + note + "%");
    }

    public static Specification<DebtRecords> hasType(String type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<DebtRecords> amountGreaterThanOrEqual(BigDecimal amountMin) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("amount"), amountMin);
    }

    public static Specification<DebtRecords> amountLessThanOrEqual(BigDecimal amountMax) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("amount"), amountMax);
    }

    public static Specification<DebtRecords> createdAtAfter(Date dateMin) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), dateMin);
    }

    public static Specification<DebtRecords> createdAtBefore(Date dateMax) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), dateMax);
    }
    public static Specification<DebtRecords> hasCreateOn(Date dateMin2) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createOn"), dateMin2);
    }
    public static Specification<DebtRecords> hasCreateOn2(Date dateMax2){
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createOn"), dateMax2);
    }
}