
package com.kpaatmik.reconciliation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reconciliation")
public class ReconciliationFileProperties {

    private final Input input = new Input();
    private final Output output = new Output();
    private int chunkSize = 500;

    public Input getInput() {
        return input;
    }

    public Output getOutput() {
        return output;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public static class Input {

        private String directory;
        private String baselineFile;
        private String sourceFile;

        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }

        public String getBaselineFile() {
            return baselineFile;
        }

        public void setBaselineFile(String baselineFile) {
            this.baselineFile = baselineFile;
        }

        public String getSourceFile() {
            return sourceFile;
        }

        public void setSourceFile(String sourceFile) {
            this.sourceFile = sourceFile;
        }
    }

    public static class Output {

        private String directory;

        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }
    }
}
