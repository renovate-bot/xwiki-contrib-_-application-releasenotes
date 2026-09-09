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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.xwiki.test.ui.TestUtils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Calls the endpoints of the application the way the clients they are for do: over HTTP, with JSON, as a user that
 * may write.
 * <p>
 * The test utilities of the platform send the representations of its own REST model as XML, which these endpoints
 * neither speak nor are called with, so the requests are made here instead.
 *
 * @version $Id$
 */
public class ReleaseNotesRestClient
{
    private final String baseURL;

    private final String authorization;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final ObjectMapper objectMapper = new ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /**
     * @param setup the test utilities holding the wiki the requests go to
     */
    public ReleaseNotesRestClient(TestUtils setup)
    {
        this.baseURL = setup.rest().getBaseURL();
        this.authorization = "Basic " + Base64.getEncoder().encodeToString(
            (TestUtils.SUPER_ADMIN_CREDENTIALS.getUserName() + ':'
                + TestUtils.SUPER_ADMIN_CREDENTIALS.getPassword()).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param path the endpoint to call, from the wiki of the URL onwards
     * @return what it answered
     * @throws Exception when the request could not be made
     */
    public JsonResponse get(String path) throws Exception
    {
        return send(request(path).GET());
    }

    /**
     * @param path the endpoint to call, from the wiki of the URL onwards
     * @param body what to post, which is written as JSON
     * @return what it answered
     * @throws Exception when the request could not be made
     */
    public JsonResponse post(String path, Object body) throws Exception
    {
        return send(request(path).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(this.objectMapper.writeValueAsString(body))));
    }

    private HttpRequest.Builder request(String path)
    {
        return HttpRequest.newBuilder(URI.create(this.baseURL + "/wikis/xwiki" + path))
            .header("Accept", "application/json").header("Authorization", this.authorization);
    }

    private JsonResponse send(HttpRequest.Builder request) throws Exception
    {
        HttpResponse<String> response =
            this.httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        return new JsonResponse(response, this.objectMapper);
    }
}
