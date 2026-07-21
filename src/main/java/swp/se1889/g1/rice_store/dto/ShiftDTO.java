package swp.se1889.g1.rice_store.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public class ShiftDTO {
    @NotNull(message = "Mã ca làm việc không được để trống")
    @Size(min = 1, max = 20, message = "Mã ca làm việc phải có độ dài từ 1 đến 20 ký tự")
    private String shiftCode;

    @NotNull(message = "Tên ca làm việc không được để trống")
    @Size(min = 1, max = 50, message = "Tên ca làm việc phải có độ dài từ 1 đến 50 ký tự")
    private String shiftName;

    @NotNull(message = "Giờ bắt đầu không được để trống")
    private LocalTime startTime;

    @NotNull(message = "Giờ kết thúc không được để trống")
    private LocalTime endTime;

    @NotNull(message = "Loại ca làm việc không được để trống")
    private String shiftType;
    // Constructors
    public ShiftDTO() {
    }

    public ShiftDTO(String shiftCode, String shiftName, LocalTime startTime, LocalTime endTime, String shiftType) {
        this.shiftCode = shiftCode;
        this.shiftName = shiftName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.shiftType = shiftType;
    }

    // Getters and setters
    public String getShiftCode() {
        return shiftCode;
    }

    public void setShiftCode(String shiftCode) {
        this.shiftCode = shiftCode;
    }

    public String getShiftName() {
        return shiftName;
    }

    public void setShiftName(String shiftName) {
        this.shiftName = shiftName;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getShiftType() {
        return shiftType;
    }

    public void setShiftType(String shiftType) {
        this.shiftType = shiftType;
    }
}