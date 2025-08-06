-- COMPREHENSIVE JOB TRACKING - All Jobs Processed or Not
-- This shows COMPLETE job lifecycle tracking

USE scheduler;

-- 1. ALL JOBS FROM HISTORY TABLE (Complete tracking)
SELECT '=== ALL JOBS - COMPLETE HISTORY ===' as '';
SELECT 
    job_id,
    job_name,
    entity_id,
    scheduled_time,
    status,
    started_at,
    completed_at,
    CASE 
        WHEN status = 'SCHEDULED' THEN 'NOT PROCESSED (Waiting)'
        WHEN status = 'STARTED' THEN 'PROCESSING (Running)'
        WHEN status = 'COMPLETED' THEN 'PROCESSED (Success)'
        WHEN status = 'FAILED' THEN 'PROCESSED (Failed)'
    END as PROCESSING_STATUS,
    execution_duration_ms,
    error_message,
    created_at
FROM job_execution_history 
ORDER BY created_at DESC;

-- 2. SUMMARY BY STATUS
SELECT '=== PROCESSING SUMMARY ===' as '';
SELECT 
    status,
    COUNT(*) as COUNT,
    CASE 
        WHEN status IN ('COMPLETED') THEN 'PROCESSED'
        WHEN status IN ('FAILED') THEN 'PROCESSED (with errors)'
        WHEN status IN ('STARTED') THEN 'CURRENTLY PROCESSING'
        WHEN status IN ('SCHEDULED') THEN 'NOT YET PROCESSED'
    END as CATEGORY
FROM job_execution_history 
GROUP BY status
ORDER BY 
    CASE status 
        WHEN 'SCHEDULED' THEN 1
        WHEN 'STARTED' THEN 2 
        WHEN 'COMPLETED' THEN 3
        WHEN 'FAILED' THEN 4
    END;

-- 3. JOBS BY PROCESSING STATUS
SELECT '=== JOBS NOT YET PROCESSED ===' as '';
SELECT job_id, entity_id, scheduled_time, created_at
FROM job_execution_history 
WHERE status = 'SCHEDULED'
ORDER BY scheduled_time;

SELECT '=== JOBS CURRENTLY PROCESSING ===' as '';
SELECT job_id, entity_id, scheduled_time, started_at, 
       TIMESTAMPDIFF(SECOND, started_at, NOW()) as running_seconds
FROM job_execution_history 
WHERE status = 'STARTED'
ORDER BY started_at;

SELECT '=== JOBS SUCCESSFULLY PROCESSED ===' as '';
SELECT job_id, entity_id, scheduled_time, completed_at, execution_duration_ms
FROM job_execution_history 
WHERE status = 'COMPLETED'
ORDER BY completed_at DESC;

SELECT '=== JOBS FAILED PROCESSING ===' as '';
SELECT job_id, entity_id, scheduled_time, completed_at, error_message
FROM job_execution_history 
WHERE status = 'FAILED'
ORDER BY completed_at DESC;

-- 4. PERFORMANCE METRICS
SELECT '=== PERFORMANCE METRICS ===' as '';
SELECT 
    COUNT(*) as TOTAL_JOBS,
    AVG(execution_duration_ms) as AVG_EXECUTION_MS,
    MIN(execution_duration_ms) as MIN_EXECUTION_MS,
    MAX(execution_duration_ms) as MAX_EXECUTION_MS,
    SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as SUCCESS_COUNT,
    SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as FAILED_COUNT,
    ROUND(
        (SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) * 100.0 / 
         SUM(CASE WHEN status IN ('COMPLETED', 'FAILED') THEN 1 ELSE 0 END)), 2
    ) as SUCCESS_RATE_PERCENT
FROM job_execution_history
WHERE status IN ('COMPLETED', 'FAILED');

-- 5. TIMELINE VIEW (Last 24 hours)
SELECT '=== TIMELINE - LAST 24 HOURS ===' as '';
SELECT 
    DATE_FORMAT(created_at, '%Y-%m-%d %H:%i') as TIME_SLOT,
    COUNT(*) as JOBS_SCHEDULED,
    SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as COMPLETED,
    SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as FAILED,
    SUM(CASE WHEN status = 'SCHEDULED' THEN 1 ELSE 0 END) as PENDING
FROM job_execution_history 
WHERE created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d %H:%i')
ORDER BY TIME_SLOT DESC
LIMIT 20;