package swp.se1889.g1.rice_store.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import swp.se1889.g1.rice_store.entity.Store;
import swp.se1889.g1.rice_store.entity.Zone;
import swp.se1889.g1.rice_store.service.RestoreDataService;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/restore")
public class RestoreDataController {

    @Autowired
    RestoreDataService restoreDataService;

    @GetMapping("get-stores")
    public Page<Store> getAllStores(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return restoreDataService.findAllStore(pageable);
    }

    @GetMapping("get-store/{storeId}")
    public Page<Zone> getZonesByStoreId(
            @PathVariable Long storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return restoreDataService.findZonesByStoreId(storeId, pageable);
    }

    @PutMapping("/restore-before-date")
    public ResponseEntity<String> restoreZonesBeforeDate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime date) {
        int updatedRecords = restoreDataService.updateZonesBeforeDate(date);
        return ResponseEntity.ok("Updated " + updatedRecords + " records successfully.");
    }

    @PutMapping("/{storeId}/status")
    public ResponseEntity<String> updateStoreStatus(@PathVariable Long storeId, @RequestParam boolean isDeleted) {
        boolean isUpdated = restoreDataService.updateStoreStatus(storeId, isDeleted);
        if (isUpdated) {
            return ResponseEntity.ok("Store status updated successfully.");
        } else {
            return ResponseEntity.badRequest().body("Failed to update store status.");
        }
    }


}
