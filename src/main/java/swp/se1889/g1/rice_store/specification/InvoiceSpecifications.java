package swp.se1889.g1.rice_store.specification;

import org.springframework.data.jpa.domain.Specification;
import swp.se1889.g1.rice_store.entity.DebtRecords;
import swp.se1889.g1.rice_store.entity.Invoices;
import swp.se1889.g1.rice_store.entity.Store;
import swp.se1889.g1.rice_store.entity.Zone;

import java.math.BigDecimal;
import java.util.Date;

public class InvoiceSpecifications {
    public static Specification<Invoices> idGreatThan(Long idMin) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("id"), idMin);
    }

    public static Specification<Invoices> idLessThan(Long idMax) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("id"), idMax);
    }

    public static Specification<Invoices> noteContains(String note) {
        return (root, query, cb) -> cb.like(root.get("note"), "%" + note + "%");
    }

    public static Specification<Invoices> hasStatus(String status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Invoices> amountGreaterThanOrEqual(BigDecimal amountMin) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("totalPrice"), amountMin);
    }

    public static Specification<Invoices> amountLessThanOrEqual(BigDecimal amountMax) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("totalPrice"), amountMax);
    }

    public static Specification<Invoices> createdAtBefore(Date dateMax) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), dateMax);
    }

    public static Specification<Invoices> createdAtAfter(Date dateMin) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), dateMin);
    }

    public static Specification<Invoices> updatedAtBefore(Date dateMax1) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("updatedAt"), dateMax1);
    }

    public static Specification<Invoices> updatedAtAfter(Date dateMin1) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("updatedAt"), dateMin1);
    }

    public static Specification<Invoices> hasStore(Store store) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("store"), store);
    }

    public static Specification<Invoices> hasType(Invoices.InvoiceType type) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("type"), type.Purchase);
    }
}
