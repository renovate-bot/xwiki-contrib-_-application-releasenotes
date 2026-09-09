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

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.releasenotes.ReleaseNoteAlreadyExistsException;
import org.xwiki.contrib.releasenotes.ReleaseNotesAccessDeniedException;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.contrib.releasenotes.rest.model.ErrorRepresentation;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rest.XWikiRestComponent;

import com.xpn.xwiki.XWikiContext;

/**
 * Answers the failures of the application with the status code they mean, so that the endpoints themselves are about
 * what they do and there is one place saying what a failure looks like on the wire.
 *
 * @version $Id$
 * @since 2.7
 */
@Component
@Named("org.xwiki.contrib.releasenotes.rest.internal.ReleaseNotesExceptionMapper")
@Provider
@Singleton
public class ReleaseNotesExceptionMapper implements ExceptionMapper<ReleaseNotesException>, XWikiRestComponent
{
    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> localEntityReferenceSerializer;

    // Written out in full because the name Provider is taken here by the JAX-RS annotation of this class.
    @Inject
    private jakarta.inject.Provider<XWikiContext> xcontextProvider;

    @Inject
    private Logger logger;

    @Override
    public Response toResponse(ReleaseNotesException exception)
    {
        Response.Status status;
        DocumentReference reference = null;

        if (exception instanceof ReleaseNoteAlreadyExistsException alreadyExists) {
            // The page of the release note that already exists is answered with it, so that a client that is
            // re-running knows at its first call, and not at its two hundredth change, that it has run before.
            status = Response.Status.CONFLICT;
            reference = alreadyExists.getReleaseNoteReference();
        } else if (exception instanceof ReleaseNotesAccessDeniedException accessDenied) {
            // A caller that has not said who it is is asked to, the way the generic resources of the wiki answer a
            // write it may not do; a caller that has, and still may not write, is refused.
            status = isGuest() ? Response.Status.UNAUTHORIZED : Response.Status.FORBIDDEN;
            reference = accessDenied.getReference();
        } else {
            // Everything else is a failure of the wiki rather than something the client did: a store that would not
            // answer, a query that would not run, a listener that cancelled the save.
            status = Response.Status.INTERNAL_SERVER_ERROR;
            this.logger.error("Failed to serve a release notes request.", exception);
        }

        String serializedReference =
            reference == null ? null : this.localEntityReferenceSerializer.serialize(reference);

        return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE)
            .entity(new ErrorRepresentation(exception.getMessage(), serializedReference)).build();
    }

    /**
     * @return whether the request was made without saying who is making it, which the context holds no user for
     */
    private boolean isGuest()
    {
        XWikiContext xcontext = this.xcontextProvider.get();

        return xcontext == null || xcontext.getUserReference() == null;
    }
}
