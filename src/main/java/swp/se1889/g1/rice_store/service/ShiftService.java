package swp.se1889.g1.rice_store.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import swp.se1889.g1.rice_store.dto.ShiftDTO;
import swp.se1889.g1.rice_store.entity.Shift;
import swp.se1889.g1.rice_store.repository.ShiftRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShiftService {

    private final ShiftRepository shiftRepository;

    @Autowired
    public ShiftService(ShiftRepository shiftRepository) {
        this.shiftRepository = shiftRepository;
    }

    public List<Shift> getAllShifts() {
        return shiftRepository.findAll();
    }
    public List<Shift> getStoreByCreatedBy(Long storeId) {
        return shiftRepository.findByCreatedBy(storeId.toString());
    }

    public void save(Shift shift) {
        shiftRepository.save(shift);
    }

    public Shift createShift(ShiftDTO shiftDTO, Long storeId) {
        // Kiểm tra nếu mã ca làm việc đã tồn tại
        Shift existingShift = shiftRepository.findByShiftCode(shiftDTO.getShiftCode());
        if (existingShift != null) {
            throw new RuntimeException("Mã ca làm việc đã tồn tại");
        }

        Shift shift = new Shift();
        shift.setShiftCode(shiftDTO.getShiftCode());
        shift.setShiftName(shiftDTO.getShiftName());
        shift.setStartTime(shiftDTO.getStartTime());
        shift.setEndTime(shiftDTO.getEndTime());
        shift.setShiftType(shiftDTO.getShiftType());
        shift.setCreatedAt(LocalDateTime.now());
        shift.setUpdatedAt(LocalDateTime.now());
        shift.setCreatedBy(storeId.toString());
        shift.setUpdatedBy(storeId.toString());
        shift.setDeleted(false);

        return shiftRepository.save(shift);
    }

    public Shift getShiftById(Long id) {
        return shiftRepository.findById(id).orElse(null);
    }

    public boolean deleteShift(Long id, Long storeId) {
        Shift shift = shiftRepository.findById(id).orElse(null);
        if (shift != null && shift.getCreatedBy().equals(storeId.toString())) {

            shiftRepository.delete(shift);
            return true;
        }
        return false;
    }
}