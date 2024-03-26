/*
 * Copyright (C) 2022 by the geOrchestra PSC
 *
 * This file is part of geOrchestra.
 *
 * geOrchestra is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * geOrchestra is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.georchestra.gateway.filter.global;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Configuration to set white/black list over the request URL to determine if
 * the access log filter will log an entry for it.
 */
@Data
@ConfigurationProperties(prefix = "georchestra.gateway.accesslog")
public class AccessLogFilterConfig {

    /**
     * Enable/disable the access log filter
     */
    private boolean enabled = true;

    /**
     * A list of java regular expressions applied to the request URL to include them
     * from logging.
     */
    List<Pattern> include = new ArrayList<>();

    /**
     * A list of java regular expressions applied to the request URL to exclude them
     * from logging. A request URL must pass all the include filters before being
     * tested for exclusion. Useful to avoid flooding the logs with frequent
     * non-important requests such as static resources (i.e. static images, etc).
     */
    List<Pattern> exclude = new ArrayList<>();

    /**
     * @param uri the origin URL (e.g. https://my.domain.com/geoserver/web/)
     * @return {@code true} if disabled or an access log entry shall be logged for
     *         this request
     */
    public boolean matches(URI uri) {
        if (!enabled || (include.isEmpty() && exclude.isEmpty()))
            return true;

        String url = uri.toString();
        return matches(url, include, true) && !matches(url, exclude, false);
    }

    private boolean matches(String url, List<Pattern> patterns, boolean fallbackIfEmpty) {
        return (patterns == null || patterns.isEmpty()) ? fallbackIfEmpty
                : patterns.stream().anyMatch(pattern -> pattern.matcher(url).matches());
    }
}
