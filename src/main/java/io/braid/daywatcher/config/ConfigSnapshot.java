package io.braid.daywatcher.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Serializable snapshot of all scoring configuration.
 * Written to S3 on every Admin UI save and read back on startup.
 * All 84 parameters: 18 similarity + 54 weights + 3 auto-clearance + 2 webhook + 7 search.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfigSnapshot {

    // Similarity (18)
    public double jaroWinklerBoostThreshold;
    public int jaroWinklerPrefixSize;
    public double winklerPrefixWeight;
    public int minimumTokenLength;
    public double phoneticLengthDifferenceThreshold;
    public double shortTokenRatioThreshold;
    public double lengthDifferencePenaltyWeight;
    public double lengthDifferenceCutoffFactor;
    public double differentLetterPenaltyWeight;
    public double exactMatchFavoritism;
    public double unmatchedIndexTokenWeight;
    public boolean phoneticFilteringDisabled;
    public boolean keepStopwords;
    public boolean logStopwordDebugging;
    public double queryCoverageQualityThreshold;
    public double queryCoverageBoostMultiplier;
    public double tokenBlendWeight;
    public double languageDetectionMinConfidence;

    // Weights (54)
    public double nameWeight;
    public double addressWeight;
    public double criticalIdWeight;
    public double supportingInfoWeight;
    public double minimumScore;
    public double exactMatchThreshold;
    public boolean nameComparisonEnabled;
    public boolean altNameComparisonEnabled;
    public boolean addressComparisonEnabled;
    public boolean govIdComparisonEnabled;
    public boolean cryptoComparisonEnabled;
    public boolean contactComparisonEnabled;
    public boolean dateComparisonEnabled;
    public double aliasTieBreakerThreshold;
    public double exactMatchCriticalIdThreshold;
    public double exactMatchIdWeight;
    public double exactMatchNameWeight;
    public double aliasScoreMultiplier;
    public double aliasMinimumScore;
    public double aliasBoostMaxScore;
    public double aliasBoostAmount;
    public double addressLine1Weight;
    public double addressLine2Weight;
    public double addressCityWeight;
    public double addressStateWeight;
    public double addressPostalWeight;
    public double addressCountryWeight;
    public double addressHighConfidenceThreshold;
    public double dateYearDecayRate;
    public double dateDistantYearScore;
    public double dateMonthTolerance1;
    public double dateMonthTolerance2;
    public double dateMonthTolerance3Plus;
    public double dateDayTolerance0to3Start;
    public double dateDayTolerance0to3Decay;
    public double dateDayTolerance4to7;
    public double dateDayTolerance8Plus;
    public double dateYearWeight;
    public double dateMonthWeight;
    public double dateDayWeight;
    public double supportingInfoMatchedThreshold;
    public double supportingInfoExactThreshold;
    public double supportingInfoSecondaryPenalty;
    public double titleMatchedThreshold;
    public double titleExactThreshold;
    public double affiliationNameThreshold;
    public double affiliationExactThreshold;
    public double affiliationTypeScoreThreshold;
    public double nameEarlyExitThreshold;
    public double scorerAddressCountryWeight;
    public double scorerAddressCityWeight;
    public double scorerAddressLineWeight;
    public double aliasSelectionTolerance;
    public double aliasCoverageMinScore;

    // Auto-clearance (3)
    public double phase1Threshold;
    public double addressMismatchThreshold;
    public long dobDifferenceThresholdYears;

    // Webhook (2)
    public boolean webhookEnabled;
    public String webhookRefreshNotificationUrl;

    public static ConfigSnapshot from(
            SimilarityConfig s,
            WeightConfig w,
            AutoClearanceConfig a,
            WebhookConfig wh) {
        ConfigSnapshot snap = new ConfigSnapshot();

        snap.jaroWinklerBoostThreshold = s.getJaroWinklerBoostThreshold();
        snap.jaroWinklerPrefixSize = s.getJaroWinklerPrefixSize();
        snap.winklerPrefixWeight = s.getWinklerPrefixWeight();
        snap.minimumTokenLength = s.getMinimumTokenLength();
        snap.phoneticLengthDifferenceThreshold = s.getPhoneticLengthDifferenceThreshold();
        snap.shortTokenRatioThreshold = s.getShortTokenRatioThreshold();
        snap.lengthDifferencePenaltyWeight = s.getLengthDifferencePenaltyWeight();
        snap.lengthDifferenceCutoffFactor = s.getLengthDifferenceCutoffFactor();
        snap.differentLetterPenaltyWeight = s.getDifferentLetterPenaltyWeight();
        snap.exactMatchFavoritism = s.getExactMatchFavoritism();
        snap.unmatchedIndexTokenWeight = s.getUnmatchedIndexTokenWeight();
        snap.phoneticFilteringDisabled = s.isPhoneticFilteringDisabled();
        snap.keepStopwords = s.isKeepStopwords();
        snap.logStopwordDebugging = s.isLogStopwordDebugging();
        snap.queryCoverageQualityThreshold = s.getQueryCoverageQualityThreshold();
        snap.queryCoverageBoostMultiplier = s.getQueryCoverageBoostMultiplier();
        snap.tokenBlendWeight = s.getTokenBlendWeight();
        snap.languageDetectionMinConfidence = s.getLanguageDetectionMinConfidence();

        snap.nameWeight = w.getNameWeight();
        snap.addressWeight = w.getAddressWeight();
        snap.criticalIdWeight = w.getCriticalIdWeight();
        snap.supportingInfoWeight = w.getSupportingInfoWeight();
        snap.minimumScore = w.getMinimumScore();
        snap.exactMatchThreshold = w.getExactMatchThreshold();
        snap.nameComparisonEnabled = w.isNameComparisonEnabled();
        snap.altNameComparisonEnabled = w.isAltNameComparisonEnabled();
        snap.addressComparisonEnabled = w.isAddressComparisonEnabled();
        snap.govIdComparisonEnabled = w.isGovIdComparisonEnabled();
        snap.cryptoComparisonEnabled = w.isCryptoComparisonEnabled();
        snap.contactComparisonEnabled = w.isContactComparisonEnabled();
        snap.dateComparisonEnabled = w.isDateComparisonEnabled();
        snap.aliasTieBreakerThreshold = w.getAliasTieBreakerThreshold();
        snap.exactMatchCriticalIdThreshold = w.getExactMatchCriticalIdThreshold();
        snap.exactMatchIdWeight = w.getExactMatchIdWeight();
        snap.exactMatchNameWeight = w.getExactMatchNameWeight();
        snap.aliasScoreMultiplier = w.getAliasScoreMultiplier();
        snap.aliasMinimumScore = w.getAliasMinimumScore();
        snap.aliasBoostMaxScore = w.getAliasBoostMaxScore();
        snap.aliasBoostAmount = w.getAliasBoostAmount();
        snap.addressLine1Weight = w.getAddressLine1Weight();
        snap.addressLine2Weight = w.getAddressLine2Weight();
        snap.addressCityWeight = w.getAddressCityWeight();
        snap.addressStateWeight = w.getAddressStateWeight();
        snap.addressPostalWeight = w.getAddressPostalWeight();
        snap.addressCountryWeight = w.getAddressCountryWeight();
        snap.addressHighConfidenceThreshold = w.getAddressHighConfidenceThreshold();
        snap.dateYearDecayRate = w.getDateYearDecayRate();
        snap.dateDistantYearScore = w.getDateDistantYearScore();
        snap.dateMonthTolerance1 = w.getDateMonthTolerance1();
        snap.dateMonthTolerance2 = w.getDateMonthTolerance2();
        snap.dateMonthTolerance3Plus = w.getDateMonthTolerance3Plus();
        snap.dateDayTolerance0to3Start = w.getDateDayTolerance0to3Start();
        snap.dateDayTolerance0to3Decay = w.getDateDayTolerance0to3Decay();
        snap.dateDayTolerance4to7 = w.getDateDayTolerance4to7();
        snap.dateDayTolerance8Plus = w.getDateDayTolerance8Plus();
        snap.dateYearWeight = w.getDateYearWeight();
        snap.dateMonthWeight = w.getDateMonthWeight();
        snap.dateDayWeight = w.getDateDayWeight();
        snap.supportingInfoMatchedThreshold = w.getSupportingInfoMatchedThreshold();
        snap.supportingInfoExactThreshold = w.getSupportingInfoExactThreshold();
        snap.supportingInfoSecondaryPenalty = w.getSupportingInfoSecondaryPenalty();
        snap.titleMatchedThreshold = w.getTitleMatchedThreshold();
        snap.titleExactThreshold = w.getTitleExactThreshold();
        snap.affiliationNameThreshold = w.getAffiliationNameThreshold();
        snap.affiliationExactThreshold = w.getAffiliationExactThreshold();
        snap.affiliationTypeScoreThreshold = w.getAffiliationTypeScoreThreshold();
        snap.nameEarlyExitThreshold = w.getNameEarlyExitThreshold();
        snap.scorerAddressCountryWeight = w.getScorerAddressCountryWeight();
        snap.scorerAddressCityWeight = w.getScorerAddressCityWeight();
        snap.scorerAddressLineWeight = w.getScorerAddressLineWeight();
        snap.aliasSelectionTolerance = w.getAliasSelectionTolerance();
        snap.aliasCoverageMinScore = w.getAliasCoverageMinScore();

        snap.phase1Threshold = a.getPhase1Threshold();
        snap.addressMismatchThreshold = a.getAddressMismatchThreshold();
        snap.dobDifferenceThresholdYears = a.getDobDifferenceThresholdYears();

        snap.webhookEnabled = wh.isEnabled();
        snap.webhookRefreshNotificationUrl = wh.getRefreshNotificationUrl();

        return snap;
    }

    public void applyTo(SimilarityConfig s, WeightConfig w, AutoClearanceConfig a, WebhookConfig wh) {
        s.setJaroWinklerBoostThreshold(jaroWinklerBoostThreshold);
        s.setJaroWinklerPrefixSize(jaroWinklerPrefixSize);
        s.setWinklerPrefixWeight(winklerPrefixWeight);
        s.setMinimumTokenLength(minimumTokenLength);
        s.setPhoneticLengthDifferenceThreshold(phoneticLengthDifferenceThreshold);
        s.setShortTokenRatioThreshold(shortTokenRatioThreshold);
        s.setLengthDifferencePenaltyWeight(lengthDifferencePenaltyWeight);
        s.setLengthDifferenceCutoffFactor(lengthDifferenceCutoffFactor);
        s.setDifferentLetterPenaltyWeight(differentLetterPenaltyWeight);
        s.setExactMatchFavoritism(exactMatchFavoritism);
        s.setUnmatchedIndexTokenWeight(unmatchedIndexTokenWeight);
        s.setPhoneticFilteringDisabled(phoneticFilteringDisabled);
        s.setKeepStopwords(keepStopwords);
        s.setLogStopwordDebugging(logStopwordDebugging);
        s.setQueryCoverageQualityThreshold(queryCoverageQualityThreshold);
        s.setQueryCoverageBoostMultiplier(queryCoverageBoostMultiplier);
        s.setTokenBlendWeight(tokenBlendWeight);
        s.setLanguageDetectionMinConfidence(languageDetectionMinConfidence);

        w.setNameWeight(nameWeight);
        w.setAddressWeight(addressWeight);
        w.setCriticalIdWeight(criticalIdWeight);
        w.setSupportingInfoWeight(supportingInfoWeight);
        w.setMinimumScore(minimumScore);
        w.setExactMatchThreshold(exactMatchThreshold);
        w.setNameComparisonEnabled(nameComparisonEnabled);
        w.setAltNameComparisonEnabled(altNameComparisonEnabled);
        w.setAddressComparisonEnabled(addressComparisonEnabled);
        w.setGovIdComparisonEnabled(govIdComparisonEnabled);
        w.setCryptoComparisonEnabled(cryptoComparisonEnabled);
        w.setContactComparisonEnabled(contactComparisonEnabled);
        w.setDateComparisonEnabled(dateComparisonEnabled);
        w.setAliasTieBreakerThreshold(aliasTieBreakerThreshold);
        w.setExactMatchCriticalIdThreshold(exactMatchCriticalIdThreshold);
        w.setExactMatchIdWeight(exactMatchIdWeight);
        w.setExactMatchNameWeight(exactMatchNameWeight);
        w.setAliasScoreMultiplier(aliasScoreMultiplier);
        w.setAliasMinimumScore(aliasMinimumScore);
        w.setAliasBoostMaxScore(aliasBoostMaxScore);
        w.setAliasBoostAmount(aliasBoostAmount);
        w.setAddressLine1Weight(addressLine1Weight);
        w.setAddressLine2Weight(addressLine2Weight);
        w.setAddressCityWeight(addressCityWeight);
        w.setAddressStateWeight(addressStateWeight);
        w.setAddressPostalWeight(addressPostalWeight);
        w.setAddressCountryWeight(addressCountryWeight);
        w.setAddressHighConfidenceThreshold(addressHighConfidenceThreshold);
        w.setDateYearDecayRate(dateYearDecayRate);
        w.setDateDistantYearScore(dateDistantYearScore);
        w.setDateMonthTolerance1(dateMonthTolerance1);
        w.setDateMonthTolerance2(dateMonthTolerance2);
        w.setDateMonthTolerance3Plus(dateMonthTolerance3Plus);
        w.setDateDayTolerance0to3Start(dateDayTolerance0to3Start);
        w.setDateDayTolerance0to3Decay(dateDayTolerance0to3Decay);
        w.setDateDayTolerance4to7(dateDayTolerance4to7);
        w.setDateDayTolerance8Plus(dateDayTolerance8Plus);
        w.setDateYearWeight(dateYearWeight);
        w.setDateMonthWeight(dateMonthWeight);
        w.setDateDayWeight(dateDayWeight);
        w.setSupportingInfoMatchedThreshold(supportingInfoMatchedThreshold);
        w.setSupportingInfoExactThreshold(supportingInfoExactThreshold);
        w.setSupportingInfoSecondaryPenalty(supportingInfoSecondaryPenalty);
        w.setTitleMatchedThreshold(titleMatchedThreshold);
        w.setTitleExactThreshold(titleExactThreshold);
        w.setAffiliationNameThreshold(affiliationNameThreshold);
        w.setAffiliationExactThreshold(affiliationExactThreshold);
        w.setAffiliationTypeScoreThreshold(affiliationTypeScoreThreshold);
        w.setNameEarlyExitThreshold(nameEarlyExitThreshold);
        w.setScorerAddressCountryWeight(scorerAddressCountryWeight);
        w.setScorerAddressCityWeight(scorerAddressCityWeight);
        w.setScorerAddressLineWeight(scorerAddressLineWeight);
        w.setAliasSelectionTolerance(aliasSelectionTolerance);
        w.setAliasCoverageMinScore(aliasCoverageMinScore);

        a.setPhase1Threshold(phase1Threshold);
        a.setAddressMismatchThreshold(addressMismatchThreshold);
        a.setDobDifferenceThresholdYears(dobDifferenceThresholdYears);

        wh.setEnabled(webhookEnabled);
        wh.setRefreshNotificationUrl(webhookRefreshNotificationUrl == null ? "" : webhookRefreshNotificationUrl);
    }
}
