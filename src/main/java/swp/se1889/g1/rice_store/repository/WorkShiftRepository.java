package swp.se1889.g1.rice_store.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import swp.se1889.g1.rice_store.entity.WorkShift;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface WorkShiftRepository extends JpaRepository<WorkShift, Long> {

    List<WorkShift> findByWorkDateBetween(LocalDate startDate, LocalDate endDate);

    List<WorkShift> findByWorkDate(LocalDate date);

    List<WorkShift> findByEmployee(Long employeeId);

    WorkShift findByEmployeeAndShiftAndWorkDate(Long employeeId, Long shiftId, LocalDate workDate);

}
