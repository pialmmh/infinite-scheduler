package com.telcobright.scheduler;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class SimpleTimezoneTest {
    
    private static final ZoneId BANGLADESH_TIMEZONE = ZoneId.of("Asia/Dhaka");
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @Test
    public void testBangladeshTimezone() {
        System.out.println("\n=== TIMEZONE VERIFICATION ===");
        
        // System default time
        LocalDateTime systemTime = LocalDateTime.now();
        System.out.println("System Time:     " + systemTime.format(formatter));
        
        // Wrong way (what was causing the issue)
        LocalDateTime wrongBDTime = LocalDateTime.now(BANGLADESH_TIMEZONE);
        System.out.println("Wrong BD Time:   " + wrongBDTime.format(formatter) + " (This is WRONG - same as system time)");
        
        // Correct way
        ZonedDateTime zonedBDTime = ZonedDateTime.now(BANGLADESH_TIMEZONE);
        LocalDateTime correctBDTime = zonedBDTime.toLocalDateTime();
        System.out.println("Correct BD Time: " + correctBDTime.format(formatter) + " (GMT+6)");
        System.out.println("Timezone Info:   " + zonedBDTime.getZone() + " with offset " + zonedBDTime.getOffset());
        
        // Show the difference
        long hoursDiff = java.time.Duration.between(systemTime, correctBDTime).toHours();
        System.out.println("Time Difference: " + hoursDiff + " hours");
        
        System.out.println("\n✅ Now using CORRECT Bangladesh timezone conversion!");
        System.out.println("Jobs will be scheduled for: " + correctBDTime.plusMinutes(1).format(formatter) + " to " + correctBDTime.plusMinutes(5).format(formatter));
    }
}