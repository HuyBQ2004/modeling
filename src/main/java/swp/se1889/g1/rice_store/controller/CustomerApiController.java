package swp.se1889.g1.rice_store.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import swp.se1889.g1.rice_store.dto.CustomerDTO;
import swp.se1889.g1.rice_store.service.CustomerService;

import java.util.List;

@RestController
public class CustomerApiController {

    @Autowired
    private CustomerService customerService;

    @GetMapping("/api/customers/search")
    public List<CustomerDTO> searchCustomers(@RequestParam(name = "query", defaultValue = "") String query) {
        return customerService.searchCustomers(query);
    }
}
