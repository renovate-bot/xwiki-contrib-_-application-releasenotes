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
package org.xwiki.contrib.releasenotes.rest;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.contrib.releasenotes.rest.model.ChangeRepresentation;
import org.xwiki.stability.Unstable;

/**
 * One change of one release note, the entry page it lives in being what names it: a change is created by a
 * {@code POST} to {@link ChangesResource}, which allocates that page and answers its name.
 *
 * @version $Id$
 * @since 2.8
 */
@Path("/wikis/{wikiName}/releasenotes/{product}/{version}/changes/{entry}")
@Unstable
public interface ChangeResource
{
    /**
     * Reads one change, which is the change a {@code PUT} to the same URL replaces.
     *
     * @param wikiName the wiki holding the release note
     * @param product the product the release note is about
     * @param version the version the release note is about, in its long form
     * @param entry the entry page the change lives in, e.g. {@code Entry001}
     * @return the change, or {@code 404} when that entry holds none
     * @throws ReleaseNotesException when the change could not be read
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    ChangeRepresentation getChange(@PathParam("wikiName") String wikiName, @PathParam("product") String product,
        @PathParam("version") String version, @PathParam("entry") String entry) throws ReleaseNotesException;

    /**
     * Replaces one change: the change becomes exactly what the request says, and a property the request leaves out
     * is emptied rather than kept. The change template has no say here, unlike when a change is created: a template
     * gives a new change the values its author has not written yet, and a replacement is written in full.
     * <p>
     * This is how a change gets its screenshots, which are the names of attachments of its own page and can
     * therefore only be named once that page exists:
     * </p>
     * <ol>
     * <li>{@code POST} the change to {@link ChangesResource}, which answers the entry page it allocated;</li>
     * <li>attach the images or the videos to that page, through the attachment resource of the wiki;</li>
     * <li>{@code PUT} the change back here with the {@code screenshots} it now has.</li>
     * </ol>
     * <p>
     * The product and the version are not replaced: they say which release note the change belongs to, which is the
     * page tree it lives in, so moving it there would be a move of its page rather than an update of its properties.
     * </p>
     *
     * @param wikiName the wiki holding the release note
     * @param product the product the release note is about
     * @param version the version the release note is about, in its long form
     * @param entry the entry page the change lives in, e.g. {@code Entry001}
     * @param change the change that entry is to hold, which needs at least a title
     * @return {@code 200} with the stored change, {@code 400} when the change is not usable, {@code 401} or
     *         {@code 403} when it may not be written, or {@code 404} when that entry holds no change
     * @throws ReleaseNotesException when the change could not be replaced
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response updateChange(@PathParam("wikiName") String wikiName, @PathParam("product") String product,
        @PathParam("version") String version, @PathParam("entry") String entry, ChangeRepresentation change)
        throws ReleaseNotesException;
}
