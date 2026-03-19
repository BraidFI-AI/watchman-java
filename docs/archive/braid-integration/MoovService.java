package io.ropechain.api.service;

import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.util.InMemoryResource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import io.ropechain.api.data.nacha.NachaOfacQuery;
import io.ropechain.api.enums.SystemConfigEnums;
import io.ropechain.api.enums.TransactionEnums.AchOffsetType;
import io.ropechain.api.exception.BackendConnectivityException;
import io.ropechain.api.model.OfacCheckQuery;


// WARNING //

// Do not use this code until Nacha POP has finished
// moving into Braid. It will break if Nacha POP
// is still sitting in front of Moov.
//
// When in doubt, check NachaService.isNachaPopInternal()
//

@Service
@Slf4j
@EnableScheduling
public class MoovService {

	private static final int MAX_WATCHMAN_TRIES = 10;

	private static final long WATCHMAN_RETRY_DELAY = 100L;
	
	// We will cache the results from watchman for a limited
	// time based on the system config OFAC_CACHE_AGE_IN_MINUTES.
	// This cache is local to the JVM so it will not prevent
	// other nodes from repeating the same calls to watchman.
	// The default is 10 minutes.
	
	private static class CacheResult {
		public long whenCached = System.currentTimeMillis();
		public CacheResult(JsonNode value) {
			this.value = value;
		}
		public JsonNode value;
		public long age() {
			return System.currentTimeMillis() - whenCached;
		}
	}
	
	private final Map<List<Object>,CacheResult> watchmanCache =
			new ConcurrentHashMap<>();
	
	// periodically check the cache so it doesn't
	// just sit there taking up memory forever
	@Scheduled(fixedDelay = 1000 * 60 * 60)
	void clearCache() {
		long maxAge = this.cacheAgeMillis();
		Iterator<Entry<List<Object>, CacheResult>> i = watchmanCache.entrySet().iterator();
		while(i.hasNext()) {
			Entry<List<Object>, CacheResult> e = i.next();
			if(e.getValue().age() > maxAge) {
				log.info("Removing from cache: " + e.getKey());
				i.remove();
			}
		}
	}	

	@Autowired
	RestTemplate template;
	
	@Value("${moov.server}")
	String moovServer;
	
	@Value("${moov.port}")
	String moovPort;

	@Value("${watchman.server}")
	String watchmanServer;
	
	@Value("${watchman.port}")
	String watchmanPort;

	@Value("${watchman.send.minMatch:true}")
	boolean sendMinMatch;

	// for experimental purposes only, NEVER enable in a real system
	@Value("${watchman.ignore.empty.response:false}")
	boolean ignoreEmptyResponse;

	@Value("${moov.fed:localhost:8086}")
	String moovFedUrl;
	
	@Autowired
	SystemConfigService systemConfigService;

	@Autowired
	ObjectMapper jsonMapper;

	@Autowired
	StatsDService statsDService;
	
	@Autowired
	AchConfigService achConfigService;
	
	public String getMoovURL() {
		return "http://" + moovServer + ":" + moovPort;
	}

	public String getWatchmanURL() {
		return "http://" + watchmanServer + ":" + watchmanPort;
	}

	public String getMoovFedURL() {
		return "http://" + moovFedUrl;
	}

	public String getWatchmanPort() {
		return watchmanPort;
	}

	public void setWatchmanPort(String port) {
		watchmanPort = port;
	}

	// For testing
	public void setMoovURL(String server, String port) {
		moovServer = server;
		moovPort = port;
	}

	// For testing
	public void setMoovFedURL(String server, String port) {
		moovServer = server;
		moovPort = port;
	}
	
	boolean ping(String url) {
		try {
			return template.getForEntity(url,String.class)
					.getStatusCode()
					.is2xxSuccessful();
		} catch (Exception e) {
			log.error("Error with PING URL: " + url, e);
			return false;
		}
	}

	public boolean isWatchmanUp() {
		return ping(getWatchmanURL()+ "/ping" );
	}

	public boolean isMoovUp() {
		return ping(getMoovURL()+ "/ping" );
	}

	public boolean isMoovFedUp() {
		return ping(getMoovFedURL()+ "/ping" );
	}

	@Value("${flatten.batches:true}")
	private boolean flattenBatches;
	
	public String convertToRawNacha(JsonNode fileJson) {
		Long start = System.currentTimeMillis();
		HttpHeaders headers = new HttpHeaders();
	      headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
	      headers.setContentType(MediaType.APPLICATION_JSON);
	      HttpEntity <JsonNode> entity = new HttpEntity<JsonNode>(fileJson,headers);
	      
	      JsonNode returnData = template.exchange(
	    		  getMoovURL()+"/files/create",
	    		  HttpMethod.POST, entity, ObjectNode.class).getBody();

	      log.debug("Moov says: " + returnData);
	      
	      if(flattenBatches && achConfigService.getConfig().getOffsetType() != AchOffsetType.AUTO_PER_BATCH_TOTAL  ) {
	    	  returnData = flatten(returnData.get("id").asText());
	      }

	      headers = new HttpHeaders();
	      headers.setAccept(Arrays.asList(MediaType.TEXT_PLAIN));
	      HttpEntity <String> entity2 = new HttpEntity<String>(headers);
		      
	      String rawNacha = template.exchange(
	    		  getMoovURL()+"/files/" + returnData.get("id").asText() + "/contents",
	    		  HttpMethod.GET, entity2, String.class).getBody();
		  statsDService.timing("moov.ach.to-nacha", System.currentTimeMillis() - start);
		  return rawNacha;
	}

	private JsonNode flatten(String id) {
		HttpHeaders headers = new HttpHeaders();
	    headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
	    headers.setContentType(MediaType.APPLICATION_JSON);
	    HttpEntity <JsonNode> entity = new HttpEntity<JsonNode>(headers);
	    
	    ObjectNode returnData = template.exchange(
	  		  getMoovURL()+"/files/"+id+"/flatten",
	  		  HttpMethod.POST, entity, ObjectNode.class).getBody();
	    
	    return returnData;
	}

	public JsonNode sendToMoovToTest(JsonNode fileJson) {
		log.info("Send JSON to moov for test");
		Long start = System.currentTimeMillis();
		HttpHeaders headers = new HttpHeaders();
	    headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
	    headers.setContentType(MediaType.APPLICATION_JSON);
	    HttpEntity <JsonNode> entity = new HttpEntity<JsonNode>(fileJson,headers);
	    
	    ObjectNode returnData = template.exchange(
	  		  getMoovURL()+"/files/create",
	  		  HttpMethod.POST, entity, ObjectNode.class).getBody();
		statsDService.timing("moov.ach.validate", System.currentTimeMillis() - start);
	    
	    return returnData;
	
	}	
	
	public ResponseEntity<JsonNode> sendJsonToMoovWithId(String id, JsonNode node) throws RestClientException {
		log.info("Send JSON to moov: " + id);
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity <JsonNode> entity = new HttpEntity<JsonNode>(
				node.has("file") ? node.get("file") : node
				,headers);
		
		ResponseEntity<JsonNode> postResponse  = template.exchange(
				  getMoovURL()+"/files/"+id,
			  HttpMethod.POST, entity, JsonNode.class);
		return postResponse;
	}	
	
	public boolean existsOnMoov(String id) {

		log.info("Checking if exists on moov: " + id);
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
		HttpEntity <String> entity = new HttpEntity<String>(headers);
		  
		try {
			ResponseEntity<JsonNode> response = template.exchange(
					  getMoovURL()+"/files/"+id,
				  HttpMethod.GET, entity, JsonNode.class);
			
			log.debug("Check response from Moov: " + response.getBody());
			
			return response.getStatusCode().is2xxSuccessful();
		} catch (HttpClientErrorException e) {
			log.error(e.getResponseBodyAsString(), e);
		}
		
		return false;
	}	

	public String getFileContentsFromMoov(String id) {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Arrays.asList(MediaType.TEXT_PLAIN));
		HttpEntity <String> entity = new HttpEntity<String>(headers);
		  
		String returnData = template.exchange(
				  getMoovURL()+"/files/"+id+"/contents",
			  HttpMethod.GET, entity, String.class).getBody();
		return returnData;
	}
	
	public JsonNode validateOnMoov(String id) throws RestClientException {
		log.info("Validating on Moov:" + id);
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
		HttpEntity <String> entity = new HttpEntity<String>(headers);

		JsonNode returnData = template.exchange(
				  getMoovURL()+"/files/"+id+"/validate",
			  HttpMethod.GET, entity, JsonNode.class).getBody();
		

		return returnData;
	}	
	
	public void deleteOnMoov(String id) throws RestClientException {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
		HttpEntity <String> entity = new HttpEntity<String>(headers);
		  
		ObjectNode returnData = template.exchange(
				  getMoovURL()+"/files/"+id,
			  HttpMethod.DELETE, entity, ObjectNode.class).getBody();
	}	
	
	public ArrayNode ofacCheckFile(double minMatch, JsonNode fileNode) {

		ArrayNode matches = jsonMapper.createArrayNode();
		
		ofacCheckFile(minMatch, fileNode, matches);
		
		return matches;
	}

	public ObjectNode addNachaToMoov(String achData) throws RestClientException {
		return addNachaToMoov( new InMemoryResource(achData.getBytes())  );
	}

	public ObjectNode addNachaToMoov(Resource achData) throws RestClientException {
		Long start = System.currentTimeMillis();
		HttpHeaders headers = new HttpHeaders();
		  headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
		  headers.setContentType(MediaType.TEXT_PLAIN);
		  HttpEntity <Resource> entity = new HttpEntity<Resource>(achData,headers);

		  ObjectNode returnData = template.exchange(
				  getMoovURL()+"/files/create?bypassCompanyIdentificationMatch=true",
				  HttpMethod.POST, entity, ObjectNode.class).getBody();
		statsDService.timing("moov.ach.convert", System.currentTimeMillis() - start);


		  log.debug("Moov says:\n\n" + returnData );
		return returnData;
	}
	
	
	
	public void ofacCheckFile(double minMatch, JsonNode fileNode, ArrayNode matches) {
		JsonNode batches = fileNode.get("batches");
		if(batches != null && batches.isArray()) {
			
			for ( JsonNode batch : batches) {

			
				JsonNode batchHeader = batch.get("batchHeader");
								
				ofacCheckOneValue(batchHeader, "companyIdentification","q", matches,minMatch);
				ofacCheckOneValue(batchHeader, "companyDiscretionaryData","q", matches,minMatch);
				ofacCheckOneValue(batchHeader, "companyName","name", matches,minMatch);
			
				JsonNode entries = batch.get("entryDetails");
				
				if( entries.isArray()) {
					
					for( JsonNode entry : entries) {
						
						ofacCheckOneValue(entry,"identificationNumber","q",matches,minMatch);
						ofacCheckOneValue(entry,"individualName","name",matches,minMatch);
						ofacCheckOneValue(entry,"DFIAccountNumber","q",matches,minMatch);
						
					}
				}
			}
		}
	}

	private void ofacCheckOneValue(JsonNode obj, String key, String searchParam, ArrayNode matches, double minMatch ) {

		if( obj != null && obj.has(key)) {
			ofacCheckOneValue(key,obj.get(key),searchParam,matches,minMatch);
		}
	}

	private void ofacCheckOneValue(String key, JsonNode value, String searchParam,ArrayNode matches, double minMatch) {

		if( value != null && value.isTextual()) {
			ofacCheckOneValue(key,value.asText(),searchParam,matches,minMatch);
		}
	}
	
	private double minMatchDefault() {
		return this.systemConfigService.getDecimalConfig(SystemConfigEnums.Key.DEFAULT_OFAC_THRESHOLD).doubleValue();
	}

	private long cacheAgeMillis() {
		return this.systemConfigService.getIntegerConfig(SystemConfigEnums.Key.OFAC_CACHE_AGE_IN_MINUTES).longValue() * 60 * 1000;
	}
	
	
	private void ofacCheckOneValue(String key, String value, String searchParam,
			ArrayNode matches, double minMatch) {
		
		long t = System.currentTimeMillis();
		
		log.info("OFAC check! key={},value={}", key, value);
		
    	if(minMatch == 0) {
    		minMatch = minMatchDefault();
    	}
		
		// TODO Auto-generated method stub
		if(value != null && !value.isBlank() ) {

			// Added for testing purposes
			if(value.equals("OfacBlockMePlease")) {
				ObjectNode blockedResults = jsonMapper.createObjectNode();
				blockedResults.set("key", new TextNode(key));
				blockedResults.set("value", new TextNode(value));
				blockedResults.set("results", new TextNode("fake OFAC results"));
				matches.add(blockedResults);
				return;
			}
			
			try {
			
				String qstring = getWatchmanURL()+
						"/search?"
						+searchParam+"="+URLEncoder.encode(value.trim(), "UTF-8");
				

				JsonNode responseBody = getNotNullOFAC(minMatch, qstring);

				if( containsAny(responseBody,
						"SDNs",
						"altNames",
						//"addresses", - only ofacCheckAddress should count this
						"sectoralSanctions",
						"deniedPersons",
						// NOTE: 2025-12-15. Turning off temporarily because this will generate hundreds of alerts for SSB
						//"ukConsolidatedSanctionsList",
						//"euConsolidatedSanctionsList",
						"bisEntities")) {
					
				
					ObjectNode matchResult = jsonMapper.createObjectNode();
					
					matchResult.set("key", new TextNode(key));
					matchResult.set("value", new TextNode(value));
					matchResult.set("results", responseBody);
				
					matches.add(matchResult);
					
				}
			} catch (BackendConnectivityException e) {
				log.error(e.toString(),e);
				throw e;
			} catch (Exception e) {
				log.error(e.toString(),e);
				throw new BackendConnectivityException(e);
			}
		}

		//TODO: stat
        log.info("MoovService.ofacCheckOneValue: " + (System.currentTimeMillis() - t) + "ms" );
	}

	private JsonNode getNotNullOFAC(double minMatch, String qstring) {
		
		List<Object> cacheKey = Arrays.asList(minMatch,qstring);
		
		long maxAge = this.cacheAgeMillis();
		
		if(maxAge>0) {
			CacheResult cacheResult = watchmanCache.get(cacheKey);
			
			if(cacheResult != null && cacheResult.age() < maxAge) {
				log.info("OFAC cache hit: " + qstring);
				return cacheResult.value;
			} else {
				log.info("OFAC cache miss: " + qstring);
			}
		}
		
		JsonNode responseBody = null;
		
		if(sendMinMatch) {
			qstring += "&minMatch="+minMatch;
		}

		for(int i=0; i<MAX_WATCHMAN_TRIES; i++) {
			log.info("OFAC check! "+ qstring);

			HttpHeaders headers = new HttpHeaders();
			headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
			HttpEntity <String> entity = new HttpEntity<String>(headers);
			  
			ResponseEntity<JsonNode> response = template.exchange(
					  qstring,
				  HttpMethod.GET, entity, JsonNode.class);
			
		    responseBody = response.getBody();

		    if(responseBody == null) {
		    	log.info("Got null response, retrying");
		    	try {
					Thread.sleep(WATCHMAN_RETRY_DELAY);
				} catch (InterruptedException e) {
					log.error(e.toString(), e);
				}
		    } else {
		    	break;
		    }
		}
		
		if(responseBody == null) {
			if(this.ignoreEmptyResponse) {
				return jsonMapper.createObjectNode();
			} else {
				throw new BackendConnectivityException("NULL response from watchman");
			}
		}
		
		JsonNode value = sendMinMatch ? responseBody : filter(responseBody,minMatch);
		
		if(maxAge>0) {
			watchmanCache.put(cacheKey, new CacheResult(value));
		}
		
		return value;
	}
	

    private void ofacCheckAddress(NachaOfacQuery query,
            ArrayNode matches, double minMatch) {
                
    	if(minMatch == 0) {
    		minMatch = minMatchDefault();
    	}
    	
        // TODO Auto-generated method stub
        if(query.getValue() != null && !query.getValue().isBlank() ) {
        	
            try {
            
                String qstring = getWatchmanURL()+
                        "/search?"
                        +"address="+URLEncoder.encode(query.getValue().trim(), "UTF-8")
                        + ( query.getCity()==null ? "" : "&city="+URLEncoder.encode(query.getCity().trim(), "UTF-8")) 
                        + ( query.getState()==null ? "" : "&state="+URLEncoder.encode(query.getState().trim(), "UTF-8")) 
//                        + ( query.getProvince()==null ? "" : "&province="+URLEncoder.encode(query.getProvince().trim(), "UTF-8")) 
//                        + ( query.getCountry()==null ? "" : "&country="+URLEncoder.encode(query.getCountry().trim(), "UTF-8")) 
                        + ( query.getZip()==null ? "" : "&zip="+URLEncoder.encode(query.getZip().trim(), "UTF-8")) 
                        ;
                
                JsonNode responseBody = getNotNullOFAC(minMatch, qstring);
	                
				
                if( containsAny(responseBody,
                        "SDNs",
                        "altNames",
                        "addresses",
                        "sectoralSanctions",
                        "deniedPersons",
                        "bisEntities")) {
                    
                
                    ObjectNode matchResult = jsonMapper.createObjectNode();
                    
                    matchResult.set("key", new TextNode(query.getKey()));
                    matchResult.set("value", new TextNode(query.getValue()));
                    matchResult.set("results", responseBody);
                
                    matches.add(matchResult);
				}
			} catch (BackendConnectivityException e) {
				log.error(e.toString(),e);
				throw e;
			} catch (Exception e) {
				log.error(e.toString(),e);
				throw new BackendConnectivityException(e);
			}
        }
    }
	
	
	
	private boolean containsAny(JsonNode node, String... keys ) {
		
		for(String key: keys) {
			
			if( node.has(key)
				&& node.get(key).isArray()
				&& node.get(key).size() > 0) {
				
				return true;
			}
		}
		
		return false;
	}

	private JsonNode filter(JsonNode node, double minMatch) {

		log.info("Unfiltered OFAC result: " + node);
		
		if(node==null) {
			return null;
		}
		
		ObjectNode outObject = jsonMapper.createObjectNode();

		node.fields().forEachRemaining(	(e) -> {
			ArrayNode outArray = jsonMapper.createArrayNode();
			if(e.getValue() != null && e.getValue().isArray()) {
				for(JsonNode entry : e.getValue()) {
					if(entry.hasNonNull("match") && entry.get("match").asDouble() >= minMatch) {
						outArray.add(entry);
					}
				}
			}
			outObject.set(e.getKey(), outArray);
		});

		log.info("Filtered OFAC result: " + outObject);
		
		return outObject;
	}
	
	
    public ArrayNode ofacCheck(double minMatch, List<NachaOfacQuery> queries) {

    	long t = System.currentTimeMillis();
    	
        ArrayNode matches = jsonMapper.createArrayNode();
        
        for(NachaOfacQuery query : queries) {
            if( query.getKey() != null && !query.getKey().isBlank() && query.getValue() != null && !query.getValue().isBlank()) {

                if(query.getType() == NachaOfacQuery.OfacCheckQueryType.ADDRESS) {
                    ofacCheckAddress(query,matches,minMatch);
                } else {
                    ofacCheckOneValue(query.getKey(), query.getValue(), 
                            query.getType() == NachaOfacQuery.OfacCheckQueryType.FIELD ? "q" : query.getType().name().toLowerCase(),
                            matches, minMatch);
                }
             }
        }

		Long time = (System.currentTimeMillis() - t);
		statsDService.timing("moov.ofac.check", time);
        log.info("MoovService.ofacCheck: " + time + "ms" );
        
        return matches;
    }

	public JsonNode getFedBankingData(String routingNumber, boolean isWire) throws RestClientException {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
		HttpEntity <String> entity = new HttpEntity<String>(headers);

		String transactionType = isWire ? "wire" : "ach";

		return template.exchange(
				getMoovFedURL() +"/fed/" + transactionType +"/search?routingNumber=" + routingNumber,
				HttpMethod.GET, entity, JsonNode.class).getBody();
	}
}
