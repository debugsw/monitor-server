package com.debugsw.monitor.api;

import javax.validation.constraints.*;
import java.time.Instant;

/** 接口请求参数。 */
public final class Requests {
    private Requests() { }

    /** 服务器登记信息。 */
    public static class Server {
        @NotBlank @Size(max = 100)
        private String name;
        @NotBlank @Size(max = 255)
        private String address;
        @NotBlank @Size(max = 64)
        private String environment;
        @Size(max = 500)
        private String tags;

        /** 获取name。 */
        public String getName() { return name; }
        /** 设置name。 */
        public void setName(String value) { this.name = value; }
        /** 返回name。 */
        public String name() { return name; }

        /** 获取address。 */
        public String getAddress() { return address; }
        /** 设置address。 */
        public void setAddress(String value) { this.address = value; }
        /** 返回address。 */
        public String address() { return address; }

        /** 获取environment。 */
        public String getEnvironment() { return environment; }
        /** 设置environment。 */
        public void setEnvironment(String value) { this.environment = value; }
        /** 返回environment。 */
        public String environment() { return environment; }

        /** 获取tags。 */
        public String getTags() { return tags; }
        /** 设置tags。 */
        public void setTags(String value) { this.tags = value; }
        /** 返回tags。 */
        public String tags() { return tags; }
    }

    /** Agent 心跳信息。 */
    public static class Heartbeat {
        @NotBlank @Size(max = 100)
        private String version;

        /** 获取version。 */
        public String getVersion() { return version; }
        /** 设置version。 */
        public void setVersion(String value) { this.version = value; }
        /** 返回version。 */
        public String version() { return version; }
    }

    /** 受管实例信息。 */
    public static class Instance {
        @NotBlank @Size(max = 100)
        private String name;
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
        private String type;
        @Size(max = 500)
        private String description;

        /** 获取name。 */
        public String getName() { return name; }
        /** 设置name。 */
        public void setName(String value) { this.name = value; }
        /** 返回name。 */
        public String name() { return name; }

        /** 获取type。 */
        public String getType() { return type; }
        /** 设置type。 */
        public void setType(String value) { this.type = value; }
        /** 返回type。 */
        public String type() { return type; }

        /** 获取description。 */
        public String getDescription() { return description; }
        /** 设置description。 */
        public void setDescription(String value) { this.description = value; }
        /** 返回description。 */
        public String description() { return description; }
    }

    /** 主机指标。 */
    public static class HostMetric {
        @NotNull
        private Instant collectedAt;
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0")
        private Double cpuPercent;
        @NotNull @PositiveOrZero
        private Long memoryTotalBytes;
        @NotNull @PositiveOrZero
        private Long memoryUsedBytes;
        @NotNull @PositiveOrZero
        private Long diskTotalBytes;
        @NotNull @PositiveOrZero
        private Long diskUsedBytes;
        @NotNull @DecimalMin("0.0")
        private Double loadOne;
        @NotNull @PositiveOrZero
        private Long networkRxBytes;
        @NotNull @PositiveOrZero
        private Long networkTxBytes;

        /** 获取collectedAt。 */
        public Instant getCollectedAt() { return collectedAt; }
        /** 设置collectedAt。 */
        public void setCollectedAt(Instant value) { this.collectedAt = value; }
        /** 返回collectedAt。 */
        public Instant collectedAt() { return collectedAt; }

        /** 获取cpuPercent。 */
        public Double getCpuPercent() { return cpuPercent; }
        /** 设置cpuPercent。 */
        public void setCpuPercent(Double value) { this.cpuPercent = value; }
        /** 返回cpuPercent。 */
        public Double cpuPercent() { return cpuPercent; }

        /** 获取memoryTotalBytes。 */
        public Long getMemoryTotalBytes() { return memoryTotalBytes; }
        /** 设置memoryTotalBytes。 */
        public void setMemoryTotalBytes(Long value) { this.memoryTotalBytes = value; }
        /** 返回memoryTotalBytes。 */
        public Long memoryTotalBytes() { return memoryTotalBytes; }

        /** 获取memoryUsedBytes。 */
        public Long getMemoryUsedBytes() { return memoryUsedBytes; }
        /** 设置memoryUsedBytes。 */
        public void setMemoryUsedBytes(Long value) { this.memoryUsedBytes = value; }
        /** 返回memoryUsedBytes。 */
        public Long memoryUsedBytes() { return memoryUsedBytes; }

        /** 获取diskTotalBytes。 */
        public Long getDiskTotalBytes() { return diskTotalBytes; }
        /** 设置diskTotalBytes。 */
        public void setDiskTotalBytes(Long value) { this.diskTotalBytes = value; }
        /** 返回diskTotalBytes。 */
        public Long diskTotalBytes() { return diskTotalBytes; }

        /** 获取diskUsedBytes。 */
        public Long getDiskUsedBytes() { return diskUsedBytes; }
        /** 设置diskUsedBytes。 */
        public void setDiskUsedBytes(Long value) { this.diskUsedBytes = value; }
        /** 返回diskUsedBytes。 */
        public Long diskUsedBytes() { return diskUsedBytes; }

        /** 获取loadOne。 */
        public Double getLoadOne() { return loadOne; }
        /** 设置loadOne。 */
        public void setLoadOne(Double value) { this.loadOne = value; }
        /** 返回loadOne。 */
        public Double loadOne() { return loadOne; }

        /** 获取networkRxBytes。 */
        public Long getNetworkRxBytes() { return networkRxBytes; }
        /** 设置networkRxBytes。 */
        public void setNetworkRxBytes(Long value) { this.networkRxBytes = value; }
        /** 返回networkRxBytes。 */
        public Long networkRxBytes() { return networkRxBytes; }

        /** 获取networkTxBytes。 */
        public Long getNetworkTxBytes() { return networkTxBytes; }
        /** 设置networkTxBytes。 */
        public void setNetworkTxBytes(Long value) { this.networkTxBytes = value; }
        /** 返回networkTxBytes。 */
        public Long networkTxBytes() { return networkTxBytes; }
    }
}
