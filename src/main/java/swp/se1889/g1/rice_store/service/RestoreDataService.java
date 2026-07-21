package swp.se1889.g1.rice_store.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swp.se1889.g1.rice_store.entity.Store;
import swp.se1889.g1.rice_store.entity.Zone;
import swp.se1889.g1.rice_store.repository.StoreRepository;
import swp.se1889.g1.rice_store.repository.ZoneRepository;

import java.time.LocalDateTime;

@Service
public class RestoreDataService {

    @Autowired
    ZoneRepository zoneRepository;

    @Autowired
    StoreRepository storeRepository;

    @Transactional
    public boolean updateStoreStatus(Long storeId, boolean isDeleted) {
        int updatedRows = storeRepository.updateStoreStatus(storeId, isDeleted);
        return updatedRows > 0;
    }

    public Page<Store> findAllStore(Pageable pageable){
        return  storeRepository.findAll(pageable);
    }

    public Page<Zone> findZonesByStoreId(Long storeId, Pageable pageable) {
        return zoneRepository.findByStoreId(storeId, pageable);
    }

    public int updateZonesBeforeDate(LocalDateTime date) {
        return zoneRepository.updateIsDeletedBeforeDate(date);
    }


}
