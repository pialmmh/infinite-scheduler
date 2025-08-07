package com.telcobright.scheduler.examples;

import com.telcobright.db.annotation.*;
import com.telcobright.scheduler.SchedulableEntity;

import java.time.LocalDateTime;

@Table(name = "sms_schedules")
public class SmsEntity implements SchedulableEntity<Long> {
    
    @Id
    @Column(name = "id")
    private Long id;
    
    @ShardingKey
    @Column(name = "scheduled_time")
    private LocalDateTime scheduledTime;
    
    @Column(name = "phone_number")
    private String phoneNumber;
    
    @Column(name = "message")
    private String message;
    
    @Column(name = "status")
    private String status;
    
    @Column(name = "scheduled")
    private Boolean scheduled;
    
    public SmsEntity() {}
    
    public SmsEntity(Long id, LocalDateTime scheduledTime, String phoneNumber, String message) {
        this.id = id;
        this.scheduledTime = scheduledTime;
        this.phoneNumber = phoneNumber;
        this.message = message;
        this.status = "PENDING";
        this.scheduled = false; // Initially not scheduled
    }
    
    @Override
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    @Override
    public LocalDateTime getScheduledTime() {
        return scheduledTime;
    }
    
    public void setScheduledTime(LocalDateTime scheduledTime) {
        this.scheduledTime = scheduledTime;
    }
    
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    
    @Override
    public Boolean getScheduled() {
        return scheduled;
    }
    
    @Override
    public void setScheduled(Boolean scheduled) {
        this.scheduled = scheduled;
    }
    
    @Override
    public String toString() {
        return "SmsEntity{" +
                "id=" + id +
                ", scheduledTime=" + scheduledTime +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", message='" + message + '\'' +
                ", status='" + status + '\'' +
                ", scheduled=" + scheduled +
                '}';
    }
}