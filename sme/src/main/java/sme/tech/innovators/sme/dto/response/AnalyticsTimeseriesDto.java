package sme.tech.innovators.sme.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsTimeseriesDto {

    @Builder.Default
    private List<Point> points = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Point {
        /** ISO date yyyy-MM-dd */
        private String date;
        private BigDecimal revenue;
        private long orders;
    }
}
