package pl.beone.archivelink.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DateTimeUtil {

    public static String formatDate(Date date) {
        if (date == null) return "";
        return getDateFormat("yyyy-MM-dd").format(date);
    }

    public static String formatTime(Date date) {
        if (date == null) return "";
        return getDateFormat("HH:mm:ss").format(date);
    }

    public static String formatDateTime(Date date) {
        if (date == null) return "";
        return getDateFormat("yyyy-MM-dd'T'HH:mm:ssX").format(date);
    }

    private static SimpleDateFormat getDateFormat(String pattern) {
        var dateFormat = new SimpleDateFormat(pattern);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat;
    }

}
