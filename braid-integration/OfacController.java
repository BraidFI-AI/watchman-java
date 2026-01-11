package io.ropechain.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.ropechain.api.data.*;
import io.ropechain.api.data.nacha.NachaOfacQuery;
import io.ropechain.api.data.ofac.BlockedResult;
import io.ropechain.api.data.ofac.OFACResult;
import io.ropechain.api.enums.AlertEnums;
import io.ropechain.api.enums.OFACEnums;
import io.ropechain.api.enums.TransactionEnums;
import io.ropechain.api.exception.DataValidationException;
import io.ropechain.api.exception.PermissionDeniedException;
import io.ropechain.api.model.*;
import io.ropechain.api.queue.OfacTransactionQueue;
import io.ropechain.api.service.*;
import io.ropechain.api.utility.OFACNamesUtility;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import lombok.extern.slf4j.Slf4j;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;


@Tag(name = "OFAC", description = " ")
@RestController
@RequestMapping("/OFAC")
@CrossOrigin("http://localhost:4200")
@Slf4j
public class OfacController {

    @Autowired
    private OFACService ofacService;

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private AlertActivityService alertActivityService;

    @Autowired
    private WhitelistService whitelistService;

    @Autowired
    private TransactionAuditService transactionAuditService;

    @Autowired
    private JmsTemplate queueProducer;

    @Autowired
    private MoovService moovService;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private SimulationService simulationService;

    @Autowired
    private InternalWatchlistService internalWatchlistService;

    @GetMapping("")
    @Operation(summary = "Get the List of OFAC",
            extensions = {
                    @Extension(properties = {
                            @ExtensionProperty(name = "groups", value = "admins,admin-admin,admin-ops,admin-readonly,admin-compliance,developers,developer-admin,developer-ops,developer-readonly")
                    })
    })
    public Page<OfacGetResults> getOfacResults(@RequestParam(required = false) OFACEnums.Status status,
                                               @RequestParam(required = false, defaultValue = "0") Integer pageNumber,
                                               @RequestParam(required = false, defaultValue = "100") Integer pageSize,
                                               @RequestParam(required = false) String entityType,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startDate,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime endDate) {
        // Start and end date are taken as Date in ISO format, for example: 2023-11-29
        return ofacService.findAllOFAC(status, entityType, startDate, endDate, pageNumber, pageSize);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get the details of OFAC",
            extensions = {
                    @Extension(properties = {
                            @ExtensionProperty(name = "groups", value = "admins,admin-admin,admin-ops,admin-readonly,admin-compliance,developers,developer-admin,developer-ops,developer-readonly")
                    })
            })
    public BlockedResult getOfacDetails(@PathVariable Integer id) {

        return ofacService.findById(id);
    }

    // To Be Removed
    @PutMapping("/{id}")
    @Operation(summary = "Update OFAC check status",
            extensions = {@Extension(properties = {
                    @ExtensionProperty(name = "groups", value = "admins,admin-admin,admin-ops,admin-compliance")})})
    public BlockedResult updateOFACCheckStatus(@PathVariable Integer id,
                                               @RequestBody OFACUpdateRequest request) throws PermissionDeniedException, DataValidationException {

        if (!tenantContext.isAdmin())
            throw new PermissionDeniedException("Request denied. This endpoint is only for Admin use.");

        BlockedResult blockedResult = ofacService.updateOFACCheckStatus(id, tenantContext.getUsername(), request);
        try {
            AlertEnums.AlertAction alertAction = request.getStatus() == OFACEnums.Status.BLOCKED
                    ? AlertEnums.AlertAction.DECLINE : AlertEnums.AlertAction.APPROVE;
            alertActivityService.closeAlert(AlertEnums.ContextType.OFAC, id.toString(), alertAction);
        } catch (Exception e) {
            log.error("Could not update alert status. ContextType: {}, ContextId:{}", AlertEnums.ContextType.OFAC, id);
        }
        return blockedResult;
    }

    @PostMapping("/{ofac_id}/retry")
    @Operation(summary = "Retry OFAC result",
            extensions = {
                @Extension(properties = {
                    @ExtensionProperty(name = "groups", value = "admins,admin-admin,admin-ops,admin-compliance")
                })
            })
    public void retryOfac(@PathVariable("ofac_id") Integer ofacId) throws DataValidationException {

    	ofacService.retryOfacCheck(ofacId);
    }

    // To Be Removed
    @PostMapping("/whitelist")
    @Operation(summary = "White list an OFAC Result, possibly resulting in multiple new whitelist entries",
            extensions = {
                @Extension(properties = {
                    @ExtensionProperty(name = "groups", value = "admins,admin-admin,admin-ops,admin-compliance")
                })
            })
    public List<WhitelistEntry> whitelistOfac(@RequestBody WhitelistRequest req) throws DataValidationException {

    	return whitelistService.addWhitelist(req.getOfacId());
    }

    @PostMapping("/whitelist/reset")
    @Operation(summary = "Remove all existing white listings originally created from a specific OFAC result",
            extensions = {
                @Extension(properties = {
                    @ExtensionProperty(name = "groups", value = "admins,admin-admin,admin-ops,admin-compliance")
                })
            })
    public void resetWhitelistForOfac(@RequestBody WhitelistRequest req) {

    	OFACResult ofac = ofacService.findOriginalById(req.getOfacId());
    	
    	whitelistService.resetWhitelist(ofac);
    }

    @PostMapping("/search/address")
    @Operation(summary = "Allow dryrun of OFAC query on an address",
            extensions = {
                @Extension(properties = {
                    @ExtensionProperty(name = "groups", value = "admins,admin-admin,admin-ops,admin-readonly,admin-compliance,developers,developer-admin,developer-ops,developer-readonly")
                })
            })
    public ObjectNode ofacDryrunAddress(@Valid @RequestBody ExternalOfacAddressSearch request) throws DataValidationException {
        simulationService.checkSimulationEnabled();
        NachaOfacQuery ofacQuery = new NachaOfacQuery(NachaOfacQuery.OfacCheckQueryType.ADDRESS, "customer.address", request.getStreetAddress(), null, request.getCity(), request.getState(), request.getZipcode());

        log.info("[OFAC-ADDRESS] {}", ofacQuery);
        ArrayList<NachaOfacQuery> queries = new ArrayList<>();
        queries.add(ofacQuery);
        return transformOfacResponseForExternalCaller(moovService.ofacCheck(request.getMinMatch(), queries));
    }

    @PostMapping("/search/entity")
    @Operation(summary = "Allow dry-run of OFAC query on an entity",
            extensions = {
                @Extension(properties = {
                    @ExtensionProperty(name = "groups", value = "admins,admin-admin,admin-ops,admin-readonly,admin-compliance,developers,developer-admin,developer-ops,developer-readonly")
                })
            })
    public ObjectNode ofacDryrunEntity(@Valid @RequestBody ExternalOfacEntitySearch request) throws DataValidationException {
        simulationService.checkSimulationEnabled();

        NachaOfacQuery ofacQuery = new NachaOfacQuery(NachaOfacQuery.OfacCheckQueryType.FIELD, "ANY", OFACNamesUtility.filterName(request.getName()));
        log.info("[OFAC-ENTITY] {}", ofacQuery);
        ArrayList<NachaOfacQuery> queries = new ArrayList<>();
        queries.add(ofacQuery);
        return transformOfacResponseForExternalCaller(moovService.ofacCheck(request.getMinMatch(), queries));
    }

    /**
     * Changes response back closer to Moovs original response, except in a `result` object wrapper.
     * Intentionally hiding this method here to minimize the chance that it gets used elsewhere.
     */
    private ObjectNode transformOfacResponseForExternalCaller(ArrayNode arrayNode) {
        // if no-match, return `{"result":[]}` instead of just an empty array
        if (arrayNode == null || arrayNode.isEmpty()) {
            ObjectNode resultNode = mapper.createObjectNode();
            resultNode.set("results", mapper.createObjectNode());
            return resultNode;
        }

        // Strip out the `key` and `value` properties that we add in.
        JsonNode firstNode = arrayNode.get(0);
        if (firstNode.isObject()) {
            ObjectNode objectNode = (ObjectNode) firstNode;
            objectNode.remove("key");
            objectNode.remove("value");
            return objectNode;
        }
        log.error("Unexpected OFAC result");
        return null;
    }



    @GetMapping("/whitelist")
    @Operation(summary = "Show the current white list",
            extensions = {
                @Extension(properties = {
                    @ExtensionProperty(name = "groups", value = "admins,admin-admin,admin-ops,admin-readonly,admin-compliance,developers,developer-admin,developer-ops,developer-readonly")
                })
            })
    public List<WhitelistEntry> showWhitlist() throws PermissionDeniedException, DataValidationException {

    	return whitelistService.findAll();
    	
    }

    /**
     * This is a less restrictive OFAC retry that will allow us to retry a single transaction as long as it's in
     * ACCEPTED or INITIATED status. This is meant for cases where the transaction failed to be OFAC'd, or if the
     * bulk retry fails for some unforseen reason.
     * @param paymentId
     */
    @PostMapping("/retry/transaction/{paymentId}")
    @Operation(summary = "Retry OFAC for a transaction",
            extensions = {
                @Extension(properties = {
                    @ExtensionProperty(name = "groups", value = "admins,admin-admin,admin-ops,admin-compliance")
                })
            })
    public ResponseEntity<Map<String, Object>> retryTransactionOfac(@PathVariable String paymentId)
            throws DataValidationException {
        TransactionAudit transaction = transactionAuditService.findByPaymentId(paymentId);

        // Avoid rerunning OFAC on transaction that may have posted already.
        if(transaction.getProcessingStatus().equals(TransactionEnums.ProcessingStatus.ACCEPTED) ||
                transaction.getProcessingStatus().equals(TransactionEnums.ProcessingStatus.INITIATED)) {
            queueProducer.convertAndSend(OfacTransactionQueue.TRANSACTION_QUEUE_NAME,
                    new OfacTransactionMessage(transaction.getCustomer().getId(), paymentId,
                            transaction.getProduct().getTenantId()));
        } else {
            throw new DataValidationException("Only transaction in ACCEPTED or INITIATED can be retried");
        }

        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * Finds all transaction that are stuck in ACCEPTED with an OFAC error.
     * This is meant for cases where we have a catastrophic OFAC failure and need to retry hundreds or thousands of
     * transactions.
     * The SQL is fairly restrictive about which transactions it's willing to retry, requiring that the transaction
     * be in ACCEPTED processing status, and having an ERROR OFAC.
     */
    @PostMapping("/retry/transaction/errors")
    @Operation(summary = "Retry up to 500 transactions that are pending OFAC due to error",
            extensions = {
                @Extension(properties = {
                    @ExtensionProperty(name = "groups", value = "admins,admin-admin,admin-ops,admin-compliance")
                })
            })
    public ResponseEntity<Map<String, Object>> retryAllErrors() {
        List<TransactionAudit> hungByOfac = transactionAuditService.findByOfacErrorsOutstanding();
        log.info("Retrying transactions hung by OFAC failures. Found {} to retry", hungByOfac.size());

        int processedCount = hungByOfac.size(); // Count of processed transactions
        for(TransactionAudit transaction : hungByOfac) {
            queueProducer.convertAndSend(OfacTransactionQueue.TRANSACTION_QUEUE_NAME,
                    new OfacTransactionMessage(transaction.getCustomer().getId(), transaction.getPaymentId(),
                            transaction.getProduct().getTenantId()));
        }

        // Return a JSON response indicating success and the count of processed transactions
        return ResponseEntity.ok(Map.of("success", true, "processedCount", processedCount));
    }

    @GetMapping("/sanction-orders")
    @Operation(summary = "Get list of sanction orders",
            extensions = {
                    @Extension(properties = {
                            @ExtensionProperty(name = "groups", value = "admins,admin-admin,admin-ops,admin-readonly,admin-compliance")
                    })
            })
    public List<InternalWatchlist> getSanctionOrders() {

        return internalWatchlistService.getSanctionOrders();
    }

    @GetMapping("/validate-by-sanction-orders")
    @Operation(summary = "Validate input by sanction orders",
            extensions = {
                    @Extension(properties = {
                            @ExtensionProperty(name = "groups", value = "admins,admin-admin,admin-ops,admin-readonly,admin-compliance")
                    })
            })
    public JsonNode validateBySanctionOrders(@RequestParam String value) {

        return internalWatchlistService.validateBySanctionOrders(value);
    }
}
