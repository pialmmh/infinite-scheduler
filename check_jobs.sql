-- Infinite Scheduler - Job Status Checker
-- Run this script to check job execution status in MySQL

USE scheduler;

-- 1. Show all active jobs (waiting to execute)
SELECT '=== ACTIVE JOBS (Waiting/Acquired) ===' as '';
SELECT 
    jd.JOB_NAME,
    jd.JOB_GROUP,
    t.TRIGGER_STATE,
    FROM_UNIXTIME(t.NEXT_FIRE_TIME/1000) as NEXT_FIRE_TIME,
    FROM_UNIXTIME(t.PREV_FIRE_TIME/1000) as PREV_FIRE_TIME
FROM qrtz_job_details jd 
LEFT JOIN qrtz_triggers t ON jd.JOB_NAME = t.JOB_NAME 
ORDER BY t.NEXT_FIRE_TIME;

-- 2. Show currently executing jobs
SELECT '=== CURRENTLY EXECUTING JOBS ===' as '';
SELECT 
    ENTRY_ID,
    JOB_NAME,
    JOB_GROUP,
    INSTANCE_NAME,
    FROM_UNIXTIME(FIRED_TIME/1000) as FIRED_TIME,
    STATE
FROM qrtz_fired_triggers 
ORDER BY FIRED_TIME DESC
LIMIT 10;

-- 3. Show scheduler statistics
SELECT '=== SCHEDULER STATUS ===' as '';
SELECT 
    COUNT(*) as TOTAL_ACTIVE_JOBS
FROM qrtz_job_details;

SELECT 
    TRIGGER_STATE,
    COUNT(*) as COUNT
FROM qrtz_triggers 
GROUP BY TRIGGER_STATE;

-- 4. Show scheduler instance status
SELECT '=== SCHEDULER INSTANCES ===' as '';
SELECT 
    INSTANCE_NAME,
    FROM_UNIXTIME(LAST_CHECKIN_TIME/1000) as LAST_CHECKIN_TIME,
    CHECKIN_INTERVAL/1000 as CHECKIN_INTERVAL_SEC
FROM qrtz_scheduler_state;

-- 5. Show recent trigger fire history (if any)
SELECT '=== RECENT TRIGGER ACTIVITY ===' as '';
SELECT 
    JOB_NAME,
    JOB_GROUP,
    FROM_UNIXTIME(FIRED_TIME/1000) as FIRED_TIME,
    STATE
FROM qrtz_fired_triggers 
ORDER BY FIRED_TIME DESC
LIMIT 20;