package com.example.api;

import com.example.flow.DemandCreationFlow;
import com.example.flow.DemandUpdateFlow;
import com.example.state.DemandState;
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
import java.util.concurrent.ExecutionException;

import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CREATED;

@Path("demand")
public class DemandApi {
    private final CordaRPCOps rpcOps;
    private final CordaX500Name myLegalName;

    private final List<String> serviceNames = ImmutableList.of("Controller", "Network Map Service");

    private final String platformLeadServiceNamePrefix = "PL";

    static private final Logger logger = LoggerFactory.getLogger(DemandApi.class);

    public DemandApi(CordaRPCOps rpcOps) {
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
     * Displays all Demand states that exist in the node's vault.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<DemandState>> getDemands() {
        return rpcOps.vaultQuery(DemandState.class).getStates();
    }


    /**
     * Returns all parties registered with the [NetworkMapService]. These names can be used to look up identities
     * using the [IdentityService].
     */
    @GET
    @Path("platformLeads")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, List<CordaX500Name>> getPlatformLeadPeers() {
        List<NodeInfo> nodeInfoSnapshot = rpcOps.networkMapSnapshot();
        return ImmutableMap.of("plPeers", nodeInfoSnapshot
                .stream()
                .map(node -> node.getLegalIdentities().get(0).getName())
                .filter(name -> !name.equals(myLegalName) && name.getOrganisation().startsWith(platformLeadServiceNamePrefix))
                .collect(toList()));
    }


    /**
     * Initiates a flow to create a Demand between two parties.
     *
     * Once the flow finishes it will have written the Demand to ledger. Both the sponsor and the platform lead will be able to
     * see it when calling GET /api/demand on their respective nodes.
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */
    @POST
    @Path("create-demand")
    public Response createDemand(@QueryParam("description") String description, @QueryParam("partyName") CordaX500Name partyName) throws InterruptedException, ExecutionException {
        logger.error("Starting validation");
        if (description == null || description.isEmpty()) {
            return Response.status(BAD_REQUEST).entity("Query parameter 'description' must exist.\n").build();
        }
        if (partyName == null) {
            return Response.status(BAD_REQUEST).entity("Query parameter 'partyName' missing or has wrong format.\n").build();
        }

        final Party otherParty = rpcOps.wellKnownPartyFromX500Name(partyName);
        if (otherParty == null) {
            return Response.status(BAD_REQUEST).entity("Party named " + partyName + "cannot be found.\n").build();
        }
        logger.error("Other party :: " + otherParty);

        logger.error("Starting Flow");

        try {
            FlowProgressHandle<SignedTransaction> flowHandle = rpcOps
                    .startTrackedFlowDynamic(DemandCreationFlow.Initiator.class, description, otherParty);
            flowHandle.getProgress().subscribe(evt -> System.out.printf(">> %s\n", evt));

            // The line below blocks and waits for the flow to return.
            final SignedTransaction result = flowHandle
                    .getReturnValue()
                    .get();

            final String msg = String.format("Transaction id %s committed to ledger.\nDemand has been created.", result.getId());
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
    @Path("update-demand")
    public Response updateDemand(
            @QueryParam("amount") String amount,
            @QueryParam("startDate") String startDate,
            @QueryParam("endDate") String endDate,
            @QueryParam(value = "id") String id
           ) {
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

        UniqueIdentifier linearId = UniqueIdentifier.Companion.fromString(id);

        logger.error("Starting Flow");
        try {
            FlowProgressHandle<SignedTransaction> flowHandle = rpcOps
                    .startTrackedFlowDynamic(DemandUpdateFlow.Initiator.class, linearId, startDateObj, endDateObj, amt);
            flowHandle.getProgress().subscribe(evt -> System.out.printf(">> %s%n", evt));

            // The line below blocks and waits for the flow to return.
            flowHandle.getReturnValue().get();

            final String msg = String.format("Transaction id %s with amount [%s] & startDate[%s] & endDate[%s] is updated to ledger.\n", id, amount, startDate, endDate);
            final JSONObject returnJsonObj = ResponseUtil.generateSuccessJsonObject(msg);
            return Response.status(CREATED).entity(returnJsonObj).build();

        } catch (Throwable ex) {
            final String msg = ex.getMessage();
            logger.error(ex.getMessage(), ex);
            final JSONObject returnJsonObj = ResponseUtil.generateErrorJsonObject(msg);
            return Response.status(BAD_REQUEST).entity(returnJsonObj).build();
        }
    }

    /**
     * Returns all parties registered with the [NetworkMapService]. These names can be used to look up identities
     * using the [IdentityService].
     */
    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, List<CordaX500Name>> getPeers() {
        List<NodeInfo> nodeInfoSnapshot = rpcOps.networkMapSnapshot();
        return ImmutableMap.of("peers", nodeInfoSnapshot
                .stream()
                .map(node -> node.getLegalIdentities().get(0).getName())
                .filter(name -> !name.equals(myLegalName) && !serviceNames.contains(name.getOrganisation()))
                .collect(toList()));
    }
}
