"""
Braid API Client
Wrapper for Braid REST APIs with rate limiting and pagination
"""
import requests
import time
from typing import List, Dict, Optional


class BraidClient:
    """Client for Braid REST APIs with rate limiting (20 RPS safe under 30 RPS limit)"""
    
    BASE_URL = "https://api.sandbox.braid.zone"
    RATE_LIMIT_RPS = 20  # Safe under 30 RPS token bucket
    PAGE_SIZE = 100
    
    def __init__(self, username: str, api_key: str):
        self.username = username
        self.api_key = api_key
        self.session = requests.Session()
        self.session.auth = (username, api_key)
        self.session.headers.update({
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        })
        self.last_request_time = 0
    
    def _rate_limit(self):
        """Enforce rate limit of 20 RPS"""
        min_interval = 1.0 / self.RATE_LIMIT_RPS
        elapsed = time.time() - self.last_request_time
        if elapsed < min_interval:
            time.sleep(min_interval - elapsed)
        self.last_request_time = time.time()
    
    def _post_with_pagination(self, endpoint: str, filter_params: Dict):
        """
        Execute POST request with pagination
        Yields pages of results to avoid loading everything into memory
        """
        page = 0
        
        while True:
            self._rate_limit()
            
            payload = {
                **filter_params,
                'page': page,
                'pageSize': self.PAGE_SIZE
            }
            
            response = self.session.post(
                f"{self.BASE_URL}{endpoint}",
                json=payload,
                timeout=30
            )
            response.raise_for_status()
            
            data = response.json()
            results = data.get('content', [])
            
            if not results:
                break
            
            yield results
            
            # Check if more pages exist
            total_count = data.get('totalElements', 0)
            total_fetched = (page + 1) * self.PAGE_SIZE
            if total_fetched >= total_count:
                break
            
            page += 1
    
    def fetch_individuals(self, status: str = 'ACTIVE', updated_after: Optional[str] = None):
        """
        Fetch individuals with given status, optionally filtered by update time
        POST /individual/search
        Yields pages of results
        
        Args:
            status: Entity status filter (default: ACTIVE)
            updated_after: ISO timestamp - fetch only entities updated after this time
        """
        filters = {'status': status}
        if updated_after:
            filters['updatedAt'] = updated_after
            print(f"Fetching individuals with status={status}, updatedAt>{updated_after}")
        else:
            print(f"Fetching ALL individuals with status={status}")
        
        yield from self._post_with_pagination('/individual/search', filters)
    
    def fetch_businesses(self, status: str = 'ACTIVE', updated_after: Optional[str] = None):
        """
        Fetch businesses with given status, optionally filtered by update time
        POST /business/search
        Yields pages of results
        
        Args:
            status: Entity status filter (default: ACTIVE)
            updated_after: ISO timestamp - fetch only entities updated after this time
        """
        filters = {'status': status}
        if updated_after:
            filters['updatedAt'] = updated_after
            print(f"Fetching businesses with status={status}, updatedAt>{updated_after}")
        else:
            print(f"Fetching ALL businesses with status={status}")
        
        yield from self._post_with_pagination('/business/search', filters)
    
    def fetch_counterparties(self, status: str = 'ACTIVE', updated_after: Optional[str] = None):
        """
        Fetch counterparties with given status, optionally filtered by update time
        POST /counterparty/search
        Yields pages of results
        
        Note: Counterparty endpoint may not support status/updatedAt filters directly.
        If API errors, fallback to fetching all and filtering client-side.
        
        Args:
            status: Entity status filter (default: ACTIVE)
            updated_after: ISO timestamp - fetch only entities updated after this time
        """
        filters = {'status': status}
        if updated_after:
            filters['updatedAt'] = updated_after
            print(f"Fetching counterparties with status={status}, updatedAt>{updated_after}")
        else:
            print(f"Fetching ALL counterparties with status={status}")
        
        try:
            yield from self._post_with_pagination('/counterparty/search', filters)
        except requests.exceptions.HTTPError as e:
            if e.response.status_code == 400:
                # Fallback: fetch all counterparties and filter client-side
                print("Filters not supported, fetching all counterparties and filtering client-side")
                for page in self._post_with_pagination('/counterparty/search', {}):
                    filtered = [
                        c for c in page 
                        if c.get('status') == status and (
                            not updated_after or c.get('updatedAt', '') > updated_after
                        )
                    ]
                    if filtered:
                        yield filtered
            else:
                raise
