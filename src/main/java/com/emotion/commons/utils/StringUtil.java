package com.emotion.commons.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StringUtil {
    private StringUtil() {
    }

    /**
     * split str to stream
     * "1, 2, 3, 4" => ["1", "2", "3", "4"]
     * @param str string to split
     * @param separatorChar separator to split
     * @return stream of split result strings
     */
    public Stream<String> splitToStream(String str, char separatorChar) {
        if(StringUtils.isBlank(str)) {
            return Stream.empty();
        }
        return Arrays.stream(StringUtils.split(str, separatorChar))
                .map(StringUtils::trimToNull)
                .filter(StringUtils::isNotBlank);
    }

    public List<String> splitToList(String str, char separatorChar) {
        return splitToStream(str, separatorChar)
                .collect(Collectors.toList());
    }

    public Set<String> splitToSet(String str, char separatorChar) {
        return splitToStream(str, separatorChar)
                .collect(Collectors.toSet());
    }

}
