package swp.se1889.g1.rice_store.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import swp.se1889.g1.rice_store.dto.ProductDTO;
import swp.se1889.g1.rice_store.dto.ProductZoneDTO;
import swp.se1889.g1.rice_store.entity.Product;
import swp.se1889.g1.rice_store.entity.Store;
import swp.se1889.g1.rice_store.entity.User;
import swp.se1889.g1.rice_store.service.ProductService;

import jakarta.validation.Valid;
import swp.se1889.g1.rice_store.service.UserServiceIpml;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private UserServiceIpml userService;


    @GetMapping("owner/products")
    public String getProducts(@RequestParam(required = false) String productName,
                              @RequestParam(required = false) String description,
                              @RequestParam(required = false) BigDecimal priceFrom,
                              @RequestParam(required = false) BigDecimal priceTo,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdDate,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate updatedDate,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "5") int size,
                              Model model,
                              HttpSession session) {

        // Gọi service mới
        Page<Product> productPage = productService.filterProductsWithSpec(
                productName, description, priceFrom, priceTo,
                createdDate, updatedDate,
                page, size
        );


        if (!model.containsAttribute("newProduct")) {
            model.addAttribute("newProduct", new ProductDTO());
        }

        Store store = (Store) session.getAttribute("store");
        model.addAttribute("store", store);
        model.addAttribute("products", productPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", productPage.getTotalPages());
        model.addAttribute("totalItems", productPage.getTotalElements());


        model.addAttribute("productName", productName);
        model.addAttribute("description", description);
        model.addAttribute("priceFrom", priceFrom);
        model.addAttribute("priceTo", priceTo);
        model.addAttribute("createdDate", createdDate);
        model.addAttribute("updatedDate", updatedDate);

        model.addAttribute("editProduct", new ProductDTO());

        User user = userService.getCurrentUser();
        model.addAttribute("user", user);

        return "products";
    }

    @PostMapping("/products/add")
    @ResponseBody
    public ResponseEntity<?> addProduct(@Valid @ModelAttribute("newProduct") ProductDTO productDTO,
                                        BindingResult result) {
        if (result.hasErrors()) {
            Map<String, String> errors = new HashMap<>();
            result.getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
            return ResponseEntity.badRequest().body(errors);  // Trả về lỗi dưới dạng JSON
        }

        try {
            productService.createProduct(productDTO);
            return ResponseEntity.ok().body(Collections.singletonMap("message", "Thêm sản phẩm thành công!"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("errorMessage", e.getMessage()));
        }
    }

    @PostMapping("/products/update")
    @ResponseBody
    public ResponseEntity<?> updateProduct(@Valid @ModelAttribute("editProduct") ProductDTO productDTO, BindingResult result) {
        if (result.hasErrors()) {
            Map<String, String> errors = new HashMap<>();
            result.getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
            return ResponseEntity.badRequest().body(errors);  // Trả về lỗi dưới dạng JSON
        }

        try {
            productService.updateProduct(productDTO);
            return ResponseEntity.ok().body(Collections.singletonMap("message", "Cập nhật sản phẩm thành công!"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("errorMessage", e.getMessage()));
        }
    }

    @GetMapping("/delete/{id}")
    public String deleteProduct(@PathVariable Long id,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "5") int size) {
        productService.deleteProduct(id);
        return "redirect:/owner/products?page=" + page + "&size=" + size;
    }

    @GetMapping("/zones")
    public String getProductsWithZones(Model model, HttpSession session,
                                       @RequestParam(required = false) String productName,
                                       @RequestParam(required = false) String description,
                                       @RequestParam(required = false) BigDecimal minPrice,
                                       @RequestParam(required = false) BigDecimal maxPrice,
                                       @RequestParam(required = false) String zoneName,
                                       @RequestParam(required = false) Integer minQuantity,
                                       @RequestParam(required = false) Integer maxQuantity,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "5") int size) {

        Store store = (Store) session.getAttribute("store");
        if (store == null) return "redirect:/login";


        Page<ProductZoneDTO> productWithZones = productService.filterProductZonesWithSpec(
                store.getId(), productName, description, minPrice, maxPrice, zoneName, minQuantity, maxQuantity, page, size
        );

        model.addAttribute("productWithZones", productWithZones.getContent());
        model.addAttribute("store", store);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", productWithZones.getTotalPages());
        model.addAttribute("totalItems", productWithZones.getTotalElements());

        model.addAttribute("productName", productName);
        model.addAttribute("description", description);
        model.addAttribute("minPrice", minPrice);
        model.addAttribute("maxPrice", maxPrice);
        model.addAttribute("zoneName", zoneName);
        model.addAttribute("minQuantity", minQuantity);
        model.addAttribute("maxQuantity", maxQuantity);

        User user = userService.getCurrentUser();
        model.addAttribute("user", user);

        return "sellProducts";
    }


}


