package com.example.util;

import java.time.LocalDateTime;

public class DateUtil {
    public static boolean checkOverlappingDatePeriod(LocalDateTime startDatePeriodA, LocalDateTime endDatePeriodA,
                                                     LocalDateTime startDatePeriodB, LocalDateTime endDatePeriodB){
        LocalDateTime maxStartDate = (startDatePeriodA.isAfter(startDatePeriodB)) ? startDatePeriodA : startDatePeriodB;
        LocalDateTime minEndDate = (endDatePeriodA.isBefore(endDatePeriodB)) ? endDatePeriodA : endDatePeriodB;
        return maxStartDate.isBefore(minEndDate) || maxStartDate.isEqual(minEndDate);
    }
}
