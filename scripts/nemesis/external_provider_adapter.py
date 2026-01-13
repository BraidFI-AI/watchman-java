"""
External Provider Adapter - Interface for ofac-api.com
Handles API communication and format translation
"""

from typing import List, Dict, Optional
import requests
import time


class OFACAPIAdapter:
    """
    Adapter for ofac-api.com v4 API.
    Translates between Nemesis format and ofac-api.com format.
    """
    
    def __init__(self, api_key: str, base_url: str = "https://api.ofac-api.com/v4"):
        """
        Initialize adapter with API credentials.
        
        Args:
            api_key: ofac-api.com API key
            base_url: Base URL for API (default: https://api.ofac-api.com/v4)
        """
        self.api_key = api_key
        self.base_url = base_url
        
    def search(
        self, 
        query: str, 
        min_score: int = 80,
        sources: List[str] = None,
        timeout: float = 10.0
    ) -> tuple:
        """
        Search for a single query using ofac-api.com.
        
        Args:
            query: Name to search for
            min_score: Minimum match score (0-100)
            sources: List of sources to search (default: ["sdn"])
            timeout: Request timeout in seconds
            
        Returns:
            Tuple of (results, elapsed_ms, error_message)
            Results are in standardized format matching Java/Go APIs
        """
        if sources is None:
            sources = ["sdn"]  # SDN-only for apples-to-apples comparison
            
        return self.search_batch([query], min_score, sources, timeout)
        
    def search_batch(
        self,
        queries: List[str],
        min_score: int = 80,
        sources: List[str] = None,
        timeout: float = 30.0
    ) -> tuple:
        """
        Search for multiple queries in a single batch request.
        
        Args:
            queries: List of names to search for
            min_score: Minimum match score (0-100)
            sources: List of sources to search (default: ["sdn"])
            timeout: Request timeout in seconds
            
        Returns:
            Tuple of (results_by_query, elapsed_ms, error_message)
            results_by_query is a dict: {query: [matching_entities]}
        """
        if sources is None:
            sources = ["sdn"]
            
        # Build request payload
        cases = [
            {"name": query, "externalId": str(i)} 
            for i, query in enumerate(queries)
        ]
        
        payload = {
            "apiKey": self.api_key,
            "minScore": min_score,
            "sources": sources,
            "cases": cases
        }
        
        try:
            start = time.time()
            response = requests.post(
                f"{self.base_url}/screen",
                json=payload,
                timeout=timeout
            )
            elapsed = (time.time() - start) * 1000
            
            if response.status_code != 200:
                return None, None, f"HTTP {response.status_code}: {response.text}"
                
            data = response.json()
            
            # Parse response and group by query
            results_by_query = {}
            for case_result in data.get("results", []):
                external_id = case_result.get("externalId")
                query_text = queries[int(external_id)]
                
                # Convert matches to standardized format
                matches = []
                for match in case_result.get("matches", []):
                    matches.append(self._translate_to_standard_format(match))
                
                results_by_query[query_text] = matches
                
            return results_by_query, elapsed, None
            
        except requests.Timeout:
            return None, None, "Timeout"
        except requests.ConnectionError:
            return None, None, "Connection error"
        except Exception as e:
            return None, None, str(e)
            
    def _translate_to_standard_format(self, ofac_api_entity: Dict) -> Dict:
        """
        Translate ofac-api.com entity format to standardized format.
        
        Args:
            ofac_api_entity: Entity in ofac-api.com format
            
        Returns:
            Entity in standardized format matching Java/Go APIs
        """
        # ofac-api.com returns score as integer 0-100, convert to 0.0-1.0
        score = ofac_api_entity.get("score", 0) / 100.0
        
        return {
            "id": ofac_api_entity.get("uid"),  # Unique identifier
            "name": ofac_api_entity.get("name", ""),
            "match": score,
            "entityType": self._map_entity_type(ofac_api_entity.get("type")),
            "sourceList": self._map_source(ofac_api_entity.get("source")),
            "remarks": ofac_api_entity.get("remarks", ""),
            "programs": ofac_api_entity.get("programs", []),
            # Store raw data for debugging
            "_raw": ofac_api_entity
        }
        
    def _map_entity_type(self, ofac_type: Optional[str]) -> str:
        """Map ofac-api.com entity type to standard format."""
        if not ofac_type:
            return ""
            
        type_map = {
            "person": "individual",
            "individual": "individual",
            "organization": "entity",
            "entity": "entity",
            "vessel": "vessel",
            "aircraft": "aircraft"
        }
        
        return type_map.get(ofac_type.lower(), ofac_type)
        
    def _map_source(self, ofac_source: Optional[str]) -> str:
        """Map ofac-api.com source to standard format."""
        if not ofac_source:
            return ""
            
        source_map = {
            "sdn": "US-OFAC",
            "nonsdn": "US-OFAC-NonSDN",
            "un": "UN",
            "eu": "EU",
            "ofsi": "UK-OFSI"
        }
        
        return source_map.get(ofac_source.lower(), ofac_source.upper())
