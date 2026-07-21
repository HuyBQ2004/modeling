package swp.se1889.g1.rice_store.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "work_shifts")
public class WorkShift {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id")
    private Long employee;

    @Column(name = "shift_id")
    private Long shift;

    @Column(name = "work_date")
    private LocalDate workDate;

    @Column(name = "scheduled_start_time")
    private LocalDateTime scheduledStartTime;

    @Column(name = "scheduled_end_time")
    private LocalDateTime scheduledEndTime;

    @Column(name = "notes")
    private String note;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    public WorkShift() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEmployee() {
        return employee;
    }

    public void setEmployee(Long employee) {
        this.employee = employee;
    }

    public Long getShift() {
        return shift;
    }

    public void setShift(Long shift) {
        this.shift = shift;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public LocalDateTime getScheduledStartTime() {
        return scheduledStartTime;
    }

    public void setScheduledStartTime(LocalDateTime scheduledStartTime) {
        this.scheduledStartTime = scheduledStartTime;
    }

    public LocalDateTime getScheduledEndTime() {
        return scheduledEndTime;
    }

    public void setScheduledEndTime(LocalDateTime scheduledEndTime) {
        this.scheduledEndTime = scheduledEndTime;
    }

    // Getters, setters, constructors

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
