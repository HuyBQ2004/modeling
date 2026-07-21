package swp.se1889.g1.rice_store.specification;

import org.springframework.data.jpa.domain.Specification;
import swp.se1889.g1.rice_store.entity.Zone;

import java.math.BigDecimal;

public class ZoneSpecificationsForProduct {

    public static Specification<Zone> storeEquals(Long storeId) {
        return (root, query, cb) -> cb.equal(root.get("store").get("id"), storeId);
    }

    public static Specification<Zone> zoneNameContains(String zoneName) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), "%" + zoneName.toLowerCase() + "%");
    }

    public static Specification<Zone> quantityGreaterThan(Integer minQty) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("quantity"), minQty);
    }

    public static Specification<Zone> quantityLessThan(Integer maxQty) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("quantity"), maxQty);
    }

    public static Specification<Zone> joinProductName(String productName) {
        return (root, query, cb) -> {
            var join = root.join("product");
            return cb.like(cb.lower(join.get("name")), "%" + productName.toLowerCase() + "%");
        };
    }

    public static Specification<Zone> joinProductDescription(String description) {
        return (root, query, cb) -> {
            var join = root.join("product");
            return cb.like(cb.lower(join.get("description")), "%" + description.toLowerCase() + "%");
        };
    }

    public static Specification<Zone> joinProductPriceFrom(BigDecimal priceFrom) {
        return (root, query, cb) -> {
            var join = root.join("product");
            return cb.greaterThanOrEqualTo(join.get("price"), priceFrom);
        };
    }

    public static Specification<Zone> joinProductPriceTo(BigDecimal priceTo) {
        return (root, query, cb) -> {
            var join = root.join("product");
            return cb.lessThanOrEqualTo(join.get("price"), priceTo);
        };
    }

    public static Specification<Zone> notDeletedZoneAndProduct() {
        return (root, query, cb) -> {
            var productJoin = root.join("product");
            return cb.and(
                    cb.isFalse(root.get("isDeleted")),
                    cb.isFalse(productJoin.get("isDeleted"))
            );
        };
    }
}
