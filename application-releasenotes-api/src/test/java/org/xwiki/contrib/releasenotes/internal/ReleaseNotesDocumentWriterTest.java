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
package org.xwiki.contrib.releasenotes.internal;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.model.document.DocumentAuthors;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.user.GuestUserReference;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReleaseNotesDocumentWriter}.
 *
 * @version $Id$
 */
@OldcoreTest
@ReferenceComponentList
class ReleaseNotesDocumentWriterTest
{
    private static final DocumentReference RELEASE_NOTE = new DocumentReference("xwiki",
        List.of("ReleaseNotes", "Data", "XWiki", "8.3"), "WebHome");

    private static final DocumentReference AUTHOR = new DocumentReference("xwiki", "XWiki", "Author");

    private static final DocumentReference USER = new DocumentReference("xwiki", "XWiki", "User");

    @InjectMockComponents
    private ReleaseNotesDocumentWriter writer;

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    private DocumentAccessBridge documentAccessBridge;

    @BeforeEach
    void setUp() throws Exception
    {
        this.documentAccessBridge = this.oldcore.getMocker().getInstance(DocumentAccessBridge.class);
        when(this.documentAccessBridge.getCurrentAuthorReference()).thenReturn(AUTHOR);
        this.oldcore.getXWikiContext().setUserReference(USER);
    }

    /**
     * The right of the user who asks is checked, and not only what the wiki lets the application itself do:
     * {@code XWiki#saveDocument} checks nothing, so a component reachable through a script service would otherwise
     * be a way of writing to the wiki with no right at all.
     */
    @Test
    void aUserWhoCannotEditThePageIsRefused()
    {
        allowAuthor();

        ReleaseNotesException exception =
            assertThrows(ReleaseNotesException.class, () -> this.writer.checkEditRight(RELEASE_NOTE));

        assertEquals("The current user is not allowed to edit the page [xwiki:ReleaseNotes.Data.XWiki.8\\.3.WebHome].",
            exception.getMessage());
    }

    /**
     * The right of the author of the calling script is checked too: a script service is reachable with the script
     * right alone, so a check done for the user only would let a script write on behalf of whoever happens to be
     * reading the page it sits on.
     */
    @Test
    void aScriptAuthorWhoCannotEditThePageIsRefused()
    {
        allowUser();

        ReleaseNotesException exception =
            assertThrows(ReleaseNotesException.class, () -> this.writer.checkEditRight(RELEASE_NOTE));

        assertEquals("The author [xwiki:XWiki.Author] of the calling script is not allowed to edit the page "
            + "[xwiki:ReleaseNotes.Data.XWiki.8\\.3.WebHome].", exception.getMessage());
    }

    @Test
    void aPageIsNotSavedWhenItMayNotBeWritten() throws Exception
    {
        allowAuthor();

        assertThrows(ReleaseNotesException.class, () -> this.writer.save(page(), "New Release note"));

        assertTrue(load().isNew(), "A page that may not be written must not have been saved.");
    }

    /**
     * The author and the content author are set here rather than left to the save, which sets neither: the content
     * of a release note is copied from a template that scripts, and the content author is who that script runs as.
     */
    @Test
    void savingAPageRecordsTheCurrentUserAsItsAuthorAndAsItsContentAuthor() throws Exception
    {
        allowUser();
        allowAuthor();

        this.writer.save(page(), "New Release note");

        XWikiDocument saved = load();
        assertFalse(saved.isNew(), "Expected the page to have been saved.");
        DocumentAuthors authors = saved.getAuthors();
        assertNotNull(authors.getEffectiveMetadataAuthor());
        assertNotSame(GuestUserReference.INSTANCE, authors.getEffectiveMetadataAuthor(),
            "A page whose content author is nobody has content that cannot execute.");
        assertEquals(authors.getEffectiveMetadataAuthor(), authors.getContentAuthor());
        assertEquals(authors.getEffectiveMetadataAuthor(), authors.getCreator());
        assertEquals("New Release note", saved.getComment());
    }

    private void allowUser()
    {
        when(this.oldcore.getMockContextualAuthorizationManager().hasAccess(Right.EDIT, RELEASE_NOTE))
            .thenReturn(true);
    }

    private void allowAuthor()
    {
        when(this.oldcore.getMockAuthorizationManager().hasAccess(eq(Right.EDIT), eq(AUTHOR), any()))
            .thenReturn(true);
    }

    private XWikiDocument page() throws Exception
    {
        return load();
    }

    private XWikiDocument load() throws Exception
    {
        return this.oldcore.getSpyXWiki().getDocument(RELEASE_NOTE, this.oldcore.getXWikiContext());
    }
}
