package com.debugsw.monitor.api;

import jakarta.validation.constraints.*;
import java.time.Instant;

/** 接口请求参数。 */
public final class Requests {
    private Requests() { }

    /** 服务器登记信息。 */
    public record Server(@NotBlank @Size(max = 100) String name,
                         @NotBlank @Size(max = 255) String address,
                         @NotBlank @Size(max = 64) String environment,
                         @Size(max = 500) String tags) { }

    /** Agent 心跳信息。 */
    public record Heartbeat(@NotBlank @Size(max = 100) String version) { }

    /** 统一受管实例信息。 */
    public record Instance(@NotBlank @Size(max = 100) String name,
                           @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String type,
                           @Size(max = 500) String description) { }

    /** 主机指标，网络字节数为累计值。 */
    public record HostMetric(@NotNull Instant collectedAt,
                             @NotNull @DecimalMin("0.0") @DecimalMax("100.0") Double cpuPercent,
                             @NotNull @PositiveOrZero Long memoryTotalBytes,
                             @NotNull @PositiveOrZero Long memoryUsedBytes,
                             @NotNull @PositiveOrZero Long diskTotalBytes,
                             @NotNull @PositiveOrZero Long diskUsedBytes,
                             @NotNull @DecimalMin("0.0") Double loadOne,
                             @NotNull @PositiveOrZero Long networkRxBytes,
                             @NotNull @PositiveOrZero Long networkTxBytes) { }
}
