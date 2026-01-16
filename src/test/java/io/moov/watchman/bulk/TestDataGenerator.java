package io.moov.watchman.bulk;

import net.datafaker.Faker;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Generate realistic test data using DataFaker library.
 * 
 * Usage:
 *   mvn exec:java -Dexec.mainClass="io.moov.watchman.bulk.TestDataGenerator" -Dexec.args="100000"
 * 
 * Or run from IDE with args: 100000
 */
public class TestDataGenerator {
    
    private static final Faker faker = new Faker(Locale.US);
    private static final Random random = new Random();
    
    // Real OFAC sanctioned entities (injected every N records for positive matches)
    private static final List<String> SANCTIONED_NAMES = List.of(
        "Nicolas Maduro",
        "Vladimir Putin",
        "Osama Bin Laden",
        "Joaquin Guzman Loera", // El Chapo
        "Pablo Escobar",
        "Kim Jong Un",
        "Saddam Hussein",
        "Muammar Gaddafi",
        "Bashar al-Assad",
        "Ali Khamenei"
    );
    
    public static void main(String[] args) {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        String outputFile = "test-data-" + count + ".ndjson";
        
        System.out.println("🔨 Generating " + count + " test records using DataFaker...");
        System.out.println("📦 Library: net.datafaker v2.1.0");
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            for (int i = 1; i <= count; i++) {
                String name;
                
                // Every 1000th record: inject known sanctioned entity
                if (i % 1000 == 0) {
                    name = SANCTIONED_NAMES.get(random.nextInt(SANCTIONED_NAMES.size()));
                } else {
                    // Generate realistic diverse name using DataFaker
                    name = faker.name().fullName();
                }
                
                // Generate NDJSON record
                String requestId = String.format("cust_%06d", i);
                String entityType = random.nextDouble() > 0.2 ? "PERSON" : "BUSINESS";
                
                String json = String.format(
                    "{\"requestId\":\"%s\",\"name\":\"%s\",\"entityType\":\"%s\",\"source\":null}",
                    requestId, 
                    escapeName(name), 
                    entityType
                );
                
                writer.write(json);
                writer.newLine();
                
                // Progress indicator
                if (i % 10000 == 0) {
                    System.out.println("  Generated " + i + " records...");
                }
            }
            
            System.out.println("✅ Generated " + count + " records in " + outputFile);
            System.out.println("🎯 Expected matches: ~" + (count / 1000) + " sanctioned entities");
            System.out.println("📊 Diversity: DataFaker generates unique names (no repetition like bash arrays)");
            System.out.println("");
            System.out.println("Next steps:");
            System.out.println("  1. Upload to S3:");
            System.out.println("     aws s3 cp " + outputFile + " s3://watchman-input/" + outputFile);
            System.out.println("");
            System.out.println("  2. Submit bulk job:");
            System.out.println("     curl -X POST http://localhost:8084/v2/batch/bulk-job \\");
            System.out.println("       -H 'Content-Type: application/json' \\");
            System.out.println("       -d '{\"jobName\":\"" + count + "-customer-screening\",\"minMatch\":0.88,\"limit\":10,\"s3InputPath\":\"s3://watchman-input/" + outputFile + "\"}'");
            
        } catch (IOException e) {
            System.err.println("❌ Failed to generate test data: " + e.getMessage());
            System.exit(1);
        }
    }
    
    /**
     * Escape special characters in names for JSON.
     */
    private static String escapeName(String name) {
        return name.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
