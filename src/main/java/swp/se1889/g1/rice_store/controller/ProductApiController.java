package swp.se1889.g1.rice_store.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import swp.se1889.g1.rice_store.dto.ProductZoneDTO;
import swp.se1889.g1.rice_store.service.ProductService;

import java.util.List;

@RestController
public class ProductApiController {

    @Autowired
    private ProductService productService;
    
    @GetMapping("/api/products/search")
    public List<ProductZoneDTO> searchProducts(@RequestParam(name = "query", defaultValue = "") String query) {
        return productService.searchProducts(query);
    }
}
