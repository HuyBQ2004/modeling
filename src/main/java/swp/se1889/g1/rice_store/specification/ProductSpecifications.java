package swp.se1889.g1.rice_store.specification;

import org.springframework.data.jpa.domain.Specification;
import swp.se1889.g1.rice_store.entity.Product;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ProductSpecifications {

    public static Specification<Product> nameContains(String name) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }

    public static Specification<Product> descriptionContains(String description) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("description")), "%" + description.toLowerCase() + "%");
    }

    public static Specification<Product> priceGreaterThan(BigDecimal minPrice) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    public static Specification<Product> priceLessThan(BigDecimal maxPrice) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    public static Specification<Product> createdDateEqual(LocalDate date) {
        return (root, query, cb) -> cb.between(
                root.get("createdAt"),
                date.atStartOfDay(),
                date.plusDays(1).atStartOfDay()
        );
    }


    public static Specification<Product> updatedDateEqual(LocalDate date) {
        return (root, query, cb) -> cb.between(
                root.get("updatedAt"),
                date.atStartOfDay(),
                date.plusDays(1).atStartOfDay()
        );
    }

    public static Specification<Product> createdBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("createdBy").get("id"), userId);
    }

    public static Specification<Product> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("isDeleted"));
    }
}
