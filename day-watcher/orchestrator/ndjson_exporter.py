"""
NDJSON Exporter
Transforms Braid Customer/Contact entities to Watchman batch search format
Based on: archive/WatchmanBulkScreeningService.java#L191-L212
"""
import json
from decimal import Decimal
from typing import Dict, Optional


def decimal_to_float(obj):
    """Convert Decimal objects to float for JSON serialization"""
    if isinstance(obj, Decimal):
        return float(obj)
    elif isinstance(obj, dict):
        return {k: decimal_to_float(v) for k, v in obj.items()}
    elif isinstance(obj, list):
        return [decimal_to_float(item) for item in obj]
    return obj


class NDJSONExporter:
    """Export Braid entities to NDJSON format for Watchman batch screening"""
    
    def export_individual(self, individual: Dict) -> str:
        """
        Export Braid individual to NDJSON line
        
        Format:
        {
            "name": "John Doe",
            "altNames": ["Johnny Doe"],
            "entityType": "INDIVIDUAL",
            "addresses": [{"line1": "123 Main St", "city": "New York", ...}],
            "birthDate": "1980-01-15",
            "sdnType": "individual",
            "metadata": {
                "entityId": "ind_abc123",
                "entityType": "CUSTOMER_INDIVIDUAL",
                "tenantId": "tenant_xyz"
            }
        }
        """
        name = self._format_name(individual)
        alt_names = self._extract_alt_names(individual)
        addresses = self._extract_addresses(individual)
        birth_date = individual.get('dateOfBirth')
        
        data = {
            'name': name,
            'altNames': alt_names,
            'entityType': 'INDIVIDUAL',
            'addresses': addresses,
            'birthDate': birth_date,
            'sdnType': 'individual',
            'metadata': {
                'entityId': individual.get('individualId'),
                'entityType': 'CUSTOMER_INDIVIDUAL',
                'tenantId': individual.get('tenantId', 'unknown')
            }
        }
        return json.dumps(decimal_to_float(data), ensure_ascii=False)
    
    def export_business(self, business: Dict) -> str:
        """
        Export Braid business to NDJSON line
        
        Format similar to individual but entityType=BUSINESS, sdnType=entity
        """
        name = business.get('legalName', business.get('dba', 'UNKNOWN'))
        alt_names = []
        if business.get('dba') and business.get('dba') != name:
            alt_names.append(business['dba'])
        
        addresses = self._extract_addresses(business)
        
        data = {
            'name': name,
            'altNames': alt_names,
            'entityType': 'BUSINESS',
            'addresses': addresses,
            'sdnType': 'entity',
            'metadata': {
                'entityId': business.get('businessId'),
                'entityType': 'CUSTOMER_BUSINESS',
                'tenantId': business.get('tenantId', 'unknown')
            }
        }
        return json.dumps(decimal_to_float(data), ensure_ascii=False)
    
    def export_counterparty(self, counterparty: Dict) -> str:
        """
        Export Braid counterparty to NDJSON line
        
        Counterparty can be individual or business.
        Detect type from businessId/individualId association or default to INDIVIDUAL.
        """
        # Attempt to detect counterparty type
        if counterparty.get('businessId'):
            entity_type = 'COUNTERPARTY_BUSINESS'
            sdn_type = 'entity'
        else:
            # Default to individual if no business association
            entity_type = 'COUNTERPARTY_INDIVIDUAL'
            sdn_type = 'individual'
        
        name = self._format_counterparty_name(counterparty)
        addresses = self._extract_addresses(counterparty)
        
        data = {
            'name': name,
            'altNames': [],
            'entityType': entity_type.split('_')[1],  # INDIVIDUAL or BUSINESS
            'addresses': addresses,
            'sdnType': sdn_type,
            'metadata': {
                'entityId': counterparty.get('counterpartyId'),
                'entityType': entity_type,
                'tenantId': counterparty.get('tenantId', 'unknown')
            }
        }
        return json.dumps(decimal_to_float(data), ensure_ascii=False)
    
    def _format_name(self, individual: Dict) -> str:
        """Format individual name: 'LASTNAME, Firstname Middlename'"""
        first = individual.get('firstName', '')
        middle = individual.get('middleName', '')
        last = individual.get('lastName', '')
        
        if middle:
            return f"{last.upper()}, {first} {middle}"
        return f"{last.upper()}, {first}"
    
    def _format_counterparty_name(self, counterparty: Dict) -> str:
        """Format counterparty name (may be individual or business name)"""
        # Check if it's a business name
        if counterparty.get('legalName'):
            return counterparty['legalName']
        
        # Otherwise format as individual
        return self._format_name(counterparty)
    
    def _extract_alt_names(self, individual: Dict) -> list:
        """Extract alternate names from individual"""
        alt_names = []
        
        # Check for aliases array
        if individual.get('aliases'):
            alt_names.extend(individual['aliases'])
        
        # Check for maiden name
        if individual.get('maidenName'):
            alt_names.append(individual['maidenName'])
        
        return alt_names
    
    def _extract_addresses(self, entity: Dict) -> list:
        """
        Extract addresses from entity
        
        Returns list of address objects with fields:
        line1, line2, city, state, postalCode, country
        """
        addresses = []
        
        # Primary address
        if entity.get('address'):
            addr = entity['address']
            addresses.append({
                'line1': addr.get('line1', ''),
                'line2': addr.get('line2', ''),
                'city': addr.get('city', ''),
                'state': addr.get('state', ''),
                'postalCode': addr.get('postalCode', ''),
                'country': addr.get('country', 'US')
            })
        
        # Additional addresses array
        if entity.get('addresses'):
            for addr in entity['addresses']:
                addresses.append({
                    'line1': addr.get('line1', ''),
                    'line2': addr.get('line2', ''),
                    'city': addr.get('city', ''),
                    'state': addr.get('state', ''),
                    'postalCode': addr.get('postalCode', ''),
                    'country': addr.get('country', 'US')
                })
        
        return addresses
