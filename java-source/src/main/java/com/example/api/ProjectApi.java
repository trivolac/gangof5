package com.example.api;

import com.example.flow.AllocationFlow;
import com.example.flow.AllocationUpdateFlow;
import com.example.state.AllocationState;
import com.example.state.ProjectState;
import com.example.util.ResponseUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.FlowProgressHandle;
import net.corda.core.node.NodeInfo;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.Response.Status.*;

/**
 * Created by Shailendra on 17-01-2018.
 */
@Path("project")
public class ProjectApi {
    private final CordaRPCOps rpcOps;
    private final CordaX500Name myLegalName;

    private final List<String> serviceNames = ImmutableList.of("Controller", "Network Map Service");

    private final String platformLeadServiceNamePrefix = "PL";
    private final String deliveryTeamServiceNamePrefix = "DL";

    static private final Logger logger = LoggerFactory.getLogger(ProjectApi.class);

    public ProjectApi(CordaRPCOps rpcOps) {
        this.rpcOps = rpcOps;
        this.myLegalName = rpcOps.nodeInfo().getLegalIdentities().get(0).getName();
    }

    /**
     * Returns the node's name.
     */
    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, CordaX500Name> whoami() {
        return ImmutableMap.of("me", myLegalName);
    }

    /**
     * Displays all Project states that exist in the node's vault.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<ProjectState>> getProjects() {
        return rpcOps.vaultQuery(ProjectState.class).getStates();
    }

    @GET
    @Path("code/{projectCode}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProjectByProjectCode(@PathParam("projectCode") String projectCode) {
        QueryCriteria criteria = new QueryCriteria.LinearStateQueryCriteria(
                null, null, Vault.StateStatus.UNCONSUMED, null);
        List<StateAndRef<ProjectState>> projects = rpcOps.vaultQueryByCriteria(criteria, ProjectState.class).getStates();
        for(StateAndRef<ProjectState> projectStateStateAndRef : projects){
            ProjectState projectState = projectStateStateAndRef.getState().getData();
            if(projectCode.equals(projectState.getProjectCode())){
                return Response.status(OK).entity(projectStateStateAndRef).build();
            }
        }
        return Response.status(NOT_FOUND).entity("Project with specified project code not found.\n").build();
    }

    @GET
    @Path("all-projects")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<ProjectState>> getAllProjects() {
        QueryCriteria criteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL);
        return rpcOps.vaultQueryByCriteria(criteria, ProjectState.class).getStates();
    }

    @GET
    @Path("all-projects-id")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<ProjectState>> getAllProjectsById(@QueryParam("id") String id) {
        UniqueIdentifier projectLinearId = UniqueIdentifier.Companion.fromString(id);
        QueryCriteria criteria = new QueryCriteria.LinearStateQueryCriteria(
                null, ImmutableList.of(projectLinearId), Vault.StateStatus.ALL, null);
        return rpcOps.vaultQueryByCriteria(criteria, ProjectState.class).getStates();
    }

    @GET
    @Path("unconsumed-projects-id")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<ProjectState>> getProjectById(@QueryParam("id") String id) {
        UniqueIdentifier projectLinearId = UniqueIdentifier.Companion.fromString(id);
        QueryCriteria criteria = new QueryCriteria.LinearStateQueryCriteria(
                null, ImmutableList.of(projectLinearId), Vault.StateStatus.UNCONSUMED, null);
        return rpcOps.vaultQueryByCriteria(criteria, ProjectState.class).getStates();
    }

    /**
     * Displays all Allocation states that exist in the node's vault.
     */
    @GET
    @Path("allocations")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<AllocationState>> getAllocations() {
        return rpcOps.vaultQuery(AllocationState.class).getStates();
    }

    /**
     * Displays all Allocation states that exist in the node's vault.
     */
    @GET
    @Path("all-allocations")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<AllocationState>> getAllAllocations() {
        QueryCriteria.VaultQueryCriteria criteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL);
        return rpcOps.vaultQueryByCriteria(criteria, AllocationState.class).getStates();
    }

    /**
     * Returns all parties registered with the [NetworkMapService]. These names can be used to look up identities
     * using the [IdentityService].
     */
    @GET
    @Path("deliveryTeams")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, List<CordaX500Name>> getDeliveryTeams() {
        List<NodeInfo> nodeInfoSnapshot = rpcOps.networkMapSnapshot();
        return ImmutableMap.of("deliveryTeams", nodeInfoSnapshot
                .stream()
                .map(node -> node.getLegalIdentities().get(0).getName())
                .filter(name -> !name.equals(myLegalName) && name.getOrganisation().startsWith(deliveryTeamServiceNamePrefix))
                .collect(toList()));
    }

    @POST
    @Path("allocate-delivery-team")
    @Produces(MediaType.APPLICATION_JSON)
    public Response allocateDeliveryTeam(
            @QueryParam("amount") String amount,
            @QueryParam("startDate") String startDate,
            @QueryParam("endDate") String endDate,
            @QueryParam("deliveryTeam") CordaX500Name partyName,
            @QueryParam("projectId") String projectId
    ){
        int amt = Integer.parseInt(amount);
        if (amt  < 0 ) {
            return Response.status(BAD_REQUEST).entity("Query parameter 'Budget Amount' must be greater than zero.\n").build();
        }
        if (startDate == null) {
            return Response.status(BAD_REQUEST).entity("Query parameter 'startDate' missing or has wrong format.\n").build();
        }
        if (endDate == null) {
            return Response.status(BAD_REQUEST).entity("Query parameter 'endDate' missing or has wrong format.\n").build();
        }

        //parse date
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDateTime startDateObj;
        LocalDateTime endDateObj;

        try{
            startDateObj = LocalDateTime.of(LocalDate.parse(startDate, formatter), LocalDateTime.MIN.toLocalTime());
        }catch(DateTimeParseException e){
            return Response.status(BAD_REQUEST).entity("Query parameter 'startDate' missing or has wrong format.\n").build();
        }

        try{
            endDateObj = LocalDateTime.of(LocalDate.parse(endDate, formatter), LocalDateTime.MIN.toLocalTime());
        }catch(DateTimeParseException e){
            return Response.status(BAD_REQUEST).entity("Query parameter 'endDate' missing or has wrong format.\n").build();
        }

        //delivery team
        final Party deliveryTeam = rpcOps.wellKnownPartyFromX500Name(partyName);
        if (deliveryTeam == null) {
            return Response.status(BAD_REQUEST).entity("Party named " + partyName + "cannot be found.\n").build();
        }

        //linear id
        logger.error("Linear ID");
        UniqueIdentifier projectLinearId = UniqueIdentifier.Companion.fromString(projectId);

        try {
            FlowProgressHandle<SignedTransaction> flowHandle = rpcOps
                    .startTrackedFlowDynamic(AllocationFlow.Initiator.class, projectLinearId, deliveryTeam, amt, startDateObj, endDateObj);
            flowHandle.getProgress().subscribe(evt -> System.out.printf(">> %s%n", evt));

            // The line below blocks and waits for the flow to return.
            final SignedTransaction result = flowHandle
                    .getReturnValue()
                    .get();

            final String msg = String.format("Transaction id %s committed to ledger.\nAllocation has been created.", result.getId());
            final JSONObject returnJsonObj = ResponseUtil.generateSuccessJsonObject(msg);
            return Response.status(CREATED).entity(returnJsonObj).build();

        } catch (Throwable ex) {
            final String msg = ex.getMessage();
            logger.error(ex.getMessage(), ex);
            final JSONObject returnJsonObj = ResponseUtil.generateErrorJsonObject(msg);
            return Response.status(BAD_REQUEST).entity(returnJsonObj).build();
        }
    }

    @POST
    @Path("update-allocation")
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateAllocation(
            @QueryParam("amount") String amount,
            @QueryParam("startDate") String startDate,
            @QueryParam("endDate") String endDate,
            @QueryParam("allocationId") String allocationId
    ){
        int amt = Integer.parseInt(amount);
        logger.error("Starting validation");
        if (amt  < 0 ) {
            return Response.status(BAD_REQUEST).entity("Query parameter 'Budget Amount' must be greater than zero.\n").build();
        }
        if (startDate == null) {
            return Response.status(BAD_REQUEST).entity("Query parameter 'startDate' missing or has wrong format.\n").build();
        }
        if (endDate == null) {
            return Response.status(BAD_REQUEST).entity("Query parameter 'endDate' missing or has wrong format.\n").build();
        }

        //parse date
        logger.error("Parsing Date");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDateTime startDateObj;
        LocalDateTime endDateObj;

        try{
            startDateObj = LocalDateTime.of(LocalDate.parse(startDate, formatter), LocalDateTime.MIN.toLocalTime());
        }catch(DateTimeParseException e){
            return Response.status(BAD_REQUEST).entity("Query parameter 'startDate' missing or has wrong format.\n").build();
        }

        try{
            endDateObj = LocalDateTime.of(LocalDate.parse(endDate, formatter), LocalDateTime.MIN.toLocalTime());
        }catch(DateTimeParseException e){
            return Response.status(BAD_REQUEST).entity("Query parameter 'endDate' missing or has wrong format.\n").build();
        }

        //linear id
        logger.error("Allocation ID");
        UniqueIdentifier allocationLinearId = UniqueIdentifier.Companion.fromString(allocationId);

        logger.error("Start Flow");
        try {
            FlowProgressHandle<SignedTransaction> flowHandle = rpcOps
                    .startTrackedFlowDynamic(AllocationUpdateFlow.Initiator.class, allocationLinearId, amt, startDateObj, endDateObj);
            flowHandle.getProgress().subscribe(evt -> System.out.printf(">> %s%n", evt));

            // The line below blocks and waits for the flow to return.
            final SignedTransaction result = flowHandle
                    .getReturnValue()
                    .get();

            final String msg = String.format("Transaction id %s committed to ledger.\nAllocation has been successfully updated.", result.getId());
            final JSONObject returnJsonObj = ResponseUtil.generateSuccessJsonObject(msg);
            return Response.status(CREATED).entity(returnJsonObj).build();

        } catch (Throwable ex) {
            final String msg = ex.getMessage();
            logger.error(ex.getMessage(), ex);
            final JSONObject returnJsonObj = ResponseUtil.generateErrorJsonObject(msg);
            return Response.status(BAD_REQUEST).entity(returnJsonObj).build();
        }
    }

}
