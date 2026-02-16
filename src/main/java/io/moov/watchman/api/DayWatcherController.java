package io.moov.watchman.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.DBInstance;
import software.amazon.awssdk.services.rds.model.DescribeDbInstancesRequest;
import software.amazon.awssdk.services.rds.model.DescribeDbInstancesResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class DayWatcherController {

    private final LambdaClient lambdaClient;
    private final RdsClient rdsClient;
    private final EcsClient ecsClient;
    private final CloudWatchClient cloudWatchClient;
    private final S3Client s3Client;
    
    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    // Environment-based configuration with defaults
    private static final String LAMBDA_FUNCTION_NAME = System.getenv().getOrDefault("DAY_WATCHER_LAMBDA_NAME", "day-watcher-orchestrator");
    private static final String RDS_INSTANCE_ID = System.getenv().getOrDefault("DAY_WATCHER_RDS_INSTANCE", "day-watcher-db");
    private static final String ECS_CLUSTER_NAME = System.getenv().getOrDefault("DAY_WATCHER_ECS_CLUSTER", "day-watcher-cluster");
    private static final String ECS_TASK_FAMILY = System.getenv().getOrDefault("DAY_WATCHER_ECS_TASK_FAMILY", "day-watcher-task");
    private static final String S3_INPUT_BUCKET = System.getenv().getOrDefault("DAY_WATCHER_INPUT_BUCKET", "day-watcher-input");
    private static final String S3_OUTPUT_BUCKET = System.getenv().getOrDefault("DAY_WATCHER_OUTPUT_BUCKET", "day-watcher-results");
    private static final String AWS_REGION = System.getenv().getOrDefault("AWS_REGION", "us-east-1");

    public DayWatcherController(LambdaClient lambdaClient, RdsClient rdsClient, EcsClient ecsClient, 
                                 CloudWatchClient cloudWatchClient, S3Client s3Client) {
        this.lambdaClient = lambdaClient;
        this.rdsClient = rdsClient;
        this.ecsClient = ecsClient;
        this.cloudWatchClient = cloudWatchClient;
        this.s3Client = s3Client;
    }

    @GetMapping("/day-watcher")
    public DayWatcherResponse getDayWatcherMetrics() {
        return new DayWatcherResponse(
            getLambdaMetrics(),
            getRdsMetrics(),
            getEcsTaskMetrics(),
            getS3Metrics(),
            getAwsLinks()
        );
    }

    private LambdaMetrics getLambdaMetrics() {
        try {
            // Get Lambda function info
            GetFunctionResponse function = lambdaClient.getFunction(
                GetFunctionRequest.builder()
                    .functionName(LAMBDA_FUNCTION_NAME)
                    .build()
            );

            // Get CloudWatch metrics for Lambda
            Instant now = Instant.now();
            Instant dayAgo = now.minus(24, ChronoUnit.HOURS);

            // Invocation count (24h)
            Double invocationCount = getMetricSum("AWS/Lambda", "Invocations", "FunctionName", LAMBDA_FUNCTION_NAME, dayAgo, now);
            
            // Average duration (24h)
            Double avgDuration = getMetricAverage("AWS/Lambda", "Duration", "FunctionName", LAMBDA_FUNCTION_NAME, dayAgo, now);
            
            // Error count (24h)
            Double errorCount = getMetricSum("AWS/Lambda", "Errors", "FunctionName", LAMBDA_FUNCTION_NAME, dayAgo, now);
            
            // Last invocation time
            String lastInvocation = function.configuration().lastModified(); // Approximation, CloudWatch Insights would be more accurate

            return new LambdaMetrics(
                LAMBDA_FUNCTION_NAME,
                lastInvocation,
                invocationCount != null ? invocationCount.intValue() : null,
                avgDuration,
                errorCount != null ? errorCount.intValue() : null
            );
        } catch (Exception e) {
            // Return null if not in AWS environment
            return null;
        }
    }

    private RdsMetrics getRdsMetrics() {
        try {
            // Get RDS instance info
            DescribeDbInstancesResponse rdsResponse = rdsClient.describeDBInstances(
                DescribeDbInstancesRequest.builder()
                    .dbInstanceIdentifier(RDS_INSTANCE_ID)
                    .build()
            );

            if (rdsResponse.dbInstances().isEmpty()) {
                return null;
            }

            DBInstance instance = rdsResponse.dbInstances().get(0);

            // Get CloudWatch metrics for RDS
            Instant now = Instant.now();
            Instant fiveMinAgo = now.minus(5, ChronoUnit.MINUTES);

            Double cpuUtilization = getMetricAverage("AWS/RDS", "CPUUtilization", "DBInstanceIdentifier", RDS_INSTANCE_ID, fiveMinAgo, now);
            Double connections = getMetricAverage("AWS/RDS", "DatabaseConnections", "DBInstanceIdentifier", RDS_INSTANCE_ID, fiveMinAgo, now);
            Double freeStorage = getMetricAverage("AWS/RDS", "FreeStorageSpace", "DBInstanceIdentifier", RDS_INSTANCE_ID, fiveMinAgo, now);
            Double readIops = getMetricAverage("AWS/RDS", "ReadIOPS", "DBInstanceIdentifier", RDS_INSTANCE_ID, fiveMinAgo, now);
            Double writeIops = getMetricAverage("AWS/RDS", "WriteIOPS", "DBInstanceIdentifier", RDS_INSTANCE_ID, fiveMinAgo, now);

            return new RdsMetrics(
                RDS_INSTANCE_ID,
                instance.dbInstanceStatus(),
                cpuUtilization,
                connections != null ? connections.intValue() : null,
                freeStorage != null ? (freeStorage / 1_073_741_824) : null, // Convert bytes to GB
                readIops,
                writeIops,
                instance.allocatedStorage()
            );
        } catch (Exception e) {
            return null;
        }
    }

    private EcsTaskMetrics getEcsTaskMetrics() {
        try {
            // List tasks in cluster
            ListTasksResponse listTasksResponse = ecsClient.listTasks(
                ListTasksRequest.builder()
                    .cluster(ECS_CLUSTER_NAME)
                    .family(ECS_TASK_FAMILY)
                    .maxResults(10)
                    .build()
            );

            if (listTasksResponse.taskArns().isEmpty()) {
                // No tasks running, return default state
                return new EcsTaskMetrics(
                    "STOPPED",
                    null,
                    0,
                    0,
                    null,
                    null
                );
            }

            // Describe tasks to get details
            DescribeTasksResponse tasksResponse = ecsClient.describeTasks(
                DescribeTasksRequest.builder()
                    .cluster(ECS_CLUSTER_NAME)
                    .tasks(listTasksResponse.taskArns())
                    .build()
            );

            List<Task> tasks = tasksResponse.tasks();
            
            // Find most recent task
            Task latestTask = tasks.stream()
                .max(Comparator.comparing(Task::createdAt))
                .orElse(null);

            if (latestTask == null) {
                return null;
            }

            long runningCount = tasks.stream().filter(t -> "RUNNING".equals(t.lastStatus())).count();
            long stoppedCount = tasks.stream().filter(t -> "STOPPED".equals(t.lastStatus())).count();

            return new EcsTaskMetrics(
                latestTask.lastStatus(),
                latestTask.createdAt().toString(),
                (int) runningCount,
                (int) stoppedCount,
                latestTask.cpu(),
                latestTask.memory()
            );
        } catch (Exception e) {
            return null;
        }
    }

    private S3Metrics getS3Metrics() {
        try {
            // Count objects in input bucket
            ListObjectsV2Response inputResponse = s3Client.listObjectsV2(
                ListObjectsV2Request.builder()
                    .bucket(S3_INPUT_BUCKET)
                    .maxKeys(1000)
                    .build()
            );

            // Count objects in output bucket
            ListObjectsV2Response outputResponse = s3Client.listObjectsV2(
                ListObjectsV2Request.builder()
                    .bucket(S3_OUTPUT_BUCKET)
                    .maxKeys(1000)
                    .build()
            );

            // Get most recent object timestamp
            String lastModified = inputResponse.contents().stream()
                .map(S3Object::lastModified)
                .max(Comparator.naturalOrder())
                .map(Instant::toString)
                .orElse(null);

            long inputSize = inputResponse.contents().stream().mapToLong(S3Object::size).sum();
            long outputSize = outputResponse.contents().stream().mapToLong(S3Object::size).sum();

            return new S3Metrics(
                inputResponse.keyCount(),
                outputResponse.keyCount(),
                inputSize / 1_048_576, // Convert to MB
                outputSize / 1_048_576,
                lastModified
            );
        } catch (Exception e) {
            return null;
        }
    }

    private DayWatcherLinks getAwsLinks() {
        String baseUrl = "https://console.aws.amazon.com";
        return new DayWatcherLinks(
            String.format("%s/lambda/home?region=%s#/functions/%s", baseUrl, AWS_REGION, LAMBDA_FUNCTION_NAME),
            String.format("%s/rds/home?region=%s#database:id=%s", baseUrl, AWS_REGION, RDS_INSTANCE_ID),
            String.format("%s/ecs/v2/clusters/%s/tasks", baseUrl, ECS_CLUSTER_NAME),
            String.format("%s/s3/buckets/%s", baseUrl, S3_INPUT_BUCKET),
            String.format("%s/s3/buckets/%s", baseUrl, S3_OUTPUT_BUCKET),
            String.format("%s/cloudwatch/home?region=%s#logsV2:log-groups/log-group/$252Faws$252Flambda$252F%s", 
                baseUrl, AWS_REGION, LAMBDA_FUNCTION_NAME)
        );
    }

    private Double getMetricAverage(String namespace, String metricName, String dimensionName, 
                                     String dimensionValue, Instant startTime, Instant endTime) {
        try {
            GetMetricStatisticsResponse response = cloudWatchClient.getMetricStatistics(
                GetMetricStatisticsRequest.builder()
                    .namespace(namespace)
                    .metricName(metricName)
                    .dimensions(Dimension.builder().name(dimensionName).value(dimensionValue).build())
                    .startTime(startTime)
                    .endTime(endTime)
                    .period(300) // 5 minutes
                    .statistics(Statistic.AVERAGE)
                    .build()
            );

            List<Datapoint> datapoints = response.datapoints();
            if (datapoints.isEmpty()) {
                return null;
            }

            return datapoints.stream()
                .mapToDouble(Datapoint::average)
                .average()
                .orElse(0.0);
        } catch (Exception e) {
            return null;
        }
    }

    private Double getMetricSum(String namespace, String metricName, String dimensionName, 
                                 String dimensionValue, Instant startTime, Instant endTime) {
        try {
            GetMetricStatisticsResponse response = cloudWatchClient.getMetricStatistics(
                GetMetricStatisticsRequest.builder()
                    .namespace(namespace)
                    .metricName(metricName)
                    .dimensions(Dimension.builder().name(dimensionName).value(dimensionValue).build())
                    .startTime(startTime)
                    .endTime(endTime)
                    .period(3600) // 1 hour
                    .statistics(Statistic.SUM)
                    .build()
            );

            List<Datapoint> datapoints = response.datapoints();
            if (datapoints.isEmpty()) {
                return null;
            }

            return datapoints.stream()
                .mapToDouble(Datapoint::sum)
                .sum();
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("/day-watcher/runs")
    public RunsListResponse getRecentRuns(
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(required = false) String status
    ) {
        if (jdbcTemplate == null) {
            return new RunsListResponse(
                List.of(),
                "Database not configured. Set DAY_WATCHER_DB_URL environment variable."
            );
        }

        try {
            String sql = "SELECT run_id, run_date, status, start_time, end_time, " +
                        "entities_fetched_from_braid, entities_written_to_db, entities_in_ndjson, " +
                        "has_discrepancy, s3_input_path, s3_output_path, error_message, created_at " +
                        "FROM runs " +
                        (status != null ? "WHERE status = ? " : "") +
                        "ORDER BY created_at DESC LIMIT ?";

            List<RunRecord> runs = status != null
                ? jdbcTemplate.query(sql, this::mapRunRow, status, limit)
                : jdbcTemplate.query(sql, this::mapRunRow, limit);

            return new RunsListResponse(runs, null);
        } catch (Exception e) {
            return new RunsListResponse(
                List.of(),
                "Failed to query database: " + e.getMessage()
            );
        }
    }

    private RunRecord mapRunRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new RunRecord(
            rs.getString("run_id"),
            rs.getDate("run_date") != null ? rs.getDate("run_date").toString() : null,
            rs.getString("status"),
            rs.getTimestamp("start_time") != null ? rs.getTimestamp("start_time").toInstant().toString() : null,
            rs.getTimestamp("end_time") != null ? rs.getTimestamp("end_time").toInstant().toString() : null,
            rs.getInt("entities_fetched_from_braid"),
            rs.getInt("entities_written_to_db"),
            rs.getInt("entities_in_ndjson"),
            rs.getBoolean("has_discrepancy"),
            rs.getString("s3_input_path"),
            rs.getString("s3_output_path"),
            rs.getString("error_message"),
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant().toString() : null
        );
    }

    // DTOs
    public record DayWatcherResponse(
        LambdaMetrics lambda,
        RdsMetrics rds,
        EcsTaskMetrics ecsTask,
        S3Metrics s3,
        DayWatcherLinks links
    ) {}

    public record LambdaMetrics(
        String functionName,
        String lastInvocation,
        Integer invocationCount24h,
        Double averageDuration,
        Integer errorCount24h
    ) {}

    public record RdsMetrics(
        String dbInstanceIdentifier,
        String status,
        Double cpuUtilization,
        Integer databaseConnections,
        Double freeStorageSpace,
        Double readIops,
        Double writeIops,
        Integer allocatedStorage
    ) {}

    public record EcsTaskMetrics(
        String lastTaskStatus,
        String lastRunTime,
        Integer runningTaskCount,
        Integer stoppedTaskCount,
        String cpu,
        String memory
    ) {}

    public record S3Metrics(
        Integer inputObjectCount,
        Integer outputObjectCount,
        Long inputBucketSizeMb,
        Long outputBucketSizeMb,
        String lastModified
    ) {}

    public record DayWatcherLinks(
        String lambdaFunction,
        String rdsInstance,
        String ecsCluster,
        String s3InputBucket,
        String s3OutputBucket,
        String cloudWatchLogs
    ) {}

    public record RunsListResponse(
        List<RunRecord> runs,
        String error
    ) {}

    public record RunRecord(
        String runId,
        String runDate,
        String status,
        String startTime,
        String endTime,
        Integer entitiesFetchedFromBraid,
        Integer entitiesWrittenToDb,
        Integer entitiesInNdjson,
        Boolean hasDiscrepancy,
        String s3InputPath,
        String s3OutputPath,
        String errorMessage,
        String createdAt
    ) {}
}
