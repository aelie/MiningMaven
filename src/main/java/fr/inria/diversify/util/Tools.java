package fr.inria.diversify.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Created by aelie on 09/12/14.
 */
public class Tools {
    public static long increaseTimestamp(long timestamp, int field, int amount) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.add(field, amount);
        return cal.getTimeInMillis();
    }

    public static String timestampToStringDate(long timestamp, boolean formatShort) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        if (formatShort) {
            return "" + cal.get(Calendar.YEAR) +
                    (cal.get(Calendar.MONTH) + 1 <= 9 ? "0" + (cal.get(Calendar.MONTH) + 1) : (cal.get(Calendar.MONTH) + 1)) +
                    (cal.get(Calendar.DAY_OF_MONTH) <= 9 ? "0" + cal.get(Calendar.DAY_OF_MONTH) : cal.get(Calendar.DAY_OF_MONTH));
        } else {
            return (cal.get(Calendar.DAY_OF_MONTH) <= 9 ? "0" + cal.get(Calendar.DAY_OF_MONTH) : cal.get(Calendar.DAY_OF_MONTH)) + "-" +
                    (cal.get(Calendar.MONTH) + 1 <= 9 ? "0" + (cal.get(Calendar.MONTH) + 1) : (cal.get(Calendar.MONTH) + 1)) + "-" +
                    cal.get(Calendar.YEAR) + "T" +
                    (cal.get(Calendar.HOUR_OF_DAY) <= 9 ? "0" + cal.get(Calendar.HOUR_OF_DAY) : cal.get(Calendar.HOUR_OF_DAY)) + "h" +
                    (cal.get(Calendar.MINUTE) <= 9 ? "0" + cal.get(Calendar.MINUTE) : cal.get(Calendar.MINUTE)) + "m" +
                    (cal.get(Calendar.SECOND) <= 9 ? "0" + cal.get(Calendar.SECOND) : cal.get(Calendar.SECOND)) + "s";
        }
    }

    public static long stringDateToTimestamp(String date) {
        Calendar cal = Calendar.getInstance();
        if (date.length() == 8) {
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            cal.set(Calendar.YEAR, Integer.parseInt(date.substring(0, 4)));
            cal.set(Calendar.MONTH, Integer.parseInt(date.substring(4, 6)) - 1);
            cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(date.substring(6, 8)));
            return cal.getTimeInMillis();
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("dd'-'MM'-'yyyy'T'HH'h'mm'm'ss's'");
            try {
                cal.setTime(sdf.parse(date));
                return cal.getTimeInMillis();
            } catch (ParseException e) {
                Log.error("Failed to parse " + date);
                e.printStackTrace();
                return -1;
            }
        }
    }
}
