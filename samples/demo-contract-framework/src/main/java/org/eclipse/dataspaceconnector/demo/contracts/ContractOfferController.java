package org.eclipse.dataspaceconnector.demo.contracts;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.dataspaceconnector.spi.contract.ContractOfferQuery;
import org.eclipse.dataspaceconnector.spi.contract.ContractOfferService;

import java.util.stream.Collectors;

@Consumes({ MediaType.APPLICATION_JSON })
@Produces({ MediaType.APPLICATION_JSON })
@Path("/offers")
public class ContractOfferController {

    private final ContractOfferService contractOfferService;

    public ContractOfferController(ContractOfferService contractOfferService) {
        this.contractOfferService = contractOfferService;
    }

    @GET
    public Response getOffers() {
        ContractOfferQuery query = ContractOfferQuery.builder().build();

        var offers = contractOfferService.queryContractOffers(query)
                .getContractOfferStream()
                .collect(Collectors.toList());

        return Response.ok(offers).build();
    }

}
