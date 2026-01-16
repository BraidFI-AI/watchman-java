#!/bin/bash

# Generate large NDJSON test file for bulk screening
# Usage: ./generate-100k-test-data.sh [count]

set -e

COUNT=${1:-100000}
OUTPUT_FILE="test-data-${COUNT}.ndjson"

echo "🔨 Generating ${COUNT} test records..."

# Sanctioned entities to sprinkle in (from real OFAC list)
SANCTIONED=(
  "Nicolas Maduro"
  "Vladimir Putin"
  "Osama Bin Laden"
  "El Chapo"
  "Pablo Escobar"
  "Kim Jong Un"
  "Saddam Hussein"
  "Muammar Gaddafi"
  "Bashar al-Assad"
  "Ali Khamenei"
)

# Generate records
> "$OUTPUT_FILE"  # Clear file

for i in $(seq 1 $COUNT); do
  # Every 1000th record is a sanctioned entity (100 matches expected)
  if [ $((i % 1000)) -eq 0 ]; then
    SANCTIONED_IDX=$((RANDOM % ${#SANCTIONED[@]}))
    NAME="${SANCTIONED[$SANCTIONED_IDX]}"
  else
    # Generate random innocent name
    FIRST_NAMES=("John" "Jane" "Michael" "Sarah" "David" "Emily" "Robert" "Lisa" "James" "Maria")
    LAST_NAMES=("Smith" "Johnson" "Williams" "Brown" "Jones" "Garcia" "Miller" "Davis" "Martinez" "Anderson")
    
    FIRST_IDX=$((RANDOM % ${#FIRST_NAMES[@]}))
    LAST_IDX=$((RANDOM % ${#LAST_NAMES[@]}))
    
    NAME="${FIRST_NAMES[$FIRST_IDX]} ${LAST_NAMES[$LAST_IDX]}"
  fi
  
  echo "{\"requestId\":\"cust_$(printf '%06d' $i)\",\"name\":\"$NAME\",\"entityType\":\"PERSON\",\"source\":null}" >> "$OUTPUT_FILE"
  
  # Progress indicator
  if [ $((i % 10000)) -eq 0 ]; then
    echo "  Generated $i records..."
  fi
done

echo "✅ Generated ${COUNT} records in ${OUTPUT_FILE}"
echo "📊 File size: $(du -h "$OUTPUT_FILE" | cut -f1)"
echo "🎯 Expected matches: ~$((COUNT / 1000)) sanctioned entities"
echo ""
echo "Next steps:"
echo "  1. Upload to S3:"
echo "     aws s3 cp $OUTPUT_FILE s3://watchman-input/$OUTPUT_FILE"
echo ""
echo "  2. Submit bulk job:"
echo "     curl -X POST http://localhost:8084/v2/batch/bulk-job \\"
echo "       -H 'Content-Type: application/json' \\"
echo "       -d '{\"jobName\":\"${COUNT}-customer-screening\",\"minMatch\":0.88,\"limit\":10,\"s3InputPath\":\"s3://watchman-input/$OUTPUT_FILE\"}'"
