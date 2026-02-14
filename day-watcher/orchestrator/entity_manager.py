"""
Entity Manager: PostgreSQL operations for master entity list
Handles incremental updates and bulk exports
"""
import psycopg2
import psycopg2.extras
import json
from datetime import datetime, timezone
from typing import Dict, List, Optional, Iterator


class EntityManager:
    """Manages entity master list in PostgreSQL"""
    
    def __init__(self, db_config: Dict[str, str]):
        """
        Initialize with database connection configuration
        
        Args:
            db_config: Dict with keys: host, port, database, username, password
        """
        self.db_config = db_config
        self.conn = None
        self._connect()
        print(f"🔍 EntityManager initialized: host={db_config['host']}, database={db_config['database']}", flush=True)
    
    def _connect(self):
        """Establish database connection"""
        self.conn = psycopg2.connect(
            host=self.db_config['host'],
            port=self.db_config['port'],
            database=self.db_config['database'],
            user=self.db_config['username'],
            password=self.db_config['password'],
            connect_timeout=10
        )
        self.conn.autocommit = False  # Use explicit transactions
    
    def _ensure_connection(self):
        """Ensure connection is alive, reconnect if needed"""
        try:
            with self.conn.cursor() as cur:
                cur.execute("SELECT 1")
        except (psycopg2.OperationalError, psycopg2.InterfaceError):
            print("⚠️ Database connection lost, reconnecting...", flush=True)
            self._connect()
    
    def close(self):
        """Close database connection"""
        if self.conn:
            self.conn.close()
    
    def is_empty(self) -> bool:
        """Check if entities table is empty (first run detection)"""
        self._ensure_connection()
        with self.conn.cursor() as cur:
            cur.execute("SELECT COUNT(*) FROM entities LIMIT 1")
            count = cur.fetchone()[0]
        return count == 0
    
    def upsert_entity(self, entity_data: Dict) -> None:
        """Insert or update a single entity"""
        self._ensure_connection()
        
        sql = """
        INSERT INTO entities (
            entity_id, entity_type, name, addresses, dob, incorporation_date,
            alt_names, braid_updated_at, braid_tenant_id, braid_status
        ) VALUES (
            %(entityId)s, %(entityType)s, %(name)s, %(addresses)s, %(dob)s, 
            %(incorporationDate)s, %(altNames)s, %(braidUpdatedAt)s, 
            %(braidTenantId)s, %(braidStatus)s
        )
        ON CONFLICT (entity_id) DO UPDATE SET
            entity_type = EXCLUDED.entity_type,
            name = EXCLUDED.name,
            addresses = EXCLUDED.addresses,
            dob = EXCLUDED.dob,
            incorporation_date = EXCLUDED.incorporation_date,
            alt_names = EXCLUDED.alt_names,
            braid_updated_at = EXCLUDED.braid_updated_at,
            braid_tenant_id = EXCLUDED.braid_tenant_id,
            braid_status = EXCLUDED.braid_status,
            updated_at = CURRENT_TIMESTAMP
        """
        
        params = {
            'entityId': entity_data['entityId'],
            'entityType': entity_data['entityType'],
            'name': entity_data.get('name', ''),
            'addresses': json.dumps(entity_data.get('addresses', [])) if entity_data.get('addresses') else None,
            'dob': entity_data.get('dob'),
            'incorporationDate': entity_data.get('incorporationDate'),
            'altNames': json.dumps(entity_data.get('altNames', [])) if entity_data.get('altNames') else None,
            'braidUpdatedAt': entity_data.get('braidUpdatedAt'),
            'braidTenantId': entity_data.get('braidTenantId'),
            'braidStatus': entity_data.get('braidStatus', 'ACTIVE')
        }
        
        with self.conn.cursor() as cur:
            cur.execute(sql, params)
        self.conn.commit()
    
    def batch_upsert_entities(self, entities: List[Dict], batch_size: int = 1000) -> int:
        """
        Batch insert/update entities using PostgreSQL execute_values
        Deduplicates by entity_id to avoid ON CONFLICT errors
        Returns the number of entities successfully written
        """
        self._ensure_connection()
        written_count = 0
        entity_ids_sample = []
        
        # Deduplicate by entityId (keep last occurrence)
        seen = {}
        for entity in entities:
            seen[entity['entityId']] = entity
        deduped_entities = list(seen.values())
        
        duplicates_removed = len(entities) - len(deduped_entities)
        if duplicates_removed > 0:
            print(f"  ℹ️  Deduplicated {duplicates_removed} duplicate entities in batch (batch had {len(entities)} entities, {len(deduped_entities)} unique)", flush=True)
            # Log sample of duplicate IDs for debugging
            if duplicates_removed > 10:
                all_ids = [e['entityId'] for e in entities]
                unique_ids = set(all_ids)
                sample_dup = [eid for eid in unique_ids if all_ids.count(eid) > 1][:3]
                print(f"  🔍 Sample duplicate entityIds: {sample_dup}", flush=True)
        
        entities = deduped_entities
        
        try:
            # Prepare batch data
            values = []
            for entity_data in entities:
                # Handle DOB - may come as date string, None, or array (ignore if array)
                dob = entity_data.get('dob')
                if isinstance(dob, list):
                    dob = None  # Ignore array values (e.g., birthYearRange)
                
                # Handle incorporation date similarly
                inc_date = entity_data.get('incorporationDate')
                if isinstance(inc_date, list):
                    inc_date = None
                
                # Handle braidUpdatedAt - convert Unix timestamp to ISO string if needed
                braid_updated = entity_data.get('braidUpdatedAt')
                if isinstance(braid_updated, (int, float)):
                    from datetime import datetime, timezone as tz
                    braid_updated = datetime.fromtimestamp(braid_updated, tz=tz.utc).isoformat()
                
                params = (
                    entity_data['entityId'],
                    entity_data['entityType'],
                    entity_data.get('name', ''),
                    json.dumps(entity_data.get('addresses', [])) if entity_data.get('addresses') else None,
                    dob,
                    inc_date,
                    json.dumps(entity_data.get('altNames', [])) if entity_data.get('altNames') else None,
                    braid_updated,
                    entity_data.get('braidTenantId'),
                    entity_data.get('braidStatus', 'ACTIVE'),
                    json.dumps(entity_data.get('originalData', {})) if entity_data.get('originalData') else None
                )
                values.append(params)
                
                # Sample first 3 entityIds
                if len(entity_ids_sample) < 3:
                    entity_ids_sample.append(entity_data['entityId'])
            
            # Use execute_values for efficient batch insert
            sql = """
            INSERT INTO entities (
                entity_id, entity_type, name, addresses, dob, incorporation_date,
                alt_names, braid_updated_at, braid_tenant_id, braid_status, original_data
            ) VALUES %s
            ON CONFLICT (entity_id) DO UPDATE SET
                entity_type = EXCLUDED.entity_type,
                name = EXCLUDED.name,
                addresses = EXCLUDED.addresses,
                dob = EXCLUDED.dob,
                incorporation_date = EXCLUDED.incorporation_date,
                alt_names = EXCLUDED.alt_names,
                braid_updated_at = EXCLUDED.braid_updated_at,
                braid_tenant_id = EXCLUDED.braid_tenant_id,
                braid_status = EXCLUDED.braid_status,
                original_data = EXCLUDED.original_data,
                updated_at = CURRENT_TIMESTAMP
            """
            
            with self.conn.cursor() as cur:
                psycopg2.extras.execute_values(
                    cur, sql, values, template=None, page_size=batch_size
                )
            
            self.conn.commit()
            written_count = len(entities)
            
            if written_count > 0:
                sample_str = f" (IDs: {', '.join(entity_ids_sample[:3])})" if entity_ids_sample else ""
                print(f"  ✓ Batch write successful: {written_count} items written{sample_str}", flush=True)
        
        except Exception as e:
            print(f"ERROR: Batch write failed: {str(e)}", flush=True)
            self.conn.rollback()
            raise
        
        return written_count
    
    def get_all_entities(self) -> Iterator[Dict]:
        """
        Generator that yields all entities from PostgreSQL
        Uses server-side cursor for memory-efficient streaming
        """
        self._ensure_connection()
        
        # Use named cursor for server-side cursor (doesn't load all rows into memory)
        with self.conn.cursor(name='fetch_entities', cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.itersize = 1000  # Fetch 1000 rows at a time
            cur.execute("""
                SELECT 
                    entity_id as "entityId",
                    entity_type as "entityType",
                    name,
                    addresses,
                    dob,
                    incorporation_date as "incorporationDate",
                    alt_names as "altNames",
                    braid_updated_at as "braidUpdatedAt",
                    braid_tenant_id as "braidTenantId",
                    braid_status as "braidStatus",
                    last_screened_at as "lastScreenedAt",
                    last_screening_result as "lastScreeningResult",
                    original_data as "originalData"
                FROM entities
                WHERE braid_status = 'ACTIVE'
                ORDER BY entity_id
            """)
            
            for row in cur:
                # Parse JSONB fields
                entity = dict(row)
                if entity.get('addresses'):
                    entity['addresses'] = json.loads(entity['addresses']) if isinstance(entity['addresses'], str) else entity['addresses']
                if entity.get('altNames'):
                    entity['altNames'] = json.loads(entity['altNames']) if isinstance(entity['altNames'], str) else entity['altNames']
                
                # Convert datetime to ISO string
                if entity.get('braidUpdatedAt'):
                    entity['braidUpdatedAt'] = entity['braidUpdatedAt'].isoformat()
                if entity.get('lastScreenedAt'):
                    entity['lastScreenedAt'] = entity['lastScreenedAt'].isoformat()
                    
                yield entity
    
    def get_entity_count(self) -> int:
        """Get total count of active entities"""
        self._ensure_connection()
        with self.conn.cursor() as cur:
            cur.execute("SELECT COUNT(*) FROM entities WHERE braid_status = 'ACTIVE'")
            return cur.fetchone()[0]
    
    def update_screening_result(self, entity_id: str, entity_type: str, 
                               result: str, screened_at: Optional[str] = None) -> None:
        """Update entity with screening result"""
        self._ensure_connection()
        
        if screened_at is None:
            screened_at = datetime.now(timezone.utc).isoformat()
        
        sql = """
        UPDATE entities 
        SET last_screened_at = %s, last_screening_result = %s, updated_at = CURRENT_TIMESTAMP
        WHERE entity_id = %s
        """
        
        with self.conn.cursor() as cur:
            cur.execute(sql, (screened_at, result, entity_id))
        self.conn.commit()
    
    # ========== Run Management (replaces DynamoDB runs table) ==========
    
    def initialize_run(self, run_id: str, entities_fetched: int, entities_written: int, 
                      entities_in_ndjson: int, s3_input_path: str, fetch_breakdown: Dict) -> None:
        """Initialize a new run record"""
        self._ensure_connection()
        
        from datetime import date
        run_date = date.today()
        has_discrepancy = (entities_fetched != entities_written)
        
        sql = """
        INSERT INTO runs (
            run_id, run_date, status, entities_fetched_from_braid, 
            entities_written_to_db, entities_in_ndjson, s3_input_path, 
            fetch_breakdown, has_discrepancy
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        
        with self.conn.cursor() as cur:
            cur.execute(sql, (
                run_id, run_date, 'PENDING', entities_fetched, entities_written, 
                entities_in_ndjson, s3_input_path, json.dumps(fetch_breakdown), 
                has_discrepancy
            ))
        self.conn.commit()
    
    def update_run_status(self, run_id: str, status: str, **kwargs) -> None:
        """Update run status and additional fields"""
        self._ensure_connection()
        
        # Build dynamic UPDATE query
        set_clauses = ['status = %s', 'updated_at = CURRENT_TIMESTAMP']
        values = [status]
        
        # Map Python kwargs to DB columns
        field_mapping = {
            'ecsTaskArn': 'ecs_task_arn',
            'endTime': 'end_time',
            'totalEntitiesScreened': 'total_entities_screened',
            's3OutputPath': 's3_output_path',
            'errorMessage': 'error_message'
        }
        
        for key, value in kwargs.items():
            db_column = field_mapping.get(key, key)
            set_clauses.append(f'{db_column} = %s')
            values.append(value)
        
        values.append(run_id)  # For WHERE clause
        
        sql = f"UPDATE runs SET {', '.join(set_clauses)} WHERE run_id = %s"
        
        with self.conn.cursor() as cur:
            cur.execute(sql, values)
        self.conn.commit()
