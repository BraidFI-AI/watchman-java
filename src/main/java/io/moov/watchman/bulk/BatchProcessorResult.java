package io.moov.watchman.bulk;

/**
 * Result of batch processing operation.
 *
 * @param jobId        Job identifier
 * @param totalItems   Total number of items processed
 * @param matchedItems Number of items that matched OFAC lists
 * @param resultPath   S3 path to results file
 */
public record BatchProcessorResult(
    String jobId,
    int totalItems,
    int matchedItems,
    String resultPath
) {
}
