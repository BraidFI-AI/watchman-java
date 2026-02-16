package io.moov.watchman.api;

import io.moov.watchman.index.EntityIndex;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Infrastructure observability endpoint for Admin UI.
 * Provides application-level metrics (JVM, entity count) and AWS infrastructure metrics (ECS, CloudWatch, ALB).
 */
@RestController
@RequestMapping("/api/admin")
public class InfrastructureController {

    private final EntityIndex entityIndex;
    private final EcsClient ecsClient;
    private final CloudWatchClient cloudWatchClient;
    
    @Value("${AWS_REGION:us-east-1}")
    private String awsRegion;
    
    @Value("${ECS_CLUSTER_NAME:watchman-java-cluster}")
    private String ecsClusterName;
    
    @Value("${ECS_SERVICE_NAME:watchman-java-service}")
    private String ecsServiceName;
    
    @Value("${ALB_NAME:watchman-java-alb}")
    private String albName;

    public InfrastructureController(EntityIndex entityIndex, EcsClient ecsClient, CloudWatchClient cloudWatchClient) {
        this.entityIndex = entityIndex;
        this.ecsClient = ecsClient;
        this.cloudWatchClient = cloudWatchClient;
    }

    @GetMapping("/infrastructure")
    public ResponseEntity<InfrastructureResponse> getInfrastructure() {
        ApplicationMetrics appMetrics = getApplicationMetrics();
        AwsLinks links = getAwsLinks();
        EcsMetrics ecsMetrics = getEcsMetrics();
        CloudWatchMetrics cwMetrics = getCloudWatchMetrics();
        
        return ResponseEntity.ok(new InfrastructureResponse(appMetrics, links, ecsMetrics, cwMetrics));
    }

    private ApplicationMetrics getApplicationMetrics() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        int threadCount = threadBean.getThreadCount();
        int entityCount = entityIndex.size();
        
        return new ApplicationMetrics(entityCount, heapUsed, heapMax, uptime, threadCount);
    }

    private AwsLinks getAwsLinks() {
        String cloudWatchLogs = String.format(
            "https://console.aws.amazon.com/cloudwatch/home?region=%s#logsV2:log-groups/log-group/%%2Fecs%%2Fwatchman-java",
            awsRegion
        );
        String ecsService = String.format(
            "https://console.aws.amazon.com/ecs/v2/clusters/%s/services/%s?region=%s",
            ecsClusterName, ecsServiceName, awsRegion
        );
        String alb = String.format(
            "https://console.aws.amazon.com/ec2/home?region=%s#LoadBalancers:search=%s",
            awsRegion, albName
        );
        
        return new AwsLinks(cloudWatchLogs, ecsService, alb);
    }

    private EcsMetrics getEcsMetrics() {
        try {
            DescribeServicesRequest request = DescribeServicesRequest.builder()
                .cluster(ecsClusterName)
                .services(ecsServiceName)
                .build();
            
            DescribeServicesResponse response = ecsClient.describeServices(request);
            
            if (response.services().isEmpty()) {
                return null;
            }
            
            Service service = response.services().get(0);
            int runningCount = service.runningCount();
            int desiredCount = service.desiredCount();
            String status = service.status();
            
            // Get task definition revision
            String taskDefArn = service.taskDefinition();
            String taskRevision = taskDefArn.substring(taskDefArn.lastIndexOf(":") + 1);
            
            // Get latest deployment time
            Instant lastDeployment = null;
            if (!service.deployments().isEmpty()) {
                lastDeployment = service.deployments().get(0).createdAt();
            }
            
            return new EcsMetrics(runningCount, desiredCount, status, taskRevision, lastDeployment);
        } catch (Exception e) {
            // Return null if not running in AWS or missing permissions
            return null;
        }
    }

    private CloudWatchMetrics getCloudWatchMetrics() {
        try {
            Instant endTime = Instant.now();
            Instant startTime = endTime.minus(5, ChronoUnit.MINUTES);
            
            // ECS Container CPU
            Double cpuUtilization = getMetricAverage("AWS/ECS", "CPUUtilization", 
                List.of(
                    Dimension.builder().name("ServiceName").value(ecsServiceName).build(),
                    Dimension.builder().name("ClusterName").value(ecsClusterName).build()
                ), startTime, endTime);
            
            // ECS Container Memory
            Double memoryUtilization = getMetricAverage("AWS/ECS", "MemoryUtilization",
                List.of(
                    Dimension.builder().name("ServiceName").value(ecsServiceName).build(),
                    Dimension.builder().name("ClusterName").value(ecsClusterName).build()
                ), startTime, endTime);
            
            // ALB Request Count (last hour for more data points)
            Instant albStart = endTime.minus(1, ChronoUnit.HOURS);
            Double requestCount = getMetricSum("AWS/ApplicationELB", "RequestCount",
                List.of(
                    Dimension.builder().name("LoadBalancer").value(getAlbDimension()).build()
                ), albStart, endTime);
            
            // ALB Target Response Time
            Double responseTime = getMetricAverage("AWS/ApplicationELB", "TargetResponseTime",
                List.of(
                    Dimension.builder().name("LoadBalancer").value(getAlbDimension()).build()
                ), albStart, endTime);
            
            // ALB Healthy Host Count
            Double healthyHosts = getMetricAverage("AWS/ApplicationELB", "HealthyHostCount",
                List.of(
                    Dimension.builder().name("LoadBalancer").value(getAlbDimension()).build()
                ), startTime, endTime);
            
            return new CloudWatchMetrics(cpuUtilization, memoryUtilization, requestCount, responseTime, healthyHosts);
        } catch (Exception e) {
            // Return null if not running in AWS or missing permissions
            return null;
        }
    }

    private Double getMetricAverage(String namespace, String metricName, List<Dimension> dimensions, 
                                    Instant startTime, Instant endTime) {
        GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder()
            .namespace(namespace)
            .metricName(metricName)
            .dimensions(dimensions)
            .startTime(startTime)
            .endTime(endTime)
            .period(300) // 5 minutes
            .statistics(Statistic.AVERAGE)
            .build();
        
        GetMetricStatisticsResponse response = cloudWatchClient.getMetricStatistics(request);
        
        if (response.datapoints().isEmpty()) {
            return null;
        }
        
        // Return most recent datapoint
        return response.datapoints().stream()
            .max((d1, d2) -> d1.timestamp().compareTo(d2.timestamp()))
            .map(Datapoint::average)
            .orElse(null);
    }

    private Double getMetricSum(String namespace, String metricName, List<Dimension> dimensions,
                                Instant startTime, Instant endTime) {
        GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder()
            .namespace(namespace)
            .metricName(metricName)
            .dimensions(dimensions)
            .startTime(startTime)
            .endTime(endTime)
            .period(3600) // 1 hour
            .statistics(Statistic.SUM)
            .build();
        
        GetMetricStatisticsResponse response = cloudWatchClient.getMetricStatistics(request);
        
        if (response.datapoints().isEmpty()) {
            return null;
        }
        
        return response.datapoints().stream()
            .mapToDouble(Datapoint::sum)
            .sum();
    }

    private String getAlbDimension() {
        // ALB dimension format: app/watchman-java-alb/1239419410 (from ALB ARN)
        // Extract from full ARN if available, otherwise construct
        return "app/" + albName + "/1239419410";
    }

    // Response DTOs
    public record InfrastructureResponse(
        ApplicationMetrics application,
        AwsLinks links,
        EcsMetrics ecs,
        CloudWatchMetrics cloudwatch
    ) {}
    
    public record ApplicationMetrics(
        int entityCount,
        long jvmHeapUsed,
        long jvmHeapMax,
        long jvmUptime,
        int threadCount
    ) {}
    
    public record AwsLinks(
        String cloudWatchLogs,
        String ecsService,
        String alb
    ) {}
    
    public record EcsMetrics(
        int runningTasks,
        int desiredTasks,
        String status,
        String taskRevision,
        Instant lastDeployment
    ) {}
    
    public record CloudWatchMetrics(
        Double cpuUtilization,
        Double memoryUtilization,
        Double requestCount,
        Double avgResponseTime,
        Double healthyHosts
    ) {}
}
