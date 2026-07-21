package swp.se1889.g1.rice_store.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import swp.se1889.g1.rice_store.entity.Shift;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ShiftRepository extends JpaRepository<Shift, Long> {
    Shift findByShiftCode(String shiftCode);
    List<Shift> findByCreatedBy(String createdBy);
}
