package io.moov.watchman.search;

/**
 * Represents a partial scoring result from one comparison function.
 * Corresponds to Go's search.ScorePiece struct.
 */
public class ScorePiece {
    private final String pieceType;
    private final double score;
    private final double weight;
    private final int fieldsCompared;
    private final boolean required;
    private final boolean matched;
    private final boolean exact;

    private ScorePiece(Builder builder) {
        this.pieceType = builder.pieceType;
        this.score = builder.score;
        this.weight = builder.weight;
        this.fieldsCompared = builder.fieldsCompared;
        this.required = builder.required;
        this.matched = builder.matched;
        this.exact = builder.exact;
    }

    public String getPieceType() {
        return pieceType;
    }

    public double getScore() {
        return score;
    }

    public double getWeight() {
        return weight;
    }

    public int getFieldsCompared() {
        return fieldsCompared;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isMatched() {
        return matched;
    }

    public boolean isExact() {
        return exact;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String pieceType = "";
        private double score = 0.0;
        private double weight = 0.0;
        private int fieldsCompared = 0;
        private boolean required = false;
        private boolean matched = false;
        private boolean exact = false;

        public Builder pieceType(String pieceType) {
            this.pieceType = pieceType;
            return this;
        }

        public Builder score(double score) {
            this.score = score;
            return this;
        }

        public Builder weight(double weight) {
            this.weight = weight;
            return this;
        }

        public Builder fieldsCompared(int fieldsCompared) {
            this.fieldsCompared = fieldsCompared;
            return this;
        }

        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        public Builder matched(boolean matched) {
            this.matched = matched;
            return this;
        }

        public Builder exact(boolean exact) {
            this.exact = exact;
            return this;
        }

        public ScorePiece build() {
            return new ScorePiece(this);
        }
    }
}
