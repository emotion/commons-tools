package com.emotion.commons.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class DateUtil {
    private DateUtil() {
    }

    public Date later(long curTime, long time, TimeUnit timeUnit) {
        return new Date(curTime + timeUnit.toMillis(time));
    }

    public Date later(Date date, long time, TimeUnit timeUnit) {
        return later(date.getTime(), time, timeUnit);
    }

    public Date later(long time, TimeUnit timeUnit) {
        return later(System.currentTimeMillis(), time, timeUnit);
    }

    public Date before(long curTime, long time, TimeUnit timeUnit) {
        return new Date(curTime-timeUnit.toMillis(time));
    }

    public Date before(Date date, long time, TimeUnit timeUnit) {
        return before(date.getTime(), time, timeUnit);
    }

    public Date before(long time, TimeUnit timeUnit) {
        return before(System.currentTimeMillis(), time, timeUnit);
    }

    public String format(Date date, String format) {
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(date);
    }

    public Date parse(String str, String format) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            return sdf.parse(str);
        } catch (ParseException e) {
            return null;
        }
    }

    public Date parse(String str, String... formats) {
        for(String format : formats) {
            Date date = parse(str, format);
            if(date != null) {
                return date;
            }
        }
        return null;
    }
}
