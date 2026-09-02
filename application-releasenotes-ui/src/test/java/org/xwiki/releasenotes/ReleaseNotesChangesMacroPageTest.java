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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.xwiki.localization.macro.internal.TranslationMacro;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.query.internal.ScriptQuery;
import org.xwiki.query.script.QueryManagerScriptService;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.wikimacro.internal.WikiMacroFactoryComponentClass;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.script.service.ScriptService;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.WikiMacroSetup;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
// The pages under test display their strings with the translation macro.
@ComponentList(TranslationMacro.class)
class ReleaseNotesChangesMacroPageTest extends PageTest
{
    private static final DocumentReference RELEASE_NOTES_CHANGES_MACRO =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code", "Change"), "ReleaseNotesChangesMacro");

    private static final DocumentReference GET_CHANGES_MACRO =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code", "Change"), "GetChangesMacro");

    private static final DocumentReference RELEASE_NOTE_CLASS =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "ReleaseNoteClass");

    private static final String PRODUCT = "XWiki";

    /** The clause selecting the changes that do have a screenshot. */
    private static final String HAS_SCREENSHOTS =
        "(changes.screenshots <> '' or (changes.screenshots is not null and '' is null))";

    private static final String WITH_SCREENSHOTS = "and " + HAS_SCREENSHOTS;

    private static final String WITHOUT_SCREENSHOTS = "and not " + HAS_SCREENSHOTS;

    /** No screenshot clause at all, i.e. the section displays a change whether it has a screenshot or not. */
    private static final String ANY_SCREENSHOTS = "";

    /** The three importance values, i.e. the section displays a change whatever its importance. */
    private static final List<String> ANY_IMPORTANCE = List.of("0", "1", "2");

    /**
     * The macro splits its changes over one section per audience, and each section is what a warning is about.
     */
    private static final int AUDIENCE_COUNT = 3;

    @Mock
    private ScriptQuery query;

    @Mock
    private QueryManagerScriptService queryManagerScriptService;

    /** The statement of each query the rendered release note built, in the order the sections built them. */
    private final List<String> statements = new ArrayList<>();

    /** The values bound to each of those queries, by parameter name. */
    private final List<Map<String, String>> bindings = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception
    {
        this.componentManager.registerComponent(ScriptService.class, "query", this.queryManagerScriptService);
        when(this.queryManagerScriptService.xwql(anyString())).thenAnswer(invocation -> {
            this.statements.add(invocation.getArgument(0));
            this.bindings.add(new LinkedHashMap<>());
            return this.query;
        });
        // A query is built and then bound before the next one is built, so a bound value belongs to the last
        // statement recorded above. That is what makes a filter attributable to the section that applied it.
        when(this.query.bindValue(anyString(), any())).thenAnswer(invocation -> {
            Object value = invocation.getArgument(1);
            this.bindings.get(this.bindings.size() - 1).put(invocation.getArgument(0), String.valueOf(value));
            return this.query;
        });

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
     * A release note gets the version it displays from the name of its page, and a final release note aggregates
     * the changes of the milestones and release candidates that led to it. A milestone or release candidate
     * displays only its own changes, under the version those changes were stored with.
     */
    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
        "8.3M1;  8.3-milestone-1",
        "8.3M12; 8.3-milestone-12",
        "8.3RC1; 8.3-rc-1",
        "8.3;    8.3, 8.3-milestone%, 8.3-rc%",
        "8.3.1;  8.3.1, 8.3.1-milestone%, 8.3.1-rc%"
    })
    void versionsQueriedByTheReleaseNote(String shortVersion, String expectedVersions) throws Exception
    {
        when(this.query.execute()).thenReturn(changes(0));

        renderReleaseNote(shortVersion, 100);

        assertEquals(List.of(expectedVersions.split("\\s*,\\s*")), boundValues(0, "version"));
    }

    /**
     * A release note has one section per audience, and each section splits its changes in two: the user and admin
     * sections lead with the changes illustrated by a screenshot and list the others under "Miscellaneous", while
     * the developer section, whose changes rarely have one, leads with the important ones instead. Every change of
     * the audience must fall in exactly one of the two queries, otherwise the release note silently loses it.
     */
    @Test
    void eachAudienceSectionSplitsItsChangesInTwo() throws Exception
    {
        when(this.query.execute()).thenReturn(changes(0));

        renderReleaseNote(100);

        assertEquals(2 * AUDIENCE_COUNT, this.statements.size(), "Expected two queries per audience section.");
        assertSectionQuery(0, "user", WITH_SCREENSHOTS, ANY_IMPORTANCE);
        assertSectionQuery(1, "user", WITHOUT_SCREENSHOTS, ANY_IMPORTANCE);
        assertSectionQuery(2, "administrator", WITH_SCREENSHOTS, ANY_IMPORTANCE);
        assertSectionQuery(3, "administrator", WITHOUT_SCREENSHOTS, ANY_IMPORTANCE);
        assertSectionQuery(4, "developer", ANY_SCREENSHOTS, List.of("1", "2"));
        assertSectionQuery(5, "developer", ANY_SCREENSHOTS, List.of("0"));
    }

    /**
     * The product is not part of the page name, it comes from the release note xobject, and every section must
     * query that product only.
     */
    @Test
    void productQueriedComesFromTheReleaseNoteObject() throws Exception
    {
        when(this.query.execute()).thenReturn(changes(0));

        renderReleaseNote(100);

        for (int index = 0; index < this.statements.size(); index++) {
            assertEquals(List.of(PRODUCT), boundValues(index, "product"));
        }
    }

    private void assertSectionQuery(int index, String expectedAudience, String expectedScreenshotClause,
        List<String> expectedImportance)
    {
        assertEquals(List.of(expectedAudience), boundValues(index, "audience"));
        assertEquals(expectedImportance, boundValues(index, "importance"));

        String statement = this.statements.get(index);
        if (ANY_SCREENSHOTS.equals(expectedScreenshotClause)) {
            assertFalse(statement.contains("changes.screenshots"),
                "Expected no screenshot filter, got: " + statement);
        } else {
            assertTrue(statement.contains(expectedScreenshotClause),
                String.format("Expected the \"%s\" filter, got: %s", expectedScreenshotClause, statement));
        }
    }

    /**
     * @param index the position of the query among the ones the rendered release note built
     * @param prefix the prefix shared by the names of the query parameters to return
     * @return the values bound to those parameters, in binding order
     */
    private List<String> boundValues(int index, String prefix)
    {
        return this.bindings.get(index).entrySet().stream()
            .filter(binding -> binding.getKey().startsWith(prefix))
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());
    }

    /**
     * Renders a release note whose body is the macro under test.
     *
     * @param limit the value of the {@code limit} macro parameter
     * @return the rendered release note
     */
    private Document renderReleaseNote(int limit) throws Exception
    {
        return renderReleaseNote("8.3", limit);
    }

    /**
     * Renders a release note whose body is the macro under test.
     *
     * @param shortVersion the name of the space holding the release note, which is where the macro reads the
     *            version it displays
     * @param limit the value of the {@code limit} macro parameter
     * @return the rendered release note
     */
    private Document renderReleaseNote(String shortVersion, int limit) throws Exception
    {
        loadPage(RELEASE_NOTE_CLASS);

        XWikiDocument releaseNote = new XWikiDocument(new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", PRODUCT, shortVersion), "WebHome"));
        releaseNote.setSyntax(Syntax.XWIKI_2_1);
        BaseObject releaseNoteObject = releaseNote.newXObject(RELEASE_NOTE_CLASS, this.context);
        releaseNoteObject.setStringValue("product", PRODUCT);
        releaseNoteObject.setStringValue("version", shortVersion);
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
