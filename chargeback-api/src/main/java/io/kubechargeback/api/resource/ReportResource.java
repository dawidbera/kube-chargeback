package io.kubechargeback.api.resource;

import io.kubechargeback.api.repository.ReportRepository;
import io.kubechargeback.common.model.AllocationSnapshot;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;

@Path("/api/v1/reports") 
@Produces(MediaType.APPLICATION_JSON)
public class ReportResource {

    @Inject
    ReportRepository repository;

    /**
     * Retrieves allocation data within a time range, grouped by a specific dimension.
     *
     * @param from    the start time (ISO-8601)
     * @param to      the end time (ISO-8601)
     * @param groupBy the dimension to group by (e.g., TEAM, NAMESPACE, APP)
     * @return a response containing the allocation data
     */
    @GET
    @Path("/allocations")
    public Response allocations(@QueryParam("from") String from, 
                                @QueryParam("to") String to,
                                @QueryParam("groupBy") String groupBy) {
        if (from == null || to == null || groupBy == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"Missing params\"}").build();
        }
        try {
            Instant fromInst = Instant.parse(from);
            Instant toInst = Instant.parse(to);
            if (fromInst.isAfter(toInst)) {
                return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"from > to\"}").build();
            }
            return Response.ok(repository.findAllocations(fromInst, toInst, groupBy)).build();
        } catch (Exception e) {
             return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"" + e.getMessage() + "\"}").build();
        }
    }

    /**
     * Exports allocation data to CSV.
     *
     * @param from    the start time (ISO-8601)
     * @param to      the end time (ISO-8601)
     * @param groupBy the dimension to group by
     * @return a CSV file
     */
    @GET
    @Path("/allocations/export")
    @Produces("text/csv")
    public Response exportAllocations(@QueryParam("from") String from,
                                      @QueryParam("to") String to,
                                      @QueryParam("groupBy") String groupBy) {
        if (from == null || to == null || groupBy == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing params").build();
        }
        try {
            Instant fromInst = Instant.parse(from);
            Instant toInst = Instant.parse(to);
            List<AllocationSnapshot> data = repository.findAllocations(fromInst, toInst, groupBy);

            StringBuilder csv = new StringBuilder();
            csv.append("Group Key,CPU (mCPU),Memory (MiB),CPU Cost,Memory Cost,Total Cost\n");
            
            for (AllocationSnapshot s : data) {
                csv.append(String.format("%s,%d,%d,%.4f,%.4f,%.4f\n",
                    s.getGroupKey(),
                    s.getCpuMcpu(),
                    s.getMemMib(),
                    s.getCpuCostUnits(),
                    s.getMemCostUnits(),
                    s.getTotalCostUnits()
                ));
            }

            return Response.ok(csv.toString())
                    .header("Content-Disposition", "attachment; filename=\"allocations.csv\"")
                    .build();
        } catch (Exception e) {
             return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    /**
     * Retrieves the top applications by cost within a time range.
     *
     * @param from  the start time (ISO-8601)
     * @param to    the end time (ISO-8601)
     * @param team  optional team filter
     * @param limit the maximum number of applications to return
     * @return a response containing the top applications
     */
    @GET
    @Path("/top-apps")
    public Response topApps(@QueryParam("from") String from,
                            @QueryParam("to") String to,
                            @QueryParam("team") String team,
                            @QueryParam("limit") @DefaultValue("10") int limit) {
         if (from == null || to == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"Missing params\"}").build();
        }
        try {
            Instant fromInst = Instant.parse(from);
            Instant toInst = Instant.parse(to);
            return Response.ok(repository.findTopApps(fromInst, toInst, team, limit)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"" + e.getMessage() + "\"}").build();
        }
    }

    /**
     * Retrieves compliance data within a time range.
     *
     * @param from the start time (ISO-8601)
     * @param to   the end time (ISO-8601)
     * @return a response containing the compliance data
     */
    @GET
    @Path("/compliance")
    public Response compliance(@QueryParam("from") String from, @QueryParam("to") String to) {
        // Validation could be strict, but for MVP we might just ignore dates if repo ignores them
        try {
            Instant fromInst = from != null ? Instant.parse(from) : null;
            Instant toInst = to != null ? Instant.parse(to) : null;
            return Response.ok(repository.findCompliance(fromInst, toInst)).build();
        } catch (Exception e) {
             return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"" + e.getMessage() + "\"}").build();
        }
    }

    /**
     * Retrieves the recent alert history.
     *
     * @param limit the maximum number of alerts to return
     * @return a list of alerts
     */
    @GET
    @Path("/alerts")
    public Response alerts(@QueryParam("limit") @DefaultValue("50") int limit) {
        try {
            return Response.ok(repository.findAlerts(limit)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"" + e.getMessage() + "\"}").build();
        }
    }
}
