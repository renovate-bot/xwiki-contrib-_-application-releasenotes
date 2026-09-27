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
package org.xwiki.contrib.releasenotes.script;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xwiki.contrib.releasenotes.Change;
import org.xwiki.contrib.releasenotes.ChangeManager;
import org.xwiki.contrib.releasenotes.ChangeQuery;
import org.xwiki.contrib.releasenotes.ChangeQueryParser;
import org.xwiki.contrib.releasenotes.ChangeSearchResult;
import org.xwiki.contrib.releasenotes.ReleaseNote;
import org.xwiki.contrib.releasenotes.ReleaseNoteManager;
import org.xwiki.contrib.releasenotes.ReleaseNotesAccessDeniedException;
import org.xwiki.contrib.releasenotes.ReleaseNotesConfiguration;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReleaseNotesScriptService}, which is the one layer that must add nothing of its own: a rule
 * it enforced here would be a rule a REST or a Java caller escapes, since the components below are what all three
 * go through.
 *
 * @version $Id$
 */
@ComponentTest
class ReleaseNotesScriptServiceTest
{
    private static final DocumentReference RELEASE_NOTE = new DocumentReference("xwiki",
        List.of("ReleaseNotes", "Data", "XWiki", "8.3"), "WebHome");

    private static final DocumentReference ENTRY = new DocumentReference("xwiki",
        List.of("ReleaseNotes", "Data", "XWiki", "8.3", "Entry001"), "WebHome");

    @InjectMockComponents
    private ReleaseNotesScriptService service;

    @MockComponent
    private ReleaseNoteManager releaseNoteManager;

    @MockComponent
    private ChangeManager changeManager;

    @MockComponent
    private ChangeQueryParser changeQueryParser;

    @MockComponent
    private ReleaseNotesConfiguration configuration;

    @MockComponent
    private ContextualAuthorizationManager authorization;

    @BeforeEach
    void setUp()
    {
        when(this.authorization.hasAccess(eq(Right.VIEW), any())).thenReturn(true);
    }

    @Test
    void theReleaseNotesAreHandedToTheReleaseNoteManager() throws Exception
    {
        ReleaseNote note = new ReleaseNote();
        List<ReleaseNote> notes = List.of(note);
        when(this.releaseNoteManager.createReleaseNote(note)).thenReturn(RELEASE_NOTE);
        when(this.releaseNoteManager.getReleaseNoteReference("XWiki", "8.3")).thenReturn(RELEASE_NOTE);
        when(this.releaseNoteManager.getReleaseNote(RELEASE_NOTE)).thenReturn(note);
        when(this.releaseNoteManager.getReleaseNotes(eq("XWiki"), any())).thenReturn(notes);
        when(this.releaseNoteManager.getAggregatedVersions(RELEASE_NOTE)).thenReturn(List.of("8.3"));
        when(this.releaseNoteManager.updateReleaseNote(note)).thenReturn(note);

        assertEquals(RELEASE_NOTE, this.service.createReleaseNote(note));
        assertSame(note, this.service.updateReleaseNote(note));
        assertEquals(RELEASE_NOTE, this.service.getReleaseNoteReference("XWiki", "8.3"));
        assertSame(note, this.service.getReleaseNote(RELEASE_NOTE));
        assertSame(notes, this.service.getReleaseNotes("XWiki"));
        assertEquals(List.of("8.3"), this.service.getAggregatedVersions(RELEASE_NOTE));
    }

    @Test
    void theChangesAreHandedToTheChangeManager() throws Exception
    {
        Change change = new Change();
        when(this.changeManager.createChange(change)).thenReturn(ENTRY);
        when(this.changeManager.reserveNextEntry("XWiki", "8.3")).thenReturn(ENTRY);
        when(this.changeManager.getChange(ENTRY)).thenReturn(change);
        when(this.changeManager.updateChange(ENTRY, change)).thenReturn(change);

        assertEquals(ENTRY, this.service.createChange(change));
        assertSame(change, this.service.updateChange(ENTRY, change));
        assertEquals(ENTRY, this.service.reserveNextEntry("XWiki", "8.3"));
        assertSame(change, this.service.getChange(ENTRY));
    }

    @Test
    void theSearchesAreHandedToTheParserAndToTheChangeManager() throws Exception
    {
        Map<String, String> parameters = Map.of("versions", "8.3");
        ChangeQuery query = new ChangeQuery();
        ChangeSearchResult result = new ChangeSearchResult(List.of(), List.of(), false);
        when(this.changeQueryParser.parse(parameters)).thenReturn(query);
        when(this.changeManager.search(eq(query), any())).thenReturn(result);

        assertSame(query, this.service.parseQuery(parameters));
        assertSame(result, this.service.search(query));
    }

    /**
     * The components of the application read a page whoever asks for it, so the script service is where the view
     * right of a script call is checked.
     */
    @Test
    void aReleaseNoteTheCurrentUserCannotViewIsNotRead() throws Exception
    {
        when(this.authorization.hasAccess(Right.VIEW, RELEASE_NOTE)).thenReturn(false);

        ReleaseNotesAccessDeniedException exception =
            assertThrows(ReleaseNotesAccessDeniedException.class, () -> this.service.getReleaseNote(RELEASE_NOTE));

        assertEquals(RELEASE_NOTE, exception.getReference());
        verify(this.releaseNoteManager, never()).getReleaseNote(any());
    }

    @Test
    void aChangeTheCurrentUserCannotViewIsNotRead() throws Exception
    {
        when(this.authorization.hasAccess(Right.VIEW, ENTRY)).thenReturn(false);

        ReleaseNotesAccessDeniedException exception =
            assertThrows(ReleaseNotesAccessDeniedException.class, () -> this.service.getChange(ENTRY));

        assertEquals("The current user is not allowed to view the page "
            + "[xwiki:ReleaseNotes.Data.XWiki.8\\.3.Entry001.WebHome].", exception.getMessage());
        verify(this.changeManager, never()).getChange(any());
    }

    /**
     * The release notes and the changes are listed with a filter that accepts the pages the current user can view
     * and refuses the others, which the managers apply before cutting the result into pages.
     */
    @Test
    void theListingsLeaveOutWhatTheCurrentUserCannotView() throws Exception
    {
        when(this.authorization.hasAccess(Right.VIEW, ENTRY)).thenReturn(false);

        this.service.getReleaseNotes("XWiki");
        this.service.search(new ChangeQuery());

        ArgumentCaptor<Predicate<DocumentReference>> noteFilter = ArgumentCaptor.captor();
        verify(this.releaseNoteManager).getReleaseNotes(eq("XWiki"), noteFilter.capture());
        ArgumentCaptor<Predicate<DocumentReference>> changeFilter = ArgumentCaptor.captor();
        verify(this.changeManager).search(any(), changeFilter.capture());

        for (Predicate<DocumentReference> filter : List.of(noteFilter.getValue(), changeFilter.getValue())) {
            assertTrue(filter.test(RELEASE_NOTE));
            assertFalse(filter.test(ENTRY));
        }
    }

    @Test
    void theDefaultsAreReadFromTheConfiguration()
    {
        when(this.configuration.getDefaultProduct()).thenReturn("XWiki");
        when(this.configuration.getDefaultTemplate()).thenReturn(RELEASE_NOTE);

        assertEquals("XWiki", this.service.getDefaultProduct());
        assertEquals(RELEASE_NOTE, this.service.getDefaultTemplate());
    }
}
