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
package org.xwiki.contrib.releasenotes.rest.internal;

import java.net.URI;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.contrib.releasenotes.rest.model.ErrorRepresentation;
import org.xwiki.model.ModelContext;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.rest.internal.Utils;
import org.xwiki.rest.resources.pages.PageResource;

/**
 * What the endpoints of the application share: running in the wiki the request names, pointing at the page that was
 * created, and refusing a request.
 *
 * @version $Id$
 * @since 2.7
 */
public abstract class AbstractReleaseNotesResource
{
    /**
     * What a client is told when the URL it called names no release note, which the URL of a change also always
     * does.
     */
    protected static final String NO_RELEASE_NOTE_IN_URL =
        "A change belongs to the release note of one version of one product, and the URL names neither.";

    /**
     * The name of the page an entry of a release note, and a release note itself, lives in: the space is what names
     * them, so that the changes of a release note are the pages under it.
     */
    private static final String HOME_PAGE = "WebHome";

    @Inject
    protected ModelContext modelContext;

    @Inject
    @Named("local")
    protected EntityReferenceSerializer<String> localEntityReferenceSerializer;

    /**
     * What an endpoint does once the wiki of the request is the current one.
     *
     * @param <T> what the endpoint answers with
     */
    @FunctionalInterface
    protected interface WikiOperation<T>
    {
        /**
         * @return what the endpoint answers with
         * @throws ReleaseNotesException when the endpoint failed
         */
        T call() throws ReleaseNotesException;
    }

    /**
     * Runs an endpoint with the wiki of the request as the current one, since that is how the components of the
     * application know which wiki to read and write: the release notes live in a fixed space of a wiki, and the wiki
     * is the one in the URL.
     *
     * @param wikiName the wiki of the request
     * @param operation what the endpoint does
     * @param <T> what the endpoint answers with
     * @return what the endpoint answered with
     * @throws ReleaseNotesException when the endpoint failed
     */
    protected <T> T inWiki(String wikiName, WikiOperation<T> operation) throws ReleaseNotesException
    {
        EntityReference previousReference = this.modelContext.getCurrentEntityReference();

        try {
            this.modelContext.setCurrentEntityReference(new WikiReference(wikiName));

            return operation.call();
        } finally {
            this.modelContext.setCurrentEntityReference(previousReference);
        }
    }

    /**
     * Points at a page that was created through the generic page resource of the wiki, which is where a client goes
     * on to read it, to change it or to delete it: this API creates release notes and changes, and leaves everything
     * else to that resource.
     *
     * @param uriInfo where the request was made to
     * @param reference the page that was created
     * @return the URI of that page in the REST API of the wiki
     */
    protected URI getPageUri(UriInfo uriInfo, DocumentReference reference)
    {
        return Utils.createURI(uriInfo.getBaseUri(), PageResource.class,
            reference.getWikiReference().getName(), Utils.getSpacesURLElements(reference), reference.getName());
    }

    /**
     * Gives the page one entry of a release note lives in. The name is built into a reference rather than resolved
     * from a serialized one, so a name carrying the characters a reference is written with names that page and
     * nothing else.
     *
     * @param noteReference the page of the release note the entry belongs to
     * @param entry the name of the entry, e.g. {@code Entry001}
     * @return the page of that entry
     */
    protected DocumentReference getEntryReference(DocumentReference noteReference, String entry)
    {
        return new DocumentReference(HOME_PAGE, new SpaceReference(entry, noteReference.getLastSpaceReference()));
    }

    /**
     * @param status why the request is refused
     * @param message what to tell whoever made it
     * @return that refusal
     */
    protected Response refuse(Response.Status status, String message)
    {
        return refuse(status, message, null);
    }

    /**
     * @param status why the request is refused
     * @param message what to tell whoever made it
     * @param reference the page the refusal is about, or {@code null} when it is about no page in particular
     * @return that refusal
     */
    protected Response refuse(Response.Status status, String message, DocumentReference reference)
    {
        String serializedReference =
            reference == null ? null : this.localEntityReferenceSerializer.serialize(reference);

        return Response.status(status).entity(new ErrorRepresentation(message, serializedReference)).build();
    }
}
