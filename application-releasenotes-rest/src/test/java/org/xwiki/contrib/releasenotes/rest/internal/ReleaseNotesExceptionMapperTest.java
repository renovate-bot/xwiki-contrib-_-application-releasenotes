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

import java.util.List;

import jakarta.inject.Provider;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.contrib.releasenotes.ReleaseNoteAlreadyExistsException;
import org.xwiki.contrib.releasenotes.ReleaseNotesAccessDeniedException;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.contrib.releasenotes.rest.model.ErrorRepresentation;
import org.xwiki.model.internal.reference.DefaultSymbolScheme;
import org.xwiki.model.internal.reference.LocalStringEntityReferenceSerializer;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWikiContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReleaseNotesExceptionMapper}.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList({ LocalStringEntityReferenceSerializer.class, DefaultSymbolScheme.class })
class ReleaseNotesExceptionMapperTest
{
    private static final DocumentReference RELEASE_NOTE = new DocumentReference("xwiki",
        List.of("ReleaseNotes", "Data", "XWiki", "8.3"), "WebHome");

    @InjectMockComponents
    private ReleaseNotesExceptionMapper mapper;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.ERROR);

    private XWikiContext xcontext;

    @BeforeEach
    void setUp()
    {
        this.xcontext = mock(XWikiContext.class);
        when(this.xcontextProvider.get()).thenReturn(this.xcontext);
        when(this.xcontext.getUserReference()).thenReturn(new DocumentReference("xwiki", "XWiki", "SomeUser"));
    }

    /**
     * A release note that already exists is answered with the page it lives in, so that a client that is re-running
     * knows at its first call, and not at its two hundredth change, that it has run before.
     */
    @Test
    void aReleaseNoteThatAlreadyExistsIsAConflictNamingIt()
    {
        Response response = this.mapper.toResponse(new ReleaseNoteAlreadyExistsException(RELEASE_NOTE));

        assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        assertEquals("ReleaseNotes.Data.XWiki.8\\.3.WebHome", error(response).getReference());
        assertEquals("The release note [xwiki:ReleaseNotes.Data.XWiki.8\\.3.WebHome] already exists.",
            error(response).getMessage());
    }

    @Test
    void aPageThatMayNotBeEditedIsForbidden()
    {
        Response response =
            this.mapper.toResponse(new ReleaseNotesAccessDeniedException("Not allowed.", RELEASE_NOTE));

        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
        assertEquals("Not allowed.", error(response).getMessage());
        assertEquals("ReleaseNotes.Data.XWiki.8\\.3.WebHome", error(response).getReference());
    }

    /**
     * A caller that has not said who it is is asked to, the way the generic resources of the wiki answer a write it
     * may not do: it may well be allowed once it has.
     */
    @Test
    void aRequestThatSaysWhoNobodyIsMakingItIsAskedToAuthenticate()
    {
        when(this.xcontext.getUserReference()).thenReturn(null);

        Response response =
            this.mapper.toResponse(new ReleaseNotesAccessDeniedException("Not allowed.", RELEASE_NOTE));

        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    }

    /**
     * Everything else is a failure of the wiki rather than something the client did, so it is a server error, and it
     * is logged: the client is told what happened, and the administrator can find out why.
     */
    @Test
    void anythingElseIsAServerErrorAndIsLogged()
    {
        Response response = this.mapper.toResponse(new ReleaseNotesException("The store would not answer."));

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertEquals("The store would not answer.", error(response).getMessage());
        assertNull(error(response).getReference());
        assertEquals("Failed to serve a release notes request.", this.logCapture.getMessage(0));
    }

    private static ErrorRepresentation error(Response response)
    {
        return (ErrorRepresentation) response.getEntity();
    }
}
