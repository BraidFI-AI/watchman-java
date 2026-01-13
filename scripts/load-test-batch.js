/**
 * k6 Load Test - Batch Screening (Max 1000 items)
 * 
 * Tests the /v2/search/batch endpoint with maximum batch size (1000 items)
 * 
 * Install k6:
 *   brew install k6
 * 
 * Run:
 *   k6 run scripts/load-test-batch.js
 * 
 * Custom options:
 *   k6 run scripts/load-test-batch.js --vus 10 --duration 2m
 *   BASE_URL=https://watchman-java.fly.dev k6 run scripts/load-test-batch.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const batchProcessingTime = new Trend('batch_processing_time');

// Configuration
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8084';

// Load test stages
export const options = {
  stages: [
    { duration: '30s', target: 5 },    // Warm up: 5 concurrent users
    { duration: '1m', target: 10 },    // Ramp up to 10 users
    { duration: '2m', target: 10 },    // Maintain 10 users (steady state)
    { duration: '1m', target: 20 },    // Spike to 20 users
    { duration: '1m', target: 20 },    // Hold spike
    { duration: '30s', target: 0 },    // Ramp down
  ],
  thresholds: {
    'http_req_duration': ['p(95)<10000'],  // 95% of requests under 10s
    'http_req_failed': ['rate<0.05'],      // Less than 5% errors
    'errors': ['rate<0.05'],               // Less than 5% validation errors
  },
};

// Sample names for realistic test data
const firstNames = [
  'John', 'Mohammad', 'Maria', 'Ahmed', 'Li', 'Carlos', 'Fatima', 'Ivan',
  'Anna', 'Hassan', 'Sofia', 'Dmitry', 'Elena', 'Omar', 'Yuki', 'Pierre',
  'Olga', 'Ali', 'Chen', 'Juan', 'Aisha', 'Sergei', 'Mei', 'Diego',
  'Anya', 'Mahmoud', 'Rosa', 'Vladimir', 'Leila', 'Hiroshi', 'Isabella'
];

const lastNames = [
  'Smith', 'Ali', 'Garcia', 'Wang', 'Rodriguez', 'Ivanov', 'Kim', 'Brown',
  'Hussein', 'Martinez', 'Petrov', 'Lopez', 'Hassan', 'Gonzalez', 'Lee',
  'Wilson', 'Mohammed', 'Hernandez', 'Sidorov', 'Perez', 'Khan', 'Taylor',
  'Anderson', 'Thomas', 'Sokolov', 'Jackson', 'Ahmed', 'White', 'Ibrahim'
];

const companies = [
  'Trading', 'Bank', 'Corporation', 'Industries', 'Holdings', 'Group',
  'International', 'Enterprises', 'Limited', 'Company', 'Services', 'Systems',
  'Technologies', 'Export', 'Import', 'Finance', 'Investment', 'Global'
];

/**
 * Generate a random name
 */
function generateRandomName() {
  const type = Math.random();
  
  if (type < 0.7) {
    // 70% persons
    const firstName = firstNames[Math.floor(Math.random() * firstNames.length)];
    const lastName = lastNames[Math.floor(Math.random() * lastNames.length)];
    return `${firstName} ${lastName}`;
  } else {
    // 30% businesses
    const prefix = lastNames[Math.floor(Math.random() * lastNames.length)];
    const suffix = companies[Math.floor(Math.random() * companies.length)];
    return `${prefix} ${suffix}`;
  }
}

/**
 * Generate a batch of 1000 items
 */
function generateBatch(size = 1000) {
  const items = [];
  for (let i = 0; i < size; i++) {
    items.push({
      id: `BATCH_${Date.now()}_${i}`,
      name: generateRandomName()
    });
  }
  
  return {
    items: items,
    minMatch: 0.85,
    limit: 10
  };
}

/**
 * Main test function - executed for each virtual user iteration
 */
export default function() {
  // Generate batch payload
  const batch = generateBatch(1000);
  
  // Send batch request
  const startTime = Date.now();
  const response = http.post(
    `${BASE_URL}/v2/search/batch`,
    JSON.stringify(batch),
    {
      headers: { 'Content-Type': 'application/json' },
      timeout: '30s',
    }
  );
  const duration = Date.now() - startTime;
  
  // Record custom metric
  batchProcessingTime.add(duration);
  
  // Validate response
  const success = check(response, {
    'status is 200': (r) => r.status === 200,
    'has results': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.results && Array.isArray(body.results);
      } catch {
        return false;
      }
    },
    'has statistics': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.statistics && body.statistics.totalItems === 1000;
      } catch {
        return false;
      }
    },
    'response time < 15s': (r) => r.timings.duration < 15000,
  });
  
  // Track errors
  errorRate.add(!success);
  
  // Log failures
  if (!success) {
    console.error(`Request failed: ${response.status} - ${response.body.substring(0, 200)}`);
  }
  
  // Realistic pacing - wait between requests
  sleep(Math.random() * 3 + 2); // 2-5 seconds between batches
}

/**
 * Setup - runs once before the test
 */
export function setup() {
  console.log(`🚀 Starting load test against: ${BASE_URL}`);
  console.log(`📊 Batch size: 1000 items (maximum)`);
  console.log(`⏱️  Total duration: ~6 minutes`);
  
  // Health check
  const healthCheck = http.get(`${BASE_URL}/health`, { timeout: '10s' });
  if (healthCheck.status !== 200) {
    throw new Error(`Health check failed: ${healthCheck.status}`);
  }
  console.log(`✅ Health check passed`);
  
  return { startTime: Date.now() };
}

/**
 * Teardown - runs once after the test
 */
export function teardown(data) {
  const duration = ((Date.now() - data.startTime) / 1000).toFixed(1);
  console.log(`\n✅ Load test completed in ${duration}s`);
}
