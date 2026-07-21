package swp.se1889.g1.rice_store.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import swp.se1889.g1.rice_store.dto.ProductDTO;
import swp.se1889.g1.rice_store.dto.ZoneDTO;
import swp.se1889.g1.rice_store.entity.Product;
import swp.se1889.g1.rice_store.entity.Store;
import swp.se1889.g1.rice_store.entity.User;
import swp.se1889.g1.rice_store.entity.Zone;
import swp.se1889.g1.rice_store.repository.ProductRepository;
import swp.se1889.g1.rice_store.service.ProductService;
import swp.se1889.g1.rice_store.service.UserServiceIpml;
import swp.se1889.g1.rice_store.service.ZoneService;

import javax.swing.text.html.Option;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/owner/zone")
public class ZoneController {

    @Autowired
    private ZoneService zoneService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private UserServiceIpml userService;

    @GetMapping
    public String listZones(Model model, HttpSession session,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "5") int size,
                            @RequestParam(required = false) String idMin,
                            @RequestParam(required = false) String idMax,
                            @RequestParam(required = false) String name,
                            @RequestParam(required = false) String address,
                            @RequestParam(required = false) String dateMin,
                            @RequestParam(required = false) String dateMax,
                            @RequestParam(required = false) String dateMax1,
                            @RequestParam(required = false) String dateMin1
    ) {
        Store store = (Store) session.getAttribute("store");
        if (store == null) return "redirect:/login";
        model.addAttribute("store", store);
        Long parsedIdMin = parseLong(idMin);
        Long parsedIdMax = parseLong(idMax);
        Date parsedDateMin = parseDate(dateMin);
        Date parsedDateMax = parseDate(dateMax);
        Date parsedDateMax1 = parseDate(dateMax1);
        Date parsedDateMin1 = parseDate(dateMin1);
        Pageable pageable = PageRequest.of(page, size);
        Page<Zone> zones = zoneService.getFilter(store, parsedIdMin, parsedIdMax, name, address, parsedDateMin, parsedDateMax, pageable, parsedDateMax1, parsedDateMin1);
        model.addAttribute("zones", zones.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", zones.getTotalPages());
        model.addAttribute("totalItems", zones.getTotalElements());
        model.addAttribute("recordsPerPage", size);
        model.addAttribute("idMin", idMin);
        model.addAttribute("idMax", idMax);
        model.addAttribute("name", name);
        model.addAttribute("address", address);
        model.addAttribute("dateMin", dateMin);
        model.addAttribute("dateMax", dateMax);
        model.addAttribute("dateMax1", dateMax1);
        model.addAttribute("dateMin1", dateMin1);
        User user = userService.getCurrentUser();
        model.addAttribute("user", user);
        return "zones";
    }

    private Long parseLong(String value) {
        if (value != null && !value.isEmpty()) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return null; // Ignore invalid input
            }
        }
        return null;
    }

    private Date parseDate(String value) {
        if (value != null && !value.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                return sdf.parse(value);
            } catch (ParseException e) {
                return null; // Ignore invalid input
            }
        }
        return null;
    }

    @GetMapping("/add")
    public String AddZone(Model model, HttpSession session) {
        Store store = (Store) session.getAttribute("store");
        model.addAttribute("store", store);
        model.addAttribute("zone", new Zone());
        User user = userService.getCurrentUser();
        model.addAttribute("user", user);

        return "addZone";
    }

    @PostMapping("/add")
    public String createZone(@ModelAttribute("zone") ZoneDTO zone, HttpSession session, Model model, RedirectAttributes redirectAttributes, Pageable pageable) {
        Store store = (Store) session.getAttribute("store");
        model.addAttribute("store", store);
        Page<Zone> existingZones = zoneService.getZonesByStoreId(store, pageable);
        boolean isDuplicate = existingZones.stream()
                .anyMatch(z -> z.getName().equalsIgnoreCase(zone.getName()));

        if (isDuplicate) {
            redirectAttributes.addFlashAttribute("error", "Tên khu vực đã tồn tại. Vui lòng chọn tên khác.");
            return "redirect:/owner/zone/add"; // Quay lại trang thêm khu vực
        }
        boolean isDuplicate1 = existingZones.stream()
                .anyMatch(z -> z.getAddress().equalsIgnoreCase(zone.getAddress()));

        if (isDuplicate1) {
            redirectAttributes.addFlashAttribute("error", "Địa chỉ khu vực đã tồn tại. Vui lòng chọn tên khác.");
            return "redirect:/owner/zone/add"; // Quay lại trang thêm khu vực
        }

        Zone zone2 = zoneService.createZone(zone, store);
        if (zone2 != null) {
            redirectAttributes.addFlashAttribute("success", "Thêm khu vực thành công");
        }
        return "redirect:/owner/zone";
    }

    @GetMapping("/edit/{id}")
    public String EditZone(Model model, HttpSession session, @PathVariable Long id) {
        Store store = (Store) session.getAttribute("store");
        model.addAttribute("store", store);
        Zone zone = zoneService.getZoneById(id);
        model.addAttribute("zone", zone);
        User user = userService.getCurrentUser();
        model.addAttribute("user", user);
        return "editZone";
    }

    @PostMapping("/edit/{id}")
    public String updateZone(@ModelAttribute("zone") ZoneDTO zone, HttpSession session, Model model, @PathVariable Long id, RedirectAttributes redirectAttributes) {
        Store store = (Store) session.getAttribute("store");
        model.addAttribute("store", store);

        // Lấy danh sách tất cả khu vực trong store
        List<Zone> zones = zoneService.getZone(store);

        // Lấy khu vực cũ
        Zone existingZone = zoneService.getZoneById(id);
        if (existingZone == null) {
            redirectAttributes.addFlashAttribute("error", "Khu vực không tồn tại.");
            return "redirect:/owner/zone";
        }
        // Kiểm tra nếu tên mới đã tồn tại trong danh sách khu vực (trừ chính nó)
        boolean isDuplicate1 = zones.stream()
                .anyMatch(z -> !z.getId().equals(id) && z.getAddress().equalsIgnoreCase(zone.getAddress()));

        if (isDuplicate1) {
            redirectAttributes.addFlashAttribute("error", "Địa chỉ khu vực đã tồn tại. Vui lòng chọn tên khác.");
            return "redirect:/owner/zone/edit/" + id;
        }

        boolean isDuplicate = zones.stream()
                .anyMatch(z -> !z.getId().equals(id) && z.getName().equalsIgnoreCase(zone.getName()));

        if (isDuplicate) {
            redirectAttributes.addFlashAttribute("error", "Tên khu vực đã tồn tại. Vui lòng chọn tên khác.");
            return "redirect:/owner/zone/edit/" + id;
        }


        Zone zone1 = zoneService.updateZone(zone, store);
        if (zone1 != null) {
            redirectAttributes.addFlashAttribute("success", "Sửa khu vực thành công");
        }
        return "redirect:/owner/zone";
    }

    @GetMapping("/delete/{id}")
    public String deleteZone(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Zone zone = zoneService.deleteZone(id);
        if (zone != null) {
            redirectAttributes.addFlashAttribute("success", "Xoá khu vực thành công");
        }
        return "redirect:/owner/zone";
    }

    @GetMapping("/detail/{id}")
    public String detailZone(Model model, HttpSession session, @PathVariable Long id) {
        Store store = (Store) session.getAttribute("store");
        if (store == null) return "redirect:/login";
        Zone zone = zoneService.getZoneById(id);
        ProductDTO productDTO = (zone.getProduct() != null) ? productService.getProductById(zone.getProduct().getId()) : null;
        model.addAttribute("store", store);
        model.addAttribute("zone", zone);
        model.addAttribute("product", productDTO);
        User user = userService.getCurrentUser();
        model.addAttribute("user", user);
        return "zoneDetail";
    }

    @GetMapping("/addInventory")
    public String showAddInventoryForm(Model model, HttpSession session) {
        Store store = (Store) session.getAttribute("store");
        if (store == null) return "redirect:/login";
        // Fetch all zones for the store
        List<Zone> zones = zoneService.getZone(store);
        // Fetch all products (assuming ProductService has a getAllProducts method)
        List<Product> products = productRepository.findAll(); // Adjust based on your ProductService
        model.addAttribute("store", store);
        model.addAttribute("zones", zones);
        model.addAttribute("products", products);
        User user = userService.getCurrentUser();
        model.addAttribute("user", user);
        return "addInventory";
    }

    @PostMapping("/addInventory")
    public String addInventory(
            @RequestParam("zoneId") Long zoneId,
            @RequestParam("productId") Long productId,
            @RequestParam("quantity") int quantity,
            HttpSession session,
            Model model, RedirectAttributes redirectAttributes) {
        Store store = (Store) session.getAttribute("store");
        model.addAttribute("store", store);
        if (store == null) return "redirect:/login";
        Zone zone = zoneService.getZoneById(zoneId);
        if (zone == null) {
            model.addAttribute("error", "Zone không tồn tại");
            return "addInventory"; // Return to form with error
        }
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            model.addAttribute("error", "Sản phẩm không tồn tại");
            return "addInventory"; // Return to form with error
        }
        // Add inventory using a simplified ZoneService method

        if (quantity <= 0) {
            redirectAttributes.addFlashAttribute("error", "Số lượng nhập vào phải lớn hơn 0.");
            return "redirect:/zone/addInventory";
        }
        if (zone.getProduct() != null && !zone.getProduct().getId().equals(product.getId())) {
            if (!zone.getProduct().isDeleted()) {
                redirectAttributes.addFlashAttribute("error", "Kho đã chứa một sản phẩm khác. Không thể thay đổi sản phẩm.");
                return "redirect:/owner/zone/addInventory";
            }
        }

        Zone zone1 = zoneService.addInventory(zone, product, quantity);
        if (zone1 != null) {
            redirectAttributes.addFlashAttribute("success", "Nhập kho thành công");
        }
        return "redirect:/owner/zone";
    }

    // Tìm kiếm khu vực theo tên (dựa theo store từ session)
    @GetMapping("/api/zones/search")
    @ResponseBody
    public List<Map<String, Object>> searchZones(@RequestParam("query") String query, HttpSession session) {
        Store store = (Store) session.getAttribute("store");
        if (store == null) return new ArrayList<>();

        return zoneService.searchZonesByName(store, query).stream()
                .map(zone -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", zone.getId());
                    map.put("text", zone.getName());
                    return map;
                })
                .collect(Collectors.toList());
    }

    // Tìm kiếm sản phẩm theo tên
}