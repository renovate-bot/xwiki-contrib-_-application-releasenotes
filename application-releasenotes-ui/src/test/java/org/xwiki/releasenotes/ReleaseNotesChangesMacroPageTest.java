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
package org.xwiki.releasenotes;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.query.internal.ScriptQuery;
import org.xwiki.query.script.QueryManagerScriptService;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.wikimacro.internal.WikiMacroFactoryComponentClass;
import org.xwiki.script.service.ScriptService;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.WikiMacroSetup;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Page test for the {@code releasenotechanges} wiki macro, defined in
 * {@code ReleaseNotes.Code.Change.ReleaseNotesChangesMacro}.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
@WikiMacroFactoryComponentClass
class ReleaseNotesChangesMacroPageTest extends PageTest
{
    private static final DocumentReference RELEASE_NOTES_CHANGES_MACRO =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code", "Change"), "ReleaseNotesChangesMacro");

    private static final DocumentReference GET_CHANGES_MACRO =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code", "Change"), "GetChangesMacro");

    private static final DocumentReference RELEASE_NOTE_CLASS =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "ReleaseNoteClass");

    private static final DocumentReference RELEASE_NOTE =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "XWiki", "8.3"), "WebHome");

    /**
     * The macro splits its changes over one section per audience, and each section is what a warning is about.
     */
    private static final int AUDIENCE_COUNT = 3;

    @Mock
    private ScriptQuery query;

    @Mock
    private QueryManagerScriptService queryManagerScriptService;

    @BeforeEach
    void setUp() throws Exception
    {
        this.componentManager.registerComponent(ScriptService.class, "query", this.queryManagerScriptService);
        when(this.queryManagerScriptService.xwql(anyString())).thenReturn(this.query);
        when(this.query.bindValue(anyString(), any())).thenReturn(this.query);

        WikiMacroSetup.loadWikiMacro(this, this.componentManager, GET_CHANGES_MACRO);
        WikiMacroSetup.loadWikiMacro(this, this.componentManager, RELEASE_NOTES_CHANGES_MACRO);
    }

    /**
     * A release note that displays only a page of its changes looks complete while it is not, so it has to say that
     * some of its changes are missing: this warning is the only sign the author gets that the limit needs raising.
     */
    @Test
    void sectionLeavingChangesOutIsReported() throws Exception
    {
        // One change more than the sections are allowed to display.
        when(this.query.execute()).thenReturn(changes(3));

        assertEquals(AUDIENCE_COUNT, renderReleaseNote(2).select("div.warningmessage").size(),
            "Each section of the release note left a change out, so each of them must report it.");
    }

    @Test
    void sectionDisplayingAllItsChangesIsNotReported() throws Exception
    {
        when(this.query.execute()).thenReturn(changes(2));

        assertEquals(0, renderReleaseNote(2).select("div.warningmessage").size(),
            "No change was left out, so the release note must not claim otherwise.");
    }

    /**
     * The limit exists to keep a release note from loading every change of the product it aggregates, so it has to
     * bound each of the queries the macro runs, not just the first one.
     */
    @Test
    void limitBoundsEveryQueryOfTheReleaseNote() throws Exception
    {
        when(this.query.execute()).thenReturn(changes(2));

        renderReleaseNote(50);

        // Two queries per audience section, each asking for one row beyond the limit.
        verify(this.query, times(2 * AUDIENCE_COUNT)).setLimit(51);
    }

    /**
     * Renders a release note whose body is the macro under test.
     *
     * @param limit the value of the {@code limit} macro parameter
     * @return the rendered release note
     */
    private Document renderReleaseNote(int limit) throws Exception
    {
        loadPage(RELEASE_NOTE_CLASS);

        XWikiDocument releaseNote = new XWikiDocument(RELEASE_NOTE);
        releaseNote.setSyntax(Syntax.XWIKI_2_1);
        BaseObject releaseNoteObject = releaseNote.newXObject(RELEASE_NOTE_CLASS, this.context);
        releaseNoteObject.setStringValue("product", "XWiki");
        releaseNoteObject.setStringValue("version", "8.3");
        releaseNote.setContent(String.format("{{releasenotechanges limit=\"%s\"/}}", limit));
        this.xwiki.saveDocument(releaseNote, this.context);
        // The macro reads the version off the page it is on, so that page has to be the one in the context.
        this.context.setDoc(releaseNote);

        return renderHTMLPage(releaseNote);
    }

    /**
     * @param count the number of rows the query must return
     * @return as many change document names as asked for, which is all the macro needs from the query in order to
     *         count them
     */
    private List<Object> changes(int count)
    {
        List<Object> changes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            changes.add(String.format("ReleaseNotes.Data.XWiki.8.3.Entry%03d.WebHome", i + 1));
        }
        return changes;
    }
}
