-- Track All Jobs - Processed vs Not Processed
-- Run this to see current job status in Quartz

USE scheduler;

-- 1. CURRENT JOB STATUS (What's in Quartz right now)
SELECT '=== CURRENT JOB STATUS ===' as '';

-- Active jobs (not yet processed)
SELECT 
    'NOT PROCESSED' as STATUS,
    jd.JOB_NAME,
    jd.JOB_GROUP,
    t.TRIGGER_STATE,
    FROM_UNIXTIME(t.NEXT_FIRE_TIME/1000) as SCHEDULED_TIME,
    CASE 
        WHEN t.TRIGGER_STATE = 'WAITING' THEN 'Waiting for execution time'
        WHEN t.TRIGGER_STATE = 'ACQUIRED' THEN 'About to execute'
        WHEN t.TRIGGER_STATE = 'EXECUTING' THEN 'Currently executing'
        ELSE t.TRIGGER_STATE
    END as DESCRIPTION
FROM qrtz_job_details jd 
JOIN qrtz_triggers t ON jd.JOB_NAME = t.JOB_NAME 
ORDER BY t.NEXT_FIRE_TIME;

-- Currently executing jobs
SELECT '=== CURRENTLY EXECUTING ===' as '';
SELECT 
    'PROCESSING' as STATUS,
    JOB_NAME,
    JOB_GROUP,
    FROM_UNIXTIME(FIRED_TIME/1000) as STARTED_AT,
    'Currently running' as DESCRIPTION
FROM qrtz_fired_triggers
ORDER BY FIRED_TIME DESC;

-- Jobs that executed recently (have PREV_FIRE_TIME)
SELECT '=== RECENTLY EXECUTED ===' as '';
SELECT 
    'PROCESSED' as STATUS,
    jd.JOB_NAME,
    jd.JOB_GROUP,
    FROM_UNIXTIME(t.PREV_FIRE_TIME/1000) as EXECUTED_AT,
    'Completed and removed from queue' as DESCRIPTION
FROM qrtz_job_details jd 
JOIN qrtz_triggers t ON jd.JOB_NAME = t.JOB_NAME 
WHERE t.PREV_FIRE_TIME IS NOT NULL
ORDER BY t.PREV_FIRE_TIME DESC;

-- Summary counts
SELECT '=== SUMMARY ===' as '';
SELECT 
    COUNT(*) as TOTAL_ACTIVE_JOBS,
    SUM(CASE WHEN t.TRIGGER_STATE = 'WAITING' THEN 1 ELSE 0 END) as WAITING_TO_EXECUTE,
    SUM(CASE WHEN t.TRIGGER_STATE = 'ACQUIRED' THEN 1 ELSE 0 END) as ABOUT_TO_EXECUTE,
    SUM(CASE WHEN t.PREV_FIRE_TIME IS NOT NULL THEN 1 ELSE 0 END) as EXECUTED_RECENTLY
FROM qrtz_job_details jd 
LEFT JOIN qrtz_triggers t ON jd.JOB_NAME = t.JOB_NAME;

SELECT 
    (SELECT COUNT(*) FROM qrtz_fired_triggers) as CURRENTLY_EXECUTING;