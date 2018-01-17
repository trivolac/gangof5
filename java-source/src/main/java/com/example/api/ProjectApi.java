package com.example.api;

import com.example.state.ProjectState;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.messaging.CordaRPCOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

/**
 * Created by Shailendra on 17-01-2018.
 */
@Path("project")
public class ProjectApi {
    private final CordaRPCOps rpcOps;
    private final CordaX500Name myLegalName;

    private final List<String> serviceNames = ImmutableList.of("Controller", "Network Map Service");

    private final String platformLeadServiceNamePrefix = "PL";

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
     * Displays all Demand states that exist in the node's vault.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<ProjectState>> getProjects() {
        return rpcOps.vaultQuery(ProjectState.class).getStates();
    }


    @GET
    @Path("state")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<ProjectState>> getStates() {
        return rpcOps.vaultQuery(ProjectState.class).getStates();
    }

}
