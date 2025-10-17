package com.telcobright.scheduler.resource;

import com.telcobright.scheduler.InfiniteSchedulerService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * REST API for scheduling jobs.
 * Provides endpoints for scheduling single jobs, batch jobs, and querying job status.
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SchedulerResource {

    private static final Logger logger = LoggerFactory.getLogger(SchedulerResource.class);

    @Inject
    InfiniteSchedulerService schedulerService;

    /**
     * Schedule a single job.
     *
     * @param job Job data as Map with required fields: id, scheduledTime, jobType
     * @return Response with job scheduling status
     */
    @POST
    @Path("/schedule")
    public Response scheduleJob(Map<String, Object> job) {
        try {
            logger.info("Received request to schedule job: {}", job.get("id"));

            schedulerService.scheduleJob(job);

            return Response.ok(Map.of(
                "success", true,
                "jobId", job.get("id"),
                "scheduledTime", job.get("scheduledTime"),
                "message", "Job scheduled successfully"
            )).build();

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid job data: {}", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of(
                    "success", false,
                    "message", e.getMessage()
                ))
                .build();

        } catch (Exception e) {
            logger.error("Failed to schedule job", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of(
                    "success", false,
                    "message", "Failed to schedule job: " + e.getMessage()
                ))
                .build();
        }
    }

    /**
     * Schedule multiple jobs in batch.
     *
     * @param jobs List of job data maps
     * @return Response with batch scheduling status
     */
    @POST
    @Path("/schedule/batch")
    public Response scheduleJobs(List<Map<String, Object>> jobs) {
        try {
            logger.info("Received request to schedule {} jobs", jobs.size());

            int scheduled = schedulerService.scheduleJobs(jobs);

            return Response.ok(Map.of(
                "success", true,
                "scheduled", scheduled,
                "failed", jobs.size() - scheduled,
                "message", String.format("Scheduled %d out of %d jobs successfully", scheduled, jobs.size())
            )).build();

        } catch (Exception e) {
            logger.error("Failed to schedule jobs", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of(
                    "success", false,
                    "message", "Failed to schedule jobs: " + e.getMessage()
                ))
                .build();
        }
    }

    /**
     * Get job status by ID.
     *
     * @param jobId Job ID
     * @return Response with job details
     */
    @GET
    @Path("/jobs/{jobId}")
    public Response getJobStatus(@PathParam("jobId") String jobId) {
        try {
            logger.info("Received request for job status: {}", jobId);

            Map<String, Object> jobStatus = schedulerService.getJobStatus(jobId);

            if (jobStatus == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of(
                        "success", false,
                        "message", "Job not found: " + jobId
                    ))
                    .build();
            }

            return Response.ok(jobStatus).build();

        } catch (Exception e) {
            logger.error("Failed to get job status", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of(
                    "success", false,
                    "message", "Failed to get job status: " + e.getMessage()
                ))
                .build();
        }
    }

    /**
     * Health check endpoint.
     *
     * @return Response with scheduler status
     */
    @GET
    @Path("/health")
    public Response health() {
        return Response.ok(Map.of(
            "status", "UP",
            "service", "Infinite Scheduler"
        )).build();
    }
}
