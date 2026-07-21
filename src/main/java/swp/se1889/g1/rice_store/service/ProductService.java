
package swp.se1889.g1.rice_store.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import swp.se1889.g1.rice_store.dto.ProductDTO;
import swp.se1889.g1.rice_store.dto.ProductZoneDTO;
import swp.se1889.g1.rice_store.entity.Product;
import swp.se1889.g1.rice_store.entity.Store;
import swp.se1889.g1.rice_store.entity.User;
import swp.se1889.g1.rice_store.entity.Zone;
import swp.se1889.g1.rice_store.repository.ProductRepository;
import swp.se1889.g1.rice_store.repository.UserRepository;
import swp.se1889.g1.rice_store.repository.ZoneRepository;
import swp.se1889.g1.rice_store.specification.ProductSpecifications;
import swp.se1889.g1.rice_store.specification.ZoneSpecificationsForProduct;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private UserRepository userRepository;


    public Product getProductToDelete(Long id) {
        return productRepository.findById(id).orElse(null);
    }

    public ProductDTO getProductById(Long id) {
        Optional<Product> productOpt = productRepository.findById(id);
        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            return new ProductDTO(product);
        }
        throw new RuntimeException("Không tìm thấy sản phẩm có ID: " + id);
    }

    public void createProduct(ProductDTO productDTO) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            throw new RuntimeException("Không thể xác định người dùng hiện tại.");
        }

        if (productDTO.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Giá sản phẩm phải lớn hơn 0.");
        }

        boolean isProductNameExists = productRepository.existsByCreatedByAndName(currentUser, productDTO.getName());
        if (isProductNameExists) {
            throw new RuntimeException("Sản phẩm với tên này đã tồn tại.");
        }


        Product product = new Product();
        product.setName(productDTO.getName());
        product.setDescription(productDTO.getDescription());
        product.setPrice(productDTO.getPrice());
        product.setCreatedBy(currentUser);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());

        productRepository.save(product);
    }

    public void updateProduct(ProductDTO productDTO) {
        Optional<Product> productOpt = productRepository.findById(productDTO.getId());

        if (productOpt.isPresent()) {
            Product product = productOpt.get();

            boolean isDuplicateName = productRepository.existsByCreatedByAndNameAndIdNot(
                    product.getCreatedBy(), productDTO.getName(), productDTO.getId()
            );

            if (isDuplicateName) {
                throw new RuntimeException("Tên sản phẩm đã tồn tại, vui lòng chọn tên khác.");
            }


            if (productDTO.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Giá sản phẩm phải lớn hơn 0.");
            }

            product.setName(productDTO.getName());
            product.setDescription(productDTO.getDescription());
            product.setPrice(productDTO.getPrice());
            product.setUpdatedAt(LocalDateTime.now());


            User currentUser = getCurrentUser();
            if (currentUser != null) {
                product.setUpdatedBy(currentUser.getUsername());
            }

            productRepository.save(product);
        } else {
            throw new RuntimeException("Không tìm thấy sản phẩm để cập nhật!");
        }
    }


    public void deleteProduct(Long id) {
        Product product = getProductToDelete(id);
        product.setDeleted(true);
        productRepository.save(product);
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetails) {
            String username = ((UserDetails) principal).getUsername();
            return userRepository.findByUsername(username);
        }
        return null;
    }


    public List<ProductZoneDTO> searchProducts(String query) {
        return zoneRepository.searchProductZoneDetails(query);
    }

    public Product findProductById(Long id) {
        return productRepository.findById(id).get();
    }


    public Page<Product> filterProductsWithSpec(String name,
                                                String description,
                                                BigDecimal priceFrom,
                                                BigDecimal priceTo,
                                                LocalDate createdDate,
                                                LocalDate updatedDate,
                                                int page, int size) {
        User currentUser = getCurrentUser();

        Specification<Product> spec = Specification.where(ProductSpecifications.notDeleted())
                .and(ProductSpecifications.createdBy(currentUser.getId()));

        if (name != null && !name.isEmpty()) {
            spec = spec.and(ProductSpecifications.nameContains(name));
        }
        if (description != null && !description.isEmpty()) {
            spec = spec.and(ProductSpecifications.descriptionContains(description));
        }
        if (priceFrom != null) {
            spec = spec.and(ProductSpecifications.priceGreaterThan(priceFrom));
        }
        if (priceTo != null) {
            spec = spec.and(ProductSpecifications.priceLessThan(priceTo));
        }
        if (createdDate != null) {
            spec = spec.and(ProductSpecifications.createdDateEqual(createdDate));
        }
        if (updatedDate != null) {
            spec = spec.and(ProductSpecifications.updatedDateEqual(updatedDate));
        }

        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findAll(spec, pageable);
    }
    public Page<ProductZoneDTO> filterProductZonesWithSpec(Long storeId,
                                                           String productName,
                                                           String description,
                                                           BigDecimal minPrice,
                                                           BigDecimal maxPrice,
                                                           String zoneName,
                                                           Integer minQuantity,
                                                           Integer maxQuantity,
                                                           int page, int size) {

        Specification<Zone> spec = Specification.where(ZoneSpecificationsForProduct.storeEquals(storeId))
                .and(ZoneSpecificationsForProduct.notDeletedZoneAndProduct());

        if (productName != null && !productName.isBlank())
            spec = spec.and(ZoneSpecificationsForProduct.joinProductName(productName));

        if (description != null && !description.isBlank())
            spec = spec.and(ZoneSpecificationsForProduct.joinProductDescription(description));

        if (minPrice != null)
            spec = spec.and(ZoneSpecificationsForProduct.joinProductPriceFrom(minPrice));

        if (maxPrice != null)
            spec = spec.and(ZoneSpecificationsForProduct.joinProductPriceTo(maxPrice));

        if (zoneName != null && !zoneName.isBlank())
            spec = spec.and(ZoneSpecificationsForProduct.zoneNameContains(zoneName));

        if (minQuantity != null)
            spec = spec.and(ZoneSpecificationsForProduct.quantityGreaterThan(minQuantity));

        if (maxQuantity != null)
            spec = spec.and(ZoneSpecificationsForProduct.quantityLessThan(maxQuantity));

        Pageable pageable = PageRequest.of(page, size);
        Page<Zone> zonePage = zoneRepository.findAll(spec, pageable);

        return zonePage.map(zone -> {
            var product = zone.getProduct();
            return new ProductZoneDTO(
                    product.getId(),
                    product.getName(),
                    product.getDescription(),
                    zone.getId(),
                    zone.getName(),
                    product.getPrice(),
                    zone.getQuantity()
            );
        });
    }


}