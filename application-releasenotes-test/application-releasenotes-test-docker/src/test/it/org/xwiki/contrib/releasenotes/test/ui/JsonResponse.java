/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.releasenotes.test.ui;

import java.net.http.HttpResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * What an endpoint of the application answered.
 *
 * @version $Id$
 */
public class JsonResponse
{
    private final HttpResponse<String> response;

    private final ObjectMapper objectMapper;

    /**
     * @param response the answer as it came back
     * @param objectMapper what reads its body
     */
    JsonResponse(HttpResponse<String> response, ObjectMapper objectMapper)
    {
        this.response = response;
        this.objectMapper = objectMapper;
    }

    /**
     * @return the status code of the answer
     */
    public int getStatus()
    {
        return this.response.statusCode();
    }

    /**
     * @return the body of the answer, as it was sent
     */
    public String getBody()
    {
        return this.response.body();
    }

    /**
     * @return where the answer points at what was created, or {@code null} when it points at nothing
     */
    public String getLocation()
    {
        return this.response.headers().firstValue("Location").orElse(null);
    }

    /**
     * @param type what the body holds
     * @param <T> what the body holds
     * @return the body, read as that
     * @throws Exception when the body is not that
     */
    public <T> T as(Class<T> type) throws Exception
    {
        return this.objectMapper.readValue(getBody(), type);
    }
}
