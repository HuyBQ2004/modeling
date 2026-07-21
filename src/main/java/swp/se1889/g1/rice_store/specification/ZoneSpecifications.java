package swp.se1889.g1.rice_store.specification;
import org.springframework.data.jpa.domain.Specification;
import swp.se1889.g1.rice_store.entity.Invoices;
import swp.se1889.g1.rice_store.entity.Store;
import swp.se1889.g1.rice_store.entity.Zone;

import java.time.LocalDateTime;
import java.util.Date;

public class ZoneSpecifications {
    public static  Specification<Zone> idGreateThan(Long idMin){
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("id"), idMin);
    }
    public static  Specification<Zone> idLessThan(Long idMax){
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("id"), idMax);
    }
    public static  Specification<Zone> nameContains(String name){
        return (root, query, cb) -> cb.like(root.get("name"), "%" + name + "%");
    }
    public static  Specification<Zone> addressContains(String address){
        return (root, query, cb) -> cb.like(root.get("address"), "%" + address + "%");
    }
    public static Specification<Zone> createdAtBefore(Date dateMax){
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), dateMax);
    }
    public static Specification<Zone> createdAtAfter(Date dateMin){
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), dateMin);
    }
    public static Specification<Zone> updatedAtBefore(Date dateMax1){
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("updatedAt"), dateMax1);
    }
    public static Specification<Zone> updatedAtAfter(Date dateMin1){
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("updatedAt"), dateMin1);
    }
    public static Specification<Zone> hasStores(Store store) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("store"), store);
    }
}
