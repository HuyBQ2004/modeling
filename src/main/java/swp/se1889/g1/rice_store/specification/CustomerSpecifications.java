package swp.se1889.g1.rice_store.specification;

import org.springframework.data.jpa.domain.Specification;
import swp.se1889.g1.rice_store.entity.Customer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class CustomerSpecifications {

    public static Specification<Customer> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("isDeleted"));
    }

    public static Specification<Customer> createdBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("createdBy").get("id"), userId);
    }

    public static Specification<Customer> idEquals(Long id) {
        return (root, query, cb) -> cb.equal(root.get("id"), id);
    }

    public static Specification<Customer> nameContains(String name) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }

    public static Specification<Customer> phoneContains(String phone) {
        return (root, query, cb) -> cb.like(root.get("phone"), "%" + phone + "%");
    }

    public static Specification<Customer> addressContains(String address) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("address")), "%" + address.toLowerCase() + "%");
    }

    public static Specification<Customer> emailContains(String email) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("email")), "%" + email.toLowerCase() + "%");
    }

    public static Specification<Customer> debtEquals(BigDecimal debt) {
        return (root, query, cb) -> cb.equal(root.get("debtBalance"), debt);
    }

    public static Specification<Customer> createdAtBetween(LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> cb.between(root.get("createdAt"), from, to);
    }

    public static Specification<Customer> updatedAtBetween(LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> cb.between(root.get("updatedAt"), from, to);
    }

    public static Specification<Customer> createdByIn(List<Long> ids) {
        return (root, query, cb) -> root.get("createdBy").get("id").in(ids);
    }
}
