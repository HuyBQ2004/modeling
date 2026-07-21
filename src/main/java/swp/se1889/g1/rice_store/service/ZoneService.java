package swp.se1889.g1.rice_store.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import swp.se1889.g1.rice_store.dto.ProductDTO;
import swp.se1889.g1.rice_store.dto.ZoneDTO;
import swp.se1889.g1.rice_store.entity.Product;
import swp.se1889.g1.rice_store.entity.Store;
import swp.se1889.g1.rice_store.entity.User;
import swp.se1889.g1.rice_store.entity.Zone;
import swp.se1889.g1.rice_store.repository.ProductRepository;
import swp.se1889.g1.rice_store.repository.StoreRepository;
import swp.se1889.g1.rice_store.repository.UserRepository;
import swp.se1889.g1.rice_store.repository.ZoneRepository;
import swp.se1889.g1.rice_store.specification.ZoneSpecifications;


import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ZoneService {
    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductService productService;


    public List<Zone> getAllZones() {
        return zoneRepository.findAll();
    }

    public Zone getZoneById(Long id) {
        return zoneRepository.findByIdAndIsDeletedFalse(id).orElse(null);

    }

    public List<Zone> searchZonesByName(Store store, String name) {
        return zoneRepository.findByNameContainingIgnoreCaseAndStoreAndIsDeletedFalse(name, store);
    }

    public Page<Zone> getZonesByStoreId(Store store, Pageable pageable) {
        return zoneRepository.findByStoreAndIsDeletedFalse(store, pageable);
    }

    public List<Zone> getZone(Store store) {
        return zoneRepository.findByStore(store);
    }

    public Zone createZone(ZoneDTO zoneDTO, Store store) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            throw new RuntimeException("Không tìm thấy thông tin người dùng");
        }

        Zone zone = new Zone();
        zone.setName(zoneDTO.getName());
        zone.setStore(store);
        zone.setAddress(zoneDTO.getAddress());
        zone.setCreatedBy(currentUser);
        zone.setQuantity(0);
        zone.setQuantity(zoneDTO.getQuantity());
        zone.setCreatedAt(LocalDateTime.now());
        zone.setUpdatedAt(LocalDateTime.now());
        zoneRepository.save(zone);
        return zone;
    }

    public Zone updateZone(ZoneDTO zoneDTO, Store store) {
        Zone zone = getZoneById(zoneDTO.getId());
        zone.setName(zoneDTO.getName());
        zone.setAddress(zoneDTO.getAddress());
        zone.setStore(store);
        zone.setUpdatedAt(LocalDateTime.now());
        zoneRepository.save(zone);
        return zone;
    }

    public Zone deleteZone(Long id) {
        Zone zone = getZoneById(id);
        zone.setIsDeleted(true);
        zoneRepository.save(zone);
        return zone;
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetails) {
            String username = ((UserDetails) principal).getUsername();
            return userRepository.findByUsername(username);
        }
        return null;
    }

    public Zone addInventory(Zone zone, Product product, int quantity) {
        if (zone == null || product == null) {
            throw new RuntimeException("Zone hoặc Product không hợp lệ");
        }

        // Nếu kho đã có một sản phẩm nhưng sản phẩm đó bị xóa (isDeleted = true), thay thế bằng sản phẩm mới
        if (zone.getProduct() != null && zone.getProduct().isDeleted()) {
            zone.setProduct(product); // Gán sản phẩm mới vào kho
            zone.setQuantity(quantity); // Đặt lại số lượng mới
        }
        // Nếu kho đang chứa sản phẩm trùng với sản phẩm mới, cộng dồn số lượng
        else if (zone.getProduct() != null && zone.getProduct().getId().equals(product.getId())) {
            zone.setQuantity(zone.getQuantity() + quantity);
        }
        // Nếu kho đã có sản phẩm khác (chưa bị xóa), không cho phép thay đổi sản phẩm
        else if (zone.getProduct() != null) {
            throw new RuntimeException("Kho đã chứa một sản phẩm khác. Không thể thay đổi sản phẩm.");
        }
        // Nếu kho chưa có sản phẩm, thêm sản phẩm mới
        else {
            zone.setProduct(product);
            zone.setQuantity(quantity);

        }

        zone.setCreatedAt(LocalDateTime.now());
        return zoneRepository.save(zone);
    }

    public Page<Zone> getFilter(Store store ,Long idMin, Long idMax, String name, String address, Date dateMin, Date dateMax, Pageable pageable, Date dateMax1, Date dateMin1) {
        Specification<Zone> spec = Specification.where(null);
        if (store != null) {
            spec = spec.and(ZoneSpecifications.hasStores(store));
        }
        if (idMin != null) {
            spec = spec.and(ZoneSpecifications.idGreateThan(idMin));
        }
        if (idMax != null) {
            spec = spec.and(ZoneSpecifications.idLessThan(idMax));
        }
        if (name != null && !name.isEmpty()) {
            spec = spec.and(ZoneSpecifications.nameContains(name));
        }
        if (address != null && !address.isEmpty()) {
            spec = spec.and(ZoneSpecifications.addressContains(address));

        }
        if (dateMin != null) {
            spec = spec.and(ZoneSpecifications.createdAtAfter(dateMin));
        }
        if (dateMax != null) {
            spec = spec.and(ZoneSpecifications.createdAtBefore(dateMax));
        }
        if (dateMin1 != null) {
            spec = spec.and(ZoneSpecifications.updatedAtAfter(dateMin1));
        }
        if (dateMax1 != null) {
            spec = spec.and(ZoneSpecifications.updatedAtBefore(dateMax1));
        }
        return zoneRepository.findAll(spec, pageable);
    }
}
