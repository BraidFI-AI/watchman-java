"""
Match Enrichment
Adds entityId, tenantId, and alert description to Watchman matches
Based on: braid-integration/NachaService.java#L956-L967
"""
from typing import Dict


def enrich_match(match: Dict, metadata: Dict) -> Dict:
    """
    Enrich Watchman match with Braid entity metadata
    
    Input match from Watchman:
    {
        "match": 0.94,
        "name": "AL-BAGHDADI, Ibrahim Awwad Ibrahim Ali",
        "entityType": "individual",
        "addresses": [...],
        ...
    }
    
    Input metadata from NDJSON:
    {
        "entityId": "ind_abc123" | "bus_xyz789" | "cpty_def456",
        "entityType": "CUSTOMER_INDIVIDUAL" | "CUSTOMER_BUSINESS" | "COUNTERPARTY_INDIVIDUAL" | "COUNTERPARTY_BUSINESS",
        "tenantId": "tenant_xyz"
    }
    
    Output:
    {
        ...original match fields...,
        "alertMetadata": {
            "entityId": "ind_abc123",
            "tenantId": "tenant_xyz",
            "description": "INDIVIDUAL: AL-BAGHDADI, Ibrahim Awwad Ibrahim Ali is flagged for OFAC"
        }
    }
    """
    entity_type = metadata.get('entityType', 'UNKNOWN')
    entity_id = metadata.get('entityId', 'unknown')
    tenant_id = metadata.get('tenantId', 'unknown')
    name = match.get('name', 'UNKNOWN')
    
    # Generate alert description based on entity type
    description = generate_alert_description(entity_type, name)
    
    # Return enriched match
    enriched = {
        **match,
        'alertMetadata': {
            'entityId': entity_id,
            'tenantId': tenant_id,
            'entityType': entity_type,
            'description': description
        }
    }
    
    return enriched


def generate_alert_description(entity_type: str, name: str) -> str:
    """
    Generate alert description based on entity type
    Based on NachaService.java alert patterns
    """
    if entity_type == 'CUSTOMER_INDIVIDUAL':
        return f"INDIVIDUAL: {name} is flagged for OFAC"
    elif entity_type == 'CUSTOMER_BUSINESS':
        return f"BUSINESS: {name} is flagged for OFAC"
    elif entity_type == 'COUNTERPARTY_INDIVIDUAL':
        return f"Counterparty: {name} is flagged for OFAC"
    elif entity_type == 'COUNTERPARTY_BUSINESS':
        return f"Counterparty: {name} is flagged for OFAC"
    else:
        return f"Entity: {name} is flagged for OFAC"
