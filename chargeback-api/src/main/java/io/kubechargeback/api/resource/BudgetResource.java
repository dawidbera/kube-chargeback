package io.kubechargeback.api.resource;

import io.kubechargeback.api.repository.BudgetRepository;
import io.kubechargeback.common.model.Budget;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;

@Path("/api/v1/budgets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BudgetResource {

    @Inject
    BudgetRepository repository;

    /**
     * Lists all budgets.
     *
     * @return a list of all budgets
     */
    @GET
    public List<Budget> list() {
        return repository.findAll();
    }

    /**
     * Creates a new budget.
     *
     * @param budget the budget to create
     * @return a response indicating the result of the creation
     */
    @POST
    public Response create(Budget budget) {
        if (budget.getName() == null || budget.getSelectorType() == null || budget.getPeriod() == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\": \"Missing required fields\"}").build();
        }
        if (!"TEAM".equals(budget.getSelectorType()) && !"NAMESPACE".equals(budget.getSelectorType())) {
             return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\": \"selectorType LABEL is not supported in MVP\"}").build();
        }

        repository.create(budget);
        return Response.created(URI.create("/api/v1/budgets/" + budget.getId())).entity(budget).build();
    }

    /**
     * Retrieves a specific budget by ID.
     *
     * @param id the budget ID
     * @return a response containing the budget or a 404 if not found
     */
    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") String id) {
        return repository.findById(id)
                .map(b -> Response.ok(b).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Updates an existing budget.
     *
     * @param id     the budget ID
     * @param budget the updated budget data
     * @return a response containing the updated budget or a 404 if not found
     */
    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") String id, Budget budget) {
        if (repository.findById(id).isEmpty()) {
             return Response.status(Response.Status.NOT_FOUND).build();
        }
        budget.setId(id);
        repository.update(budget);
        return Response.ok(budget).build();
    }

    /**
     * Deletes a specific budget by ID.
     *
     * @param id the budget ID
     * @return a response indicating the result of the deletion
     */
    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        repository.delete(id);
        return Response.noContent().build();
    }
}
