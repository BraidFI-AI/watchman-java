#!/bin/bash
# Generate test NDJSON files for local development
set -e

OUTPUT_DIR="$(dirname "$0")/../test-data"
mkdir -p "$OUTPUT_DIR"

echo "=== Generating Test NDJSON Files ==="

# Generate small test file (10 entities)
cat > "$OUTPUT_DIR/test-10.ndjson" <<'EOF'
{"name":"AL-BAGHDADI, Ibrahim","altNames":["Abu Bakr"],"entityType":"INDIVIDUAL","addresses":[{"line1":"123 Main St","city":"Baghdad","country":"IQ"}],"birthDate":"1971-07-28","sdnType":"individual","metadata":{"entityId":"ind_001","entityType":"CUSTOMER_INDIVIDUAL","tenantId":"tenant_test"}}
{"name":"AL-ZAWAHIRI, Ayman","altNames":[],"entityType":"INDIVIDUAL","addresses":[],"birthDate":"1951-06-19","sdnType":"individual","metadata":{"entityId":"ind_002","entityType":"CUSTOMER_INDIVIDUAL","tenantId":"tenant_test"}}
{"name":"SMITH, John","altNames":[],"entityType":"INDIVIDUAL","addresses":[{"line1":"456 Oak Ave","city":"New York","state":"NY","country":"US"}],"birthDate":"1980-01-15","sdnType":"individual","metadata":{"entityId":"ind_003","entityType":"CUSTOMER_INDIVIDUAL","tenantId":"tenant_test"}}
{"name":"DOE, Jane","altNames":["Jane Smith"],"entityType":"INDIVIDUAL","addresses":[],"birthDate":"1985-03-20","sdnType":"individual","metadata":{"entityId":"ind_004","entityType":"CUSTOMER_INDIVIDUAL","tenantId":"tenant_test"}}
{"name":"ACME Corporation","altNames":["ACME Corp"],"entityType":"BUSINESS","addresses":[{"line1":"789 Business Blvd","city":"Los Angeles","state":"CA","country":"US"}],"sdnType":"entity","metadata":{"entityId":"bus_001","entityType":"CUSTOMER_BUSINESS","tenantId":"tenant_test"}}
{"name":"Global Trading LLC","altNames":[],"entityType":"BUSINESS","addresses":[],"sdnType":"entity","metadata":{"entityId":"bus_002","entityType":"CUSTOMER_BUSINESS","tenantId":"tenant_test"}}
{"name":"JOHNSON, Michael","altNames":[],"entityType":"INDIVIDUAL","addresses":[],"birthDate":"1975-11-10","sdnType":"individual","metadata":{"entityId":"cpty_001","entityType":"COUNTERPARTY_INDIVIDUAL","tenantId":"tenant_test"}}
{"name":"WILLIAMS, Sarah","altNames":[],"entityType":"INDIVIDUAL","addresses":[{"line1":"321 Park Ave","city":"Chicago","state":"IL","country":"US"}],"birthDate":"1990-05-25","sdnType":"individual","metadata":{"entityId":"cpty_002","entityType":"COUNTERPARTY_INDIVIDUAL","tenantId":"tenant_test"}}
{"name":"XYZ Industries","altNames":[],"entityType":"BUSINESS","addresses":[],"sdnType":"entity","metadata":{"entityId":"cpty_003","entityType":"COUNTERPARTY_BUSINESS","tenantId":"tenant_test"}}
{"name":"Tech Solutions Inc","altNames":["TechSol"],"entityType":"BUSINESS","addresses":[{"line1":"555 Innovation Dr","city":"San Francisco","state":"CA","country":"US"}],"sdnType":"entity","metadata":{"entityId":"cpty_004","entityType":"COUNTERPARTY_BUSINESS","tenantId":"tenant_test"}}
EOF

echo "✓ Created test-10.ndjson (10 entities)"

# Generate medium test file (100 entities)
# Start with the 10 entities above, then generate 90 more variations
cat "$OUTPUT_DIR/test-10.ndjson" > "$OUTPUT_DIR/test-100.ndjson"

for i in {11..100}; do
  NAMES=("ANDERSON" "BROWN" "DAVIS" "GARCIA" "HARRIS" "JACKSON" "JONES" "MARTIN" "MILLER" "MOORE" "TAYLOR" "THOMAS" "WHITE" "WILSON")
  FIRST_NAMES=("James" "John" "Robert" "Michael" "William" "David" "Richard" "Joseph" "Thomas" "Charles")
  
  NAME_IDX=$((i % ${#NAMES[@]}))
  FIRST_IDX=$((i % ${#FIRST_NAMES[@]}))
  
  LAST_NAME="${NAMES[$NAME_IDX]}"
  FIRST_NAME="${FIRST_NAMES[$FIRST_IDX]}"
  
  BIRTH_YEAR=$((1950 + (i % 50)))
  BIRTH_MONTH=$(printf "%02d" $((1 + (i % 12))))
  BIRTH_DAY=$(printf "%02d" $((1 + (i % 28))))
  
  echo "{\"name\":\"$LAST_NAME, $FIRST_NAME\",\"altNames\":[],\"entityType\":\"INDIVIDUAL\",\"addresses\":[],\"birthDate\":\"$BIRTH_YEAR-$BIRTH_MONTH-$BIRTH_DAY\",\"sdnType\":\"individual\",\"metadata\":{\"entityId\":\"ind_$i\",\"entityType\":\"CUSTOMER_INDIVIDUAL\",\"tenantId\":\"tenant_test\"}}" >> "$OUTPUT_DIR/test-100.ndjson"
done

echo "✓ Created test-100.ndjson (100 entities)"

# Generate large test file (1000 entities)
cat "$OUTPUT_DIR/test-100.ndjson" > "$OUTPUT_DIR/test-1000.ndjson"

for i in {101..1000}; do
  NAME_IDX=$((i % 14))
  FIRST_IDX=$((i % 10))
  
  LAST_NAME="${NAMES[$NAME_IDX]}"
  FIRST_NAME="${FIRST_NAMES[$FIRST_IDX]}"
  
  BIRTH_YEAR=$((1950 + (i % 50)))
  BIRTH_MONTH=$(printf "%02d" $((1 + (i % 12))))
  BIRTH_DAY=$(printf "%02d" $((1 + (i % 28))))
  
  echo "{\"name\":\"$LAST_NAME, $FIRST_NAME\",\"altNames\":[],\"entityType\":\"INDIVIDUAL\",\"addresses\":[],\"birthDate\":\"$BIRTH_YEAR-$BIRTH_MONTH-$BIRTH_DAY\",\"sdnType\":\"individual\",\"metadata\":{\"entityId\":\"ind_$i\",\"entityType\":\"CUSTOMER_INDIVIDUAL\",\"tenantId\":\"tenant_test\"}}" >> "$OUTPUT_DIR/test-1000.ndjson"
done

echo "✓ Created test-1000.ndjson (1000 entities)"

echo ""
echo "=== Test files created in $OUTPUT_DIR ==="
echo "test-10.ndjson    - 10 entities (quick test)"
echo "test-100.ndjson   - 100 entities (medium test)"
echo "test-1000.ndjson  - 1000 entities (performance test)"
echo ""
echo "Upload to S3 for testing:"
echo "aws s3 cp $OUTPUT_DIR/test-10.ndjson s3://watchman-input/test-run/customers.ndjson"
