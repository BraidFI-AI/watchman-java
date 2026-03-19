package io.ropechain.api.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jsonwebtoken.io.IOException;
import io.ropechain.api.data.*;
import io.ropechain.api.data.alerts.Alert;
import io.ropechain.api.data.nacha.NachaInstruction;
import io.ropechain.api.data.nacha.NachaOfacQuery;
import io.ropechain.api.data.nacha.NachaSettlementHistoryRecord;
import io.ropechain.api.data.nachapop.ACHInstruction;
import io.ropechain.api.data.nachapop.InstructionReversalRequest;
import io.ropechain.api.data.nachapop.NachaPopTransaction;
import io.ropechain.api.data.nachapop.ReturnInstruction;
import io.ropechain.api.data.nachapop.NachaPopTransactionReturnRequest;
import io.ropechain.api.data.nachapop.UnblockTransactionRequest;
import io.ropechain.api.data.ofac.OFACResult;
import io.ropechain.api.data.ofac.OfacCheckDetails;
import io.ropechain.api.enums.*;
import io.ropechain.api.enums.CustomerEnums.CustomerTypes;
import io.ropechain.api.enums.PaymentInstrumentEnums.AchAccountTypes;
import io.ropechain.api.enums.TransactionEnums.AchReturnCodes;
import io.ropechain.api.enums.TransactionEnums.AchSecCode;
import io.ropechain.api.enums.TransactionEnums.AchServiceType;
import io.ropechain.api.exception.BackendConnectivityException;
import io.ropechain.api.exception.DataValidationException;
import io.ropechain.api.exception.DeclineException;
import io.ropechain.api.model.Ach;
import io.ropechain.api.model.AddressRequest;
import io.ropechain.api.model.NachaFileStats;
import io.ropechain.api.model.ScheduledEventTrigger;
import io.ropechain.api.model.Wire;
import io.ropechain.api.repository.AchRepository;
import io.ropechain.api.repository.ContactRepository;
import io.ropechain.api.repository.nachapop.NachaFileStatsRepository;
import io.ropechain.api.service.nachapop.InstructionService;
import io.ropechain.api.service.nachapop.NachaFilesService;
import io.ropechain.api.service.nachapop.NachaPopTransactionService;
import io.ropechain.api.service.nachapop.NachaScheduledEventService;
import io.ropechain.api.utility.HttpUtility;

import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;

import io.ropechain.api.utility.OFACNamesUtility;
import io.ropechain.api.utility.StringUtility;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import static io.ropechain.api.service.IdentityVerificationService.isCustomerActiveRegardingCipStatus;

@Service
@EnableCaching
@RequiredArgsConstructor
@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
@Slf4j
public class NachaService {

    private final String CONTENT_TYPE_HEADER = "Content-Type";
    private final String AUTH_HEADER = "Authorization";
    private final String APPLICATION_JSON = "application/json";
    private final String ACCEPT = "Accept";

    @Value("${nachapop.internal:false}")
    private boolean nachaPopInternal;
    
    @Value("${nachapop.api-base}")
    private String nachaApiBase; // = "https://cloud.adaptershack.com:8443/";

    @Value("${nachapop.checkForDuplicates:false}")
    private boolean checkForDuplicates;

    private final String EP_CREATE_INSTRUCTION = "ach/instructions";
    private final String EP_RETURN_TRANSACTION = "ach/transactions/{id}/return";

    private final String EP_UNBLOCK_TRANSACTION = "ach/instructions/unblock";
    private final String EP_MARK_TRANSACTION_POSTED = "ach/transactions/{id}/post";
    private final String EP_UPDATE_INSTRUCTION = "ach/instructions/{id}";
    private final String EP_GET_FILE_STATS = "ach/file/stats";
    private final String EP_GET_STATUS = "ach/status";

    private final String EP_OFAC_CHECK = "ach/ofacCheck";

    private final AchRepository achRepository;
    private final CustomerService customerService;
    private final ContactRepository contactRepository;
    private final ContactService contactService;
    private final OFACService ofacService;
    private final RetryService retryService;
    private final WhitelistService whitelistService;
    private final AlertCreationService alertCreationService;
    private final AddressService addressService;
    private final SystemConfigService systemConfigService;;
    private final InternalWatchlistService internalWatchlistService;

    @Autowired
    MoovService moovService;

    @Autowired
    private ApiCallerService apiSvc;
    
    @Autowired
    InstructionService instructionService;

    @Autowired
    NachaFilesService nachaFilesService;

    @Autowired
    ProgramService programService;

    @Autowired
    private ProhibitedEntitiesService prohibitedEntitiesService;

    @Autowired
    ProductService productService;

    @Autowired
    private WebHookEventServices webHookEventServices;

    @Autowired
    private IdentityVerificationService identityVerificationService;


    public NachaInstruction unblockTransaction(String nachaInstructionId) throws JsonProcessingException {
        
    	if(this.nachaPopInternal) {
    		UnblockTransactionRequest req = new UnblockTransactionRequest();
    		req.nachaInstructionId = nachaInstructionId;
    		return NachaInstruction.fromPOP( instructionService.unblockAch(req) );
    	}
    	
    	// FIXME the following code is only used by junit tests
    	

        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Build post body
        Map<String, Object> map = new HashMap<>();
        map.put("nachaInstructionId", nachaInstructionId);
        String putBody = mapper.writeValueAsString(map);

        String response = null;
        String authHeaderValue = HttpUtility.getBasicAuthenticationHeader(systemConfigService.getStringConfig(SystemConfigEnums.Key.NACHAPOP_USERNAME),
                                                                          systemConfigService.getStringConfig(SystemConfigEnums.Key.NACHAPOP_PASSWORD));
        try {
            response = apiSvc.put(nachaApiBase + EP_UNBLOCK_TRANSACTION, "", putBody,
                    CONTENT_TYPE_HEADER, APPLICATION_JSON, ACCEPT, APPLICATION_JSON,
                    AUTH_HEADER, authHeaderValue);
        } catch (IOException | InterruptedException | URISyntaxException | java.io.IOException ex) {
            log.error( ex.toString(), ex);
        }
        System.out.println("Nacha resp: " + response);
        NachaInstruction instruction = null;
        JsonNode json = mapper.readTree(response);

        if(json.has("id")) {
            log.info("Parsing");
            instruction = mapper.treeToValue(json, NachaInstruction.class);
            log.info(instruction.toString());
        } else {
            log.error( "Unable to unblock transaction: {0}", response);
        }

        return instruction;
    }



    public Page<NachaFileStatsRepository.NachaFileStatsV2> searchFilesBy(String filename, String originalFilename, int pageNumber, int pageSize) {
    	
    	return nachaFilesService.searchFilesBy(filename, originalFilename, pageNumber, pageSize);
    	
    }
    

    public Map<String, NachaFileStats> getNachaFileStats(LocalDate startDate) {
    	
    	if( nachaPopInternal ) {
    		
    		Map<String, NachaFileStats> results = new HashMap<>();
    		
    		try {
    			for(Map.Entry<String,NachaFileStatsRepository.NachaFileStats> entry : 
					nachaFilesService.getFileStats(startDate, tenantContext.getAccessibleTenantIds()).entrySet()) {
					results.put( entry.getKey(), NachaFileStats.of(entry.getValue()) );
				}
    			return results;
			} catch (Exception e) {
				throw new BackendConnectivityException(e);
			}
    				
    	}    	
    	
    	// FIXME the following code is only used by junit tests
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        String response;
        String url = EP_GET_FILE_STATS;
        String authHeaderValue = HttpUtility.getBasicAuthenticationHeader(systemConfigService.getStringConfig(SystemConfigEnums.Key.NACHAPOP_USERNAME),
                                                                          systemConfigService.getStringConfig(SystemConfigEnums.Key.NACHAPOP_PASSWORD));
        try {
            log.info("File Stats Request: " + nachaApiBase + url + ",startDate="+startDate+",tenantId="+tenantContext.getTenantId());
            response = apiSvc.get(nachaApiBase + url, "startDate="+startDate+"&tenantId="+tenantContext.getTenantId(),
                    CONTENT_TYPE_HEADER, APPLICATION_JSON, ACCEPT, APPLICATION_JSON,
                    AUTH_HEADER, authHeaderValue);
            return mapper.readValue(response, new TypeReference<Map<String, NachaFileStats>>(){});

        } catch (IOException | InterruptedException | URISyntaxException | java.io.IOException ex) {
            log.error( ex.toString(), ex);
            throw new BackendConnectivityException(ex);
        }
    }

    
    public NachaInstruction createInstruction(CreateInstructionRequest req)
            throws Exception {

        AchPaymentInstrument payInst = achRepository.findById(req.getPaymentInstrumentId()).orElseThrow();

        Contact counterparty = payInst.getCounterpartyId() == null ?
                null : this.contactRepository.getById(payInst.getCounterpartyId());

        String routingNumber = payInst.getRoutingNumber();
        String accountNumber = payInst.getAccountNumber();

        String name =
                counterparty == null ? null :
                        counterparty.getName();
        AchAccountTypes accountType = payInst.getBankAccountType();

        Customer customer = customerService.findById(req.getCustomerId());

        ObjectMapper mapper = new ObjectMapper().
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        mapper.configOverride(NachaInstruction.class).setInclude(
                JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL));

        // Build post body
        NachaInstruction nachaInstruction = new NachaInstruction();
        
        AchConfig settlement = getConfig(req.getProduct());

        if(!this.nachaPopInternal && !settlement.valid()) {
            // Something is very wrong here. Let's be vague back to the customer, and raise an alarm bell.
            throw new Exception("Invalid ACH Configuration");
        }
        
        Program program = programService.findById(req.getProduct().getProgramId());

        nachaInstruction.companyOdfi = program.getAchOdfi();
        nachaInstruction.immediateOrigin = settlement.immediateOrigin;
        nachaInstruction.immediateOriginName = settlement.immediateOriginName;
        nachaInstruction.immediateDestination = settlement.immediateDestination;
        nachaInstruction.immediateDestinationName = settlement.immediateDestinationName;

        // The only valid options are New & Manual Review
        nachaInstruction.status = req.getStatus().equals(TransactionEnums.ProcessingStatus.INITIATED) ?
                TransactionEnums.NachaInstructionStatus.NEW.ordinal() :
                TransactionEnums.NachaInstructionStatus.MANUAL_REVIEW.ordinal();

        if(req.getAchCompanyName() != null && !req.getAchCompanyName().isBlank()) {
            nachaInstruction.companyName = StringUtility.sanitizeMessage(req.getAchCompanyName());
        } else if(customer.getType() == CustomerTypes.INDIVIDUAL) {
            String fullname = customer.getFirstName() + (customer.getLastName() == null ? "" : " " + customer.getLastName());
            nachaInstruction.companyName = StringUtility.sanitizeMessage(fullname);
        } else {
            nachaInstruction.companyName = StringUtility.sanitizeMessage(customer.getEffectiveAchCompanyName());
        }

        if(req.getReceiverName() != null && !req.getReceiverName().isBlank()) {
            name = StringUtility.sanitizeMessage(req.getReceiverName());
        } else if(counterparty == null) {
            name = nachaInstruction.companyName;
        }

        if(req.getAchCompanyId() != null && !req.getAchCompanyId().isBlank()) {
            
            nachaInstruction.companyIdentification = req.getAchCompanyId();
            
        } else {
        
            if(customer.getEffectiveAchCompanyId()==null || customer.getEffectiveAchCompanyId().isBlank()) {
                throw new DeclineException(TransactionEnums.Declines.CUSTOMER_COMPANYID_MISSING,
                        "Customer associated with this account does not have an ID number defined");
            }
    
            nachaInstruction.companyIdentification = customer.getEffectiveAchCompanyId();
        }
        
        if(req.getSecCode()==null) {
            if(customer.getType() == CustomerTypes.INDIVIDUAL) {
                nachaInstruction.standardEntryClassCode = "PPD";
            } else {
                nachaInstruction.standardEntryClassCode = "CCD";
            }

        } else {
            nachaInstruction.standardEntryClassCode = req.getSecCode().toString();
        }

        nachaInstruction.companyEntryDescription = req.getEntryDescription();
        nachaInstruction.companyDiscretionaryData = req.getDiscretionaryData();
        nachaInstruction.loadedFromFile = req.getLoadedFromFile();
        
        // TODO do we need to create a balanced transaction?
        if(req.isCredit()) {
            nachaInstruction.creditFI = routingNumber; // RDFIIdentification + checkDigit
            nachaInstruction.creditAccountNumber = accountNumber; // DFIAccountNumber
            nachaInstruction.creditIdentificationNumber = req.getIdentificationNumber() == null ? "" : req.getIdentificationNumber();
            nachaInstruction.creditIndividualName = StringUtility.sanitizeMessage(name); // individualName
            nachaInstruction.creditAccountType = translateAccountType(accountType);
        } else {
            nachaInstruction.debitFI = routingNumber; // RDFIIdentification + checkDigit
            nachaInstruction.debitAccountNumber = accountNumber; // DFIAccountNumber
            nachaInstruction.debitIdentificationNumber = req.getIdentificationNumber() == null ? "" : req.getIdentificationNumber();
            nachaInstruction.debitIndividualName = StringUtility.sanitizeMessage(name); // individualName
            nachaInstruction.debitAccountType = translateAccountType(accountType);
        }
        nachaInstruction.amount = req.getAmount();

        if(req.getAddenda() != null && !req.getAddenda().isBlank()) {
            nachaInstruction.addenda = new ArrayList<String>();
            nachaInstruction.addenda.add(req.getAddenda());
        }

        if(settlement.getServiceType() == AchServiceType.STANDARD) {
            nachaInstruction.service = AchServiceType.STANDARD;
        } else if(req.getService() == null) {
            nachaInstruction.service = settlement.serviceType;
        } else {
            nachaInstruction.service = req.getService();
        }

        if(req.getEffectiveEntryDate() != null ) {
            
            nachaInstruction.effectiveEntryDate = req.getEffectiveEntryDate().toString();
            nachaInstruction.autoAdjustDate = true; // we are asking POP for this date, but might change it
            
        }
        
        if(req.getDescriptiveDate() != null && !req.getDescriptiveDate().isBlank()) {
            nachaInstruction.companyDescriptiveDate = req.getDescriptiveDate();
        }

        // this will produce an error in POP, so prevent it now
        if(req.isPrenote() && req.getAmount().compareTo(BigDecimal.ZERO) != 0) {
            throw new DeclineException(TransactionEnums.Declines.TRANSACTION_INVALID, "Prenote must have an amount of zero");
        }
        
        // "prenote" in this instruction is not boolean, it combines prenote
        // and zero dollar entry
        //
        //     0 - not prenote or zero dollar
        //     1 - prenote (also implies zero dollar)
        //     2 - zero dollar but not prenote
        nachaInstruction.prenote = req.isPrenote() ? 1 : 
        	req.getAmount().compareTo(BigDecimal.ZERO) == 0 ? 2 : 0 
        	;
        
        // IAT specific stuff
        if( req.getSecCode() == AchSecCode.IAT) {
        	
        	// for now, maybe later we want to separate these
        	nachaInstruction.iatOriginatorId = nachaInstruction.companyIdentification;

        	
			nachaInstruction.originatorStreetAddress = "";
			nachaInstruction.originatorCity = "";
			nachaInstruction.originatorState = "";
			nachaInstruction.originatorCountry = "";
			nachaInstruction.originatorPostalCode = "";

			Address customerAddress = addressService.getCustomerAddress(customer.getId());
    		if(customerAddress != null) {
    			nachaInstruction.originatorStreetAddress = Objects.toString(customerAddress.getLine1(),"");
    			nachaInstruction.originatorCity = Objects.toString(customerAddress.getCity(),"");
    			nachaInstruction.originatorState = Objects.toString(customerAddress.getState(),"");
    			nachaInstruction.originatorCountry = Objects.toString(customerAddress.getCountryCode(),"");
    			nachaInstruction.originatorPostalCode = Objects.toString(customerAddress.getPostalCode(),"");
    		}
        	
        	nachaInstruction.gatewayRoutingNumber = payInst.getGatewayRoutingNumber();
        	nachaInstruction.destinationCountryCode = payInst.getCountryCode();
        	nachaInstruction.receiverCountry = payInst.getCountryCode();
        	nachaInstruction.rdfiNumberQualifier = payInst.getRdfiNumberQualifier().code();
        	nachaInstruction.rdfiName = payInst.getBankName();
        	
        	// not all countries will have states, provinces, or postal codes, so do not
        	// require these fields, but also ensure we don't pass null to POP
        	nachaInstruction.receiverStreetAddress = Objects.toString(payInst.getReceiverStreetAddress(),"");
        	nachaInstruction.receiverCity = Objects.toString(payInst.getReceiverCity(),"");
        	nachaInstruction.receiverState = Objects.toString(payInst.getReceiverState(),"");
        	nachaInstruction.receiverPostalCode = Objects.toString(payInst.getReceiverPostalCode(),"");
        	
        	nachaInstruction.iatTransactionTypeCode = Objects.toString(req.getIatTransactionTypeCode(),"");
        	
        	// IAT fields used for foreign exchange
        	nachaInstruction.destinationCurrencyCode = Objects.toString(req.getDestinationCurrencyCode(),"");
        	nachaInstruction.foreignExchangeIndicator = req.getForeignExchangeIndicator();
        	nachaInstruction.foreignExchangeReferenceIndicator = req.getForeignExchangeReferenceIndicator();
        	nachaInstruction.foreignExchangeReference=Objects.toString(req.getForeignExchangeReference(),"");
        	nachaInstruction.foreignPaymentAmount = req.getForeignPaymentAmount();
        	nachaInstruction.foreignTraceNumber = Objects.toString(req.getForeignTraceNumber(),"");
        	
        }
        
        nachaInstruction.setOriginalTransactionId(req.getOriginalTransactionId());
		
		if(this.nachaPopInternal) {
			
	        nachaInstruction.productId = req.getProduct().getId().toString();
	        
	        ACHInstruction i = instructionService.addInstruction(
					nachaInstruction.toPOP(), 
					0.0,
					false, //boolean ofacCheck,
					true, //boolean validate,
					false, //boolean checkForDuplicates,
					false //boolean testOnly
					);

			nachaInstruction.populate(i);
			
		} else {
	
	    	// FIXME the following code is only used by junit tests

			String postBody = mapper.writeValueAsString(nachaInstruction);
	
	        log.info(postBody);
	
	        String response;
	        String url = EP_CREATE_INSTRUCTION;
	        String authHeaderValue = HttpUtility.getBasicAuthenticationHeader(systemConfigService.getStringConfig(SystemConfigEnums.Key.NACHAPOP_USERNAME),
                                                                              systemConfigService.getStringConfig(SystemConfigEnums.Key.NACHAPOP_PASSWORD));
	        try {
	            response =  retryService.retry( ()->
	                    apiSvc.post(nachaApiBase + url, "checkForDuplicates="+checkForDuplicates, postBody,
	                            CONTENT_TYPE_HEADER, APPLICATION_JSON, ACCEPT, APPLICATION_JSON,
	                            AUTH_HEADER, authHeaderValue));
	
	            JsonNode json = mapper.readTree(response);
	
	            if( json.has("id")) {
	                nachaInstruction = mapper.treeToValue(json, NachaInstruction.class);
	                nachaInstruction.success = true;
	            } else {
	                log.error( "Response was not a success: {0}", response);
	                nachaInstruction.success = false;
	                if(json.has("detail")) {
	                    nachaInstruction.errorDetail = json.get("detail").asText();
	                }
	            }
	        } catch (Exception ex) {
	            log.error( ex.toString(), ex);
	        }
	
	        if(req.isThrowOnFailure() && !nachaInstruction.success) {
	            if(nachaInstruction.errorDetail != null) {
	                throw new DataValidationException("ACH error: " + nachaInstruction.errorDetail);
	            } else {
	                throw new DataValidationException("ACH cannot be processed at this time.");
	            }
	        }
		}
		
        return nachaInstruction;
    }


    private String translateAccountType(AchAccountTypes accountType) {
        if (accountType == null) {
            return null;
        }

        return switch (accountType) {
            case CHECKING -> "CHECKING";
            case SAVINGS -> "SAVINGS";
        };
    }

    @Autowired
    private ObjectMapper objectMapper;
    
    
    public AchConfig getConfig(Product product) throws JsonProcessingException {
    	// we don't have anything here yet
		return achConfigService.getConfig();
    }

    @Autowired
    AchConfigService achConfigService;
    
    public AchConfig updateConfig(Product product, AchConfig in) throws JsonProcessingException, DataValidationException {
    	// nothing to do here yet
		return achConfigService.updateConfig(in);
    }

    @Autowired
    NachaPopTransactionService nachaPopTransactionService;

    public boolean returnTransaction(String nachaTranId, AchReturnCodes returnCode, String returnTransactionPaymentId) {
    	
    	if( nachaPopInternal ) {
    		
    		log.info("returnTransactionPaymentId: " + returnTransactionPaymentId);
    		
    		NachaPopTransactionReturnRequest req = new NachaPopTransactionReturnRequest();
    		req.setTransactionId(nachaTranId);
    		req.setReturnCode(returnCode.getCode());
    		req.setReturnTransactionPaymentId(returnTransactionPaymentId);
    		
    		try {
	    		ReturnInstruction i = nachaPopTransactionService.returnTransaction(nachaTranId, req);
	    		
	    		return i.getId() != null;
    		} catch (Exception e) {
    			// This happens in unit tests, specifically
    			// io.ropechain.api.AchApiTests.testInboundAch
    			// because that test simulates a message from TransactionFilesPoller
    			// but there is not actually any record in TBL_NACHA_POP_TRANSACTION.
    			// This should NOT happen in real life, and if it does it
    			// needs to be investigated.
    			log.error("Exception trying to generate outbound return for inbound transaction",e);
    			return false;
    		}
    	}    	
    	
    	// FIXME the following code is only used by junit tests
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);


        ObjectNode reqJson = mapper.createObjectNode();
        reqJson.put("id", nachaTranId);
        reqJson.put("returnCode", returnCode.getCode());

        String postBody;
        try {
            postBody = mapper.writeValueAsString(reqJson);
        } catch (JsonProcessingException e) {
            return false;
        }

        log.info(postBody);

        String response;
        String url = EP_RETURN_TRANSACTION.replace("{id}", nachaTranId);
        String authHeaderValue = HttpUtility.getBasicAuthenticationHeader(systemConfigService.getStringConfig(SystemConfigEnums.Key.NACHAPOP_USERNAME),
                                                                          systemConfigService.getStringConfig(SystemConfigEnums.Key.NACHAPOP_PASSWORD));
        try {
            response = apiSvc.post(nachaApiBase + url, "", postBody,
                    CONTENT_TYPE_HEADER, APPLICATION_JSON, ACCEPT, APPLICATION_JSON,
                    AUTH_HEADER, authHeaderValue);

            JsonNode json = mapper.readTree(response);

            if( json.has("id")) {
                return true;
            } else {
                log.error( "Response was not a success: {0}", response);
                throw new BackendConnectivityException(response);
            }
        } catch (IOException | InterruptedException | URISyntaxException | java.io.IOException ex) {
            log.error( ex.toString(), ex);
            throw new BackendConnectivityException(ex);
        }

    }

    public boolean markTransactionPosted(String nachaTranId) {

    	if( nachaPopInternal ) {
    		NachaPopTransaction nachaTran = nachaPopTransactionService.markTransactionAsPosted(nachaTranId);
    		return nachaTran.getId() != null;
    	}    	
    	
    	// FIXME the following code is only used by junit tests
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        String response;
        String url = EP_MARK_TRANSACTION_POSTED.replace("{id}", nachaTranId);
        String authHeaderValue = HttpUtility.getBasicAuthenticationHeader(systemConfigService.getStringConfig(SystemConfigEnums.Key.NACHAPOP_USERNAME),
                                                                          systemConfigService.getStringConfig(SystemConfigEnums.Key.NACHAPOP_PASSWORD));
        try {
            response = apiSvc.post(nachaApiBase + url, "", "",
                    CONTENT_TYPE_HEADER, APPLICATION_JSON, ACCEPT, APPLICATION_JSON,
                    AUTH_HEADER, authHeaderValue);

            JsonNode json = mapper.readTree(response);

            if( json.has("id")) {
                return true;
            } else {
                log.error( "Response was not a success: {0}", response);
                throw new BackendConnectivityException(response);
            }
        } catch (IOException | InterruptedException | URISyntaxException | java.io.IOException ex) {
            log.error( ex.toString(), ex);
            throw new BackendConnectivityException(ex);
        }

    }

    public List<NachaSettlementHistoryRecord> getSettlementHistory(Product product, ZonedDateTime startDate, ZonedDateTime endDate) throws JsonProcessingException {
        
    	if( nachaPopInternal ) {
    		return instructionService.getInstructionExractions(
    				product == null ? null : product.getId().toString(),
    				startDate,
    				endDate)
    			.stream().map(NachaSettlementHistoryRecord::of).toList();

    	}  else {
    		// should never be called
    		return null;
    	}
    	

    }

    public List<NachaSettlementHistoryRecord> getReturnHistory(ZonedDateTime startDate, ZonedDateTime endDate) {
        if (nachaPopInternal) {
            return instructionService.getReturnExtractions(startDate, endDate)
                    .stream().map(NachaSettlementHistoryRecord::of).toList();
        } else {
            // should never be called
            return null;
        }
    }
    
    
    public String getSettlementFile(Integer productId, String filename) {

    	if( nachaPopInternal ) {
    		try {
				return IOUtils.toString(nachaFilesService.getExtracts(filename).getInputStream(),"utf-8");
			} catch (Exception ex) {
	            log.error( ex.toString(), ex);
	            throw new BackendConnectivityException(ex);
			}
    	}  else {
    		// should never be called
    		return null;
    	}
    }

    public String getReturnFile(String filename) {

    	if( nachaPopInternal ) {
    		try {
				return IOUtils.toString(nachaFilesService.getExtracts(filename).getInputStream(),"utf-8");
			} catch (Exception ex) {
	            log.error( ex.toString(), ex);
	            throw new BackendConnectivityException(ex);
			}
    	}  else {
    		// should never be called
    		return null;
    	}
    }
    
    

    public String loadTransactionsFile(String originalFilename, String dir, String contents) {
    	
    	if( nachaPopInternal ) {
    		try {
    			if("outbound".equalsIgnoreCase(dir)) {
	    			return nachaFilesService.loadOutboundFile(contents, originalFilename).toString();
    			} else {
	    			return nachaFilesService.loadTrasactionsFile(contents, originalFilename).toString();
    			}
    		} catch (Exception e) {
    			throw new RuntimeException(e);
    		}
    	}  else {
    		// should never be called
    		return null;
    	}

    }

    public String getInstructionsInFile(Integer productId, String filename) {
    	
    	if( nachaPopInternal ) {
    		try {
				return objectMapper.writeValueAsString(
						nachaFilesService.getExtractInstructions(filename)
				);
			} catch (JsonProcessingException | FileNotFoundException e) {
				throw new RuntimeException(e);
			}
    	}  else {
    		// should never be called
    		return null;
    	}

    }


    public boolean getStatus() {
    	
    	if( nachaPopInternal ) {
    		return true;
    	}    	
    	
    	// FIXME the following code is used only by junit tests
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        String response = null;
        String authHeaderValue = HttpUtility.getBasicAuthenticationHeader(systemConfigService.getStringConfig(SystemConfigEnums.Key.NACHAPOP_USERNAME),
                                                                          systemConfigService.getStringConfig(SystemConfigEnums.Key.NACHAPOP_PASSWORD));
        try {

            response = retryService.retry( ()->
                    apiSvc.get(nachaApiBase + EP_GET_STATUS, "",
                            CONTENT_TYPE_HEADER, APPLICATION_JSON, ACCEPT, APPLICATION_JSON,
                            AUTH_HEADER, authHeaderValue));

            JsonNode json = mapper.readTree(response);

            if(json.hasNonNull("status") && json.get("status").asBoolean()) {
                return true;
            } else {
                log.error( response);
                return false;
            }

        } catch (Exception ex) {
            log.error( ex.toString(), ex);
            return false;
        }


    }

    private JsonNode getOfacError() {
        ArrayNode node = new ObjectMapper().createArrayNode();
        node.add("Unable to perform OFAC check, blocking by default");
        return node;
    }

    private JsonNode ofacCheck(double minMatch, List<NachaOfacQuery> queries) {

        if (this.nachaPopInternal) {

            ArrayNode ofacCheck = this.moovService.ofacCheck(minMatch, queries);
            return cleanOfacCheckResponse(ofacCheck);
        }
    	
    	// FIXME the following code is used only by junit tests

        return retryService.retry(() -> {
            ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            String authHeaderValue = HttpUtility.getBasicAuthenticationHeader(systemConfigService.getStringConfig(SystemConfigEnums.Key.NACHAPOP_USERNAME),
                                                                              systemConfigService.getStringConfig(SystemConfigEnums.Key.NACHAPOP_PASSWORD));

            String response = apiSvc.post(nachaApiBase + EP_OFAC_CHECK, "", mapper.writeValueAsString(queries),
                    CONTENT_TYPE_HEADER, APPLICATION_JSON, ACCEPT, APPLICATION_JSON,
                    AUTH_HEADER, authHeaderValue);

            return mapper.readTree(response);
        });
    }

    private JsonNode cleanOfacCheckResponse(JsonNode node) {
        if (node == null || node.isNull()) {
            return NullNode.instance;
        }

        if (node.isObject()) {
            ObjectNode cleaned = JsonNodeFactory.instance.objectNode();
            node.fields().forEachRemaining(entry -> {
                JsonNode cleanedChild = cleanOfacCheckResponse(entry.getValue());
                if (isNotEmpty(cleanedChild)) {
                    cleaned.set(entry.getKey(), cleanedChild);
                }
            });
            return cleaned;
        }

        if (node.isArray()) {
            ArrayNode cleaned = JsonNodeFactory.instance.arrayNode();
            for (JsonNode item : node) {
                JsonNode cleanedItem = cleanOfacCheckResponse(item);
                if (isNotEmpty(cleanedItem)) {
                    cleaned.add(cleanedItem);
                }
            }
            return cleaned;
        }
        return node;
    }

    private static boolean isNotEmpty(JsonNode node) {
        if (node == null || node.isNull()) return false;
        if (node.isObject()) return node.size() != 0;
        if (node.isArray()) return node.size() != 0;
        return true;
    }

    public void setNachaApiBase(String nachaApiBase) {
        this.nachaApiBase = nachaApiBase;
    }

    public boolean cancelInstruction(String instructionId) {
    	
    	if( nachaPopInternal ) {
    		try {
    			instructionService.cancelInstruction(instructionId);
    			return true;
    		} catch(Exception e) {
    			log.error("Error cancelling instruction: " + instructionId, e);
    			return false;
    		}
    	}    	
    	
    	// FIXME the following code is used only by junit tests

        String url = EP_UPDATE_INSTRUCTION.replace("{id}", instructionId);
        String authHeaderValue = HttpUtility.getBasicAuthenticationHeader(systemConfigService.getStringConfig(SystemConfigEnums.Key.NACHAPOP_USERNAME),
                                                                          systemConfigService.getStringConfig(SystemConfigEnums.Key.NACHAPOP_PASSWORD));
        try {
            int code = apiSvc.delete(nachaApiBase + url, "", "",
                    CONTENT_TYPE_HEADER, APPLICATION_JSON, ACCEPT, APPLICATION_JSON,
                    AUTH_HEADER, authHeaderValue);

            return code < 400;

        } catch (IOException | InterruptedException | URISyntaxException | java.io.IOException ex) {
            log.error( ex.toString(), ex);
            return false;
        }

    }

    public void ofacCheckCustomer(Customer customer, AddressRequest address, Integer ofacId) {

        ofacCheckCustomer(customer, address, ofacId, false);
    }

    public void ofacCheckCustomer(Customer customer, AddressRequest address, Integer ofacId, boolean isScheduledCheck) {

        String name;
        WhitelistEntry.EntityType entityType;
        if (customer.getType() == CustomerTypes.BUSINESS) {
            name = customer.nameFormatted();
            entityType = WhitelistEntry.EntityType.BUSINESS;
        } else {
            name = customer.getFirstName() + " " + customer.getLastName();
            entityType = WhitelistEntry.EntityType.INDIVIDUAL;
        }
        String filteredName = OFACNamesUtility.filterName(name);

        // Skip Internal Watchlist check for Individual Customers in Night OFAC Run
        boolean skipInternalWatchlistCheck = isScheduledCheck && Objects.equals(customer.getType(), CustomerTypes.INDIVIDUAL);

        List<NachaOfacQuery> queries = createQueries(name, address);
        List<NachaOfacQuery> filteredQueries = createQueries(filteredName, address);

        OFACResult ofacResult;
        OFACResult filteredOfacResult;
        try {
            // If ofacId is not null, means it is a retry attempt
            if (ofacId != null) {
                ofacResult = processOfacCheck(queries, customer, ofacId, skipInternalWatchlistCheck);
                log.info("OFAC retry result for customer {}: Status = {}, Result = {}",
                        customer.getId(), ofacResult.getStatus(), ofacResult);
                filteredOfacResult = processOfacCheck(filteredQueries, customer, ofacId, skipInternalWatchlistCheck);
                log.info("Filtered OFAC retry result for customer {}: Status = {}, Result = {}",
                        customer.getId(), filteredOfacResult.getStatus(), filteredOfacResult);
            } else {
                ofacResult = processOfacCheck(queries, customer, null, skipInternalWatchlistCheck);
                log.info("OFAC check result for customer {}: Status = {}, Result = {}",
                        customer.getId(), ofacResult.getStatus(), ofacResult);
                filteredOfacResult = processOfacCheck(filteredQueries, customer, null, skipInternalWatchlistCheck);
                log.info("Filtered OFAC check result for customer {}: Status = {}, Result = {}",
                        customer.getId(), filteredOfacResult.getStatus(), ofacResult);
            }

            boolean isOfacResultEmpty = (ofacResult.getOfacResult().isEmpty() || ofacResult.getOfacResult().equals("[]"));
            boolean isFilteredOfacResultEmpty = (filteredOfacResult.getOfacResult().isEmpty() ||
                                                    filteredOfacResult.getOfacResult().equals("[]"));

            // TODO: Only call whitelists if the OFAC status is REVIEW?
            boolean isWhiteListed = whitelistService.isWhitelisted(entityType, customer.getId(), null, ofacResult);
            boolean isFilteredWhiteListed = whitelistService.isWhitelisted(entityType, customer.getId(), null, filteredOfacResult);

            if (isOfacResultEmpty && !isFilteredOfacResultEmpty) {
                ofacResult.setOfacResult(filteredOfacResult.getOfacResult());
            }

            if (isWhiteListed || isFilteredWhiteListed) {
                ofacResult.setStatus(OFACEnums.Status.CLEARED);
                ofacResult.setNote("Customer is white listed");

            } else if (!isOfacResultEmpty || !isFilteredOfacResultEmpty) {
                customer.setStatus(CustomerEnums.CustomerStatuses.BLOCKED);
                ofacResult.setStatus(OFACEnums.Status.REVIEW);
            } else {
                ofacResult.setStatus(OFACEnums.Status.NO_MATCH);
            }

            // If ofacId is not null, means it is a retry attempt
            if ((ofacId != null) && !Objects.equals(ofacResult.getStatus(), OFACEnums.Status.ERROR)) {

                if((isOfacResultEmpty && isFilteredOfacResultEmpty) &&
                  (Objects.equals(ofacResult.getStatus(), OFACEnums.Status.CLEARED) ||
                   Objects.equals(ofacResult.getStatus(), OFACEnums.Status.NO_MATCH))  &&
                   customer.getProhibitedEntityId() == null && isCustomerActiveRegardingCipStatus(customer) &&
                   !Objects.equals(customer.getStatus(), CustomerEnums.CustomerStatuses.DELETED)) {
                    customer.setStatus(CustomerEnums.CustomerStatuses.ACTIVE);
                }

                ofacResult.setStatus(OFACEnums.Status.RETRIED);
            }
        } catch (Exception ex) {
            log.error( "Unable to perform OFAC check", ex);
            customer.setStatus(CustomerEnums.CustomerStatuses.BLOCKED);
            ofacResult = createOFACResultFromResponseForCustomer(getOfacError(), customer);
            ofacResult.setStatus(OFACEnums.Status.ERROR);
        }

        saveOfacCheckDetails(queries, ofacResult);
        ofacResult = ofacService.save(ofacResult);
        createOfacAlertIfNeeded(ofacResult, customer);
        customer.setOfacId(ofacResult.getId());

        customerService.save(customer);
    }

    private void createOfacAlertIfNeeded(OFACResult ofacResult, Customer customer) {
        if (Objects.equals(ofacResult.getStatus(), OFACEnums.Status.REVIEW)) {
            // Creating Alert for failed OFAC Check
            String tenantId = customer.getProduct() != null ? customer.getProduct().getTenantId() : null;
            Alert alert = alertCreationService.createAlert(AlertEnums.Type.OFAC, AlertEnums.ContextType.OFAC,
                    ofacResult, null, tenantId, AlertEnums.OfacAlertParam.ENTITY.name());
            if(alert != null){
                ofacResult.setAlertId(alert.getId());
                ofacService.save(ofacResult);
            }
        }
    }

    public OFACResult ofacCheckContact(Contact contact, Ach ach, Wire wire, Product product) {
        return  ofacCheckContact(contact, ach, wire, product, null, true);
    }

    public OFACResult ofacCheckContact(Contact contact, Ach ach, Wire wire, Product product, AddressRequest address, Boolean sendCounterpartyWebhook) {
    	long t = System.currentTimeMillis();

        String name = contact.getName();
        String filteredName = OFACNamesUtility.filterName(name);
        ContactEnums.ContactStatus oldStatus = contact.getStatus();

        List<NachaOfacQuery> queries = createQueries1(name, ach, wire, address);
        List<NachaOfacQuery> filteredQueries = createQueries1(filteredName, ach, wire, address);

        OFACResult ofacResult;
        OFACResult filteredOfacResult;
        try {
            ofacResult = processOfacCheck(queries, contact, product);

            // skip processing the filtered check if the filtered
            // and unfiltered name are the same
            filteredOfacResult =
            		name.equals(filteredName) ? ofacResult :
            		processOfacCheck(filteredQueries, contact, product);

            boolean isOfacResultEmpty = (ofacResult.getOfacResult().isEmpty() || ofacResult.getOfacResult().equals("[]"));
            boolean isFilteredOfacResultEmpty = (filteredOfacResult.getOfacResult().isEmpty() ||
                                                  filteredOfacResult.getOfacResult().equals("[]"));


            boolean isWhiteListed = whitelistService.isWhitelisted(WhitelistEntry.EntityType.COUNTERPARTY, contact.getId(), null, ofacResult);
            boolean isFilteredWhiteListed = whitelistService.isWhitelisted(WhitelistEntry.EntityType.COUNTERPARTY, contact.getId(), null, filteredOfacResult);

            if (isOfacResultEmpty && !isFilteredOfacResultEmpty) {
                ofacResult.setOfacResult(filteredOfacResult.getOfacResult());
            }

            if (isWhiteListed || isFilteredWhiteListed) {
                if (prohibitedEntitiesService.isContactProhibtedStatusClear(ach, wire) &&
                    !Objects.equals(contact.getStatus(), ContactEnums.ContactStatus.DELETED)) {
                    contact.setStatus(ContactEnums.ContactStatus.ACTIVE);
                }
                ofacResult.setStatus(OFACEnums.Status.CLEARED);
                ofacResult.setNote("Contact is white listed");
            } else if (!isOfacResultEmpty || !isFilteredOfacResultEmpty) {
                contact.setStatus(ContactEnums.ContactStatus.BLOCKED);
                ofacResult.setStatus(OFACEnums.Status.REVIEW);
            } else {
                if (prohibitedEntitiesService.isContactProhibtedStatusClear(ach, wire) &&
                    !Objects.equals(contact.getStatus(), ContactEnums.ContactStatus.DELETED)) {
                    contact.setStatus(ContactEnums.ContactStatus.ACTIVE);
                }
                ofacResult.setStatus(OFACEnums.Status.NO_MATCH);
            }
        } catch (Exception ex) {

            log.error( "Unable to perform OFAC check", ex);
            contact.setStatus(ContactEnums.ContactStatus.NEEDS_OFAC);

            ofacResult = createOFACResultFromResponseForContact(getOfacError(), contact);
            ofacResult.setStatus(OFACEnums.Status.ERROR);
        }

        saveOfacCheckDetails(queries, ofacResult);
        ofacResult = ofacService.save(ofacResult);

        if (Objects.equals(ofacResult.getStatus(), OFACEnums.Status.REVIEW)) {
            // Creating Alert for failed OFAC Check
            Alert alert =  alertCreationService.createContactOFACAlert(AlertEnums.Type.OFAC, AlertEnums.ContextType.OFAC,
                    ofacResult, contact);
            if(alert != null){
                ofacResult.setAlertId(alert.getId());
                ofacService.save(ofacResult);
            }
        }

        contact.setOfacId(ofacResult.getId());

        if (oldStatus != null && oldStatus != contact.getStatus() && sendCounterpartyWebhook) {
            webHookEventServices.contactStatusChange(contact);
        }

        contactService.save(contact);

        log.info("ofacCheckContact: " + (System.currentTimeMillis() - t) + "ms" );
        return ofacResult;
    }

    public static OFACResult createOFACResultFromResponseForContact(JsonNode response, Contact contact) {
        OFACResult ofacResult = new OFACResult();
        ofacResult.setOfacResult(String.valueOf(response));
        ofacResult.setCounterpartyId(contact.getId());
        ofacResult.setName(contact.getName());
        return ofacResult;
    }

    public static OFACResult createOFACResultFromResponseForCustomer(JsonNode response, Customer customer) {
        OFACResult ofacResult = new OFACResult();
        ofacResult.setOfacResult(OFACNamesUtility.filterNonASCIICharacters(String.valueOf(response)));
        if (customer.getType() == CustomerTypes.BUSINESS) {
            ofacResult.setBusinessId(customer.getId());
            ofacResult.setName(customer.nameFormatted());
        } else if (customer.getType() == CustomerTypes.INDIVIDUAL) {
            ofacResult.setIndividualId(customer.getId());
            ofacResult.setName(customer.nameFormatted());
        }
        return ofacResult;
    }

    public OFACResult updateOFACResultFromResponseForCustomer(JsonNode response, Integer ofacId) {
        OFACResult ofacResult = ofacService.findOriginalById(ofacId);
        ofacResult.setOfacResult(OFACNamesUtility.filterNonASCIICharacters(String.valueOf(response)));

        return ofacResult;
    }

    public OFACResult ofacCheckOutboundAchTransaction(Customer customer, Contact counterparty, String paymentId, String tenantId, Product p) {

        String originatorName = customer != null ? customer.nameFormatted() : null;
        String receiverName = counterparty != null ? counterparty.getName() : null;

        // Creating query for OFAC check
        List<NachaOfacQuery> queries = new ArrayList<>();
        if (!StringUtils.isEmpty(originatorName)) {
            queries.add(new NachaOfacQuery(NachaOfacQuery.OfacCheckQueryType.FIELD, "originatorName", originatorName));
        }
        if (!StringUtils.isEmpty(receiverName)) {
            queries.add(new NachaOfacQuery(NachaOfacQuery.OfacCheckQueryType.FIELD, "receiverName", receiverName));
        }

        OFACResult ofacResult = transactionOfacCheck(queries, paymentId, tenantId, p);

        // ACH Outbound
        // originatorName -> customer
        // receiverName -> counterparty
        if(ofacResult.getStatus().equals(OFACEnums.Status.REVIEW)) {
            boolean isWhitelisted = whitelistService.isWhitelistedTransaction(customer, "originatorName",
                    counterparty, "receiverName", ofacResult);
            if (isWhitelisted) {
                ofacResult.setStatus(OFACEnums.Status.CLEARED);
                ofacResult.setNote("Customer and contact are white listed");
                ofacService.save(ofacResult);
            }
        }
        return ofacResult;
    }

    public OFACResult ofacCheckInboundAchTransaction(Customer customer, String originatorName, String receiverName,
                                                     String paymentId, String tenantId, Product p) {

        // Creating query for OFAC check
        List<NachaOfacQuery> queries = new ArrayList<>();
        if (!StringUtils.isEmpty(originatorName)) {
            queries.add(new NachaOfacQuery(NachaOfacQuery.OfacCheckQueryType.FIELD, "originatorName", originatorName));
        }
        if (!StringUtils.isEmpty(receiverName)) {
            queries.add(new NachaOfacQuery(NachaOfacQuery.OfacCheckQueryType.FIELD, "receiverName", receiverName));
        }

        OFACResult ofacResult = transactionOfacCheck(queries, paymentId, tenantId, p);

        // ACH Inbound (does not have counterparty)
        // receiverName -> customer
        if(ofacResult.getStatus().equals(OFACEnums.Status.REVIEW)) {
            boolean isWhitelisted = whitelistService.isWhitelistedTransaction(customer, "receiverName",
                    null, "originatorName", ofacResult);
            if (isWhitelisted) {
                ofacResult.setStatus(OFACEnums.Status.CLEARED);
                ofacResult.setNote("Customer and contact are white listed");
                ofacService.save(ofacResult);
            }
        }
        return ofacResult;
    }

    public OFACResult ofacCheckOutboundWireTransaction(Customer customer, Contact counterparty, String beneficiaryBankName,
                                                       String paymentId, String tenantId, Product p) {

        String originatorName = customer != null ? customer.nameFormatted() : null;
        String beneficiaryName = counterparty != null ? counterparty.getName() : null;

        // Creating query for OFAC check
        List<NachaOfacQuery> queries = new ArrayList<>();
        if (!StringUtils.isEmpty(originatorName)) {
            queries.add(new NachaOfacQuery(NachaOfacQuery.OfacCheckQueryType.FIELD, "originatorName", originatorName));
        }
        if (!StringUtils.isEmpty(beneficiaryName)) {
            queries.add(new NachaOfacQuery(NachaOfacQuery.OfacCheckQueryType.FIELD, "beneficiaryName", beneficiaryName));
        }
        if (!StringUtils.isEmpty(beneficiaryBankName)) {
            queries.add(new NachaOfacQuery(NachaOfacQuery.OfacCheckQueryType.FIELD, "beneficiaryBankName", beneficiaryBankName));
        }

        OFACResult ofacResult = transactionOfacCheck(queries, paymentId, tenantId, p);

        // WIRE Outbound
        // originatorName -> customer
        // beneficiaryName -> counterparty
        if(ofacResult.getStatus().equals(OFACEnums.Status.REVIEW)) {
            boolean isWhitelisted = whitelistService.isWhitelistedTransaction(customer, "originatorName",
                    counterparty, "beneficiaryName", ofacResult);
            if (isWhitelisted) {
                ofacResult.setStatus(OFACEnums.Status.CLEARED);
                ofacResult.setNote("Customer and contact are white listed");
                ofacService.save(ofacResult);
            }
        }
        return ofacResult;
    }

    public OFACResult ofacCheckInboundWireTransaction(Customer customer, Contact counterparty, String originatorName,
                                                      String originatorBankName, String beneficiaryName,
                                                      String beneficiaryBankName, String paymentId, String tenantId, Product p) {

        // Creating query for OFAC check
        List<NachaOfacQuery> queries = new ArrayList<>();
        if (!StringUtils.isEmpty(originatorName)) {
            queries.add(new NachaOfacQuery(NachaOfacQuery.OfacCheckQueryType.FIELD, "originatorName", originatorName));
        }
        if (!StringUtils.isEmpty(originatorBankName)) {
            queries.add(new NachaOfacQuery(NachaOfacQuery.OfacCheckQueryType.FIELD, "originatorBankName", originatorBankName));
        }
        if (!StringUtils.isEmpty(beneficiaryName)) {
            queries.add(new NachaOfacQuery(NachaOfacQuery.OfacCheckQueryType.FIELD, "beneficiaryName", beneficiaryName));
        }
        if (!StringUtils.isEmpty(beneficiaryBankName)) {
            queries.add(new NachaOfacQuery(NachaOfacQuery.OfacCheckQueryType.FIELD, "beneficiaryBankName", beneficiaryBankName));
        }

        OFACResult ofacResult = transactionOfacCheck(queries, paymentId, tenantId, p);

        // WIRE Inbound
        // originatorName -> counterparty
        // beneficiaryName -> customer
        if(ofacResult.getStatus().equals(OFACEnums.Status.REVIEW)) {
            boolean isWhitelisted = whitelistService.isWhitelistedTransaction(customer, "beneficiaryName",
                    counterparty, "originatorName", ofacResult);
            if (isWhitelisted) {
                ofacResult.setStatus(OFACEnums.Status.CLEARED);
                ofacResult.setNote("Customer and contact are white listed");
                ofacService.save(ofacResult);
            }
        }
        return ofacResult;
    }


    public OFACResult ofacCheckSimpleTransaction(String payeeName, String paymentId, String tenantId, Product p) {

        // Creating query for OFAC check
        List<NachaOfacQuery> queries = new ArrayList<>();
        if (!StringUtils.isEmpty(payeeName)) {
            queries.add(new NachaOfacQuery(NachaOfacQuery.OfacCheckQueryType.FIELD, "payeeName", payeeName));
        }

        return transactionOfacCheck(queries, paymentId, tenantId, p);
    }

    
    
    private OFACResult transactionOfacCheck(List<NachaOfacQuery> queries, String paymentId, String tenantId, Product p) {

        OFACResult ofacResult = new OFACResult();
        ofacResult.setTenantId(tenantId);
        try {

        	// the method being called here knows how to decide
        	// if Moov should be used directly or if Nacha POP
            JsonNode matches = this.ofacCheck(ofacThresholdValue(p), queries);
            matches = internalWatchlistService.runInternalWatchlistCheck(matches, queries, ofacThresholdValue(p));
            log.info("Match results: " + matches);

            ofacResult.setOfacResult(String.valueOf(matches));
            ofacResult.setTransactionPaymentId(paymentId);

            if (ofacResult.getOfacResult().isEmpty() || ofacResult.getOfacResult().equals("[]")) {
                ofacResult.setStatus(OFACEnums.Status.NO_MATCH);
            } else {
                ofacResult.setStatus(OFACEnums.Status.REVIEW);
            }
        } catch (Exception e) {
            log.error("Unbale to perform OFAC check", e);
            ofacResult.setOfacResult(String.valueOf(getOfacError()));
            ofacResult.setTransactionPaymentId(paymentId);
            ofacResult.setStatus(OFACEnums.Status.ERROR);
        }
        log.info("OFAC result: " + ofacResult.getOfacResult());
        saveOfacCheckDetails(queries, ofacResult);
        return ofacService.save(ofacResult);
    }

    public NachaInstruction reverse(String id, String addenda) throws JsonProcessingException, DeclineException {
        
    	if( nachaPopInternal ) {
    		InstructionReversalRequest req = new InstructionReversalRequest();
    		if(addenda != null) {
    			req.getAddenda().add(addenda);
    		}
    		return NachaInstruction.fromPOP( instructionService.reverseInstruction(id, req) );

    	} else {
    		// should never be called
    		return null;
    	}
    	
        
    }

    @Autowired
    TenantContext tenantContext;
    
    @Autowired
    NachaScheduledEventService scheduled;
    
	public void eventTrigger(ScheduledEventTrigger trigger) {
		
    	if( nachaPopInternal ) {
    		scheduled.trigger(trigger);
    		return;
    	}    	
	}

    private List<NachaOfacQuery> createQueries(String name, AddressRequest address) {
    	
    	List<NachaOfacQuery> queries = new ArrayList<>();
        queries.add( new NachaOfacQuery(NachaOfacQuery.OfacCheckQueryType.FIELD, "name", name));
        createQueries(queries, "customer.address", address);
        return queries;
    }

    private OFACResult processOfacCheck(List<NachaOfacQuery> queries, Customer customer, Integer ofacId, boolean skipInternalWatchlist) {
        JsonNode matches = ofacCheck(ofacThresholdValue(customer.getProduct()), queries);

        if (!skipInternalWatchlist) {
            matches = internalWatchlistService.runInternalWatchlistCheck(matches, queries, ofacThresholdValue(customer.getProduct()));
        }
        log.info("Match results: " + matches);
        // Updates existing record if ofacId present, otherwise create new
        if (ofacId != null) {
            return updateOFACResultFromResponseForCustomer(matches, ofacId);
        } else {
            return createOFACResultFromResponseForCustomer(matches, customer);
        }
    }

    private List<NachaOfacQuery> createQueries1(String name, Ach ach, Wire wire, AddressRequest address) {
        List<NachaOfacQuery> queries = new ArrayList<>();
        if (name != null && !name.isEmpty()) {
            queries.add(new NachaOfacQuery(NachaOfacQuery.OfacCheckQueryType.FIELD, "name", name));
        }

        if(ach != null) {
        	if(!StringUtils.isBlank(ach.bankName)) {
                queries.add(new NachaOfacQuery(NachaOfacQuery.OfacCheckQueryType.FIELD, "ach.bankName", ach.bankName));
        	}

        	createQueries(queries, "ach.address", ach.address);
        }

        if(wire != null) {
        	if(!StringUtils.isBlank(wire.beneficiaryFIName)) {
                queries.add(new NachaOfacQuery(NachaOfacQuery.OfacCheckQueryType.FIELD, "wire.beneficiaryFIName", wire.beneficiaryFIName));
        	}
        	if(!StringUtils.isBlank(wire.intermediaryFIName)) {
                queries.add(new NachaOfacQuery(NachaOfacQuery.OfacCheckQueryType.FIELD, "wire.intermediaryFIName", wire.intermediaryFIName));
        	}

        	createQueries(queries, "wire.address", wire.address);
        	createQueries(queries, "wire.beneficiaryFIAddress", wire.beneficiaryFIAddress);
        	createQueries(queries, "wire.intermediaryFIAddress", wire.intermediaryFIAddress);
        }

        if (address != null) {
            // For V2 - has only 1 counterparty address
            createQueries(queries, "wire.address", address);
        }

        return queries;
    }

	private void createQueries(List<NachaOfacQuery> queries, String key, AddressRequest address) {
		if(address != null) {
		    
		    queries.add(new NachaOfacQuery(NachaOfacQuery.OfacCheckQueryType.ADDRESS,
		    		key,
		    		address.getLine1(),
		    		address.getLine2(),
		    		address.getCity(),
		    		address.getState(),
		    		address.getPostalCode()
		    		));

		}
	}

    private OFACResult processOfacCheck(List<NachaOfacQuery> queries, Contact contact,Product p) {
        JsonNode matches = ofacCheck(ofacThresholdValue(p),queries);
        matches = internalWatchlistService.runInternalWatchlistCheck(matches, queries, ofacThresholdValue(p));
        log.info("Match results: " + matches);
        return createOFACResultFromResponseForContact(matches, contact);
    }

    public void saveOfacCheckDetails(List<NachaOfacQuery> queries, OFACResult ofacResult) {
        for (NachaOfacQuery query : queries) {
            OfacCheckDetails ofacCheckDetails = new OfacCheckDetails();
            ofacCheckDetails.setCheckKey(query.getKey());
            if (Objects.equals(query.getType(), NachaOfacQuery.OfacCheckQueryType.ADDRESS)) {
                ofacCheckDetails.setCheckValue(query.addressToString());
            } else {
                ofacCheckDetails.setCheckValue(query.getValue());
            }
            ofacResult.addOfacCheckKey(ofacCheckDetails);
        }
    }
    
    public boolean isNachaPopInternal() {
    	return nachaPopInternal;
    }

	public void setNachaPopInternal(boolean nachaPopInternal) {
		this.nachaPopInternal = nachaPopInternal;
	}

	private double ofacThresholdValue(Product p) {
		return p == null ? 0 : p.ofacThresholdValue();
	}

}
