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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.xwiki.extension.script.ExtensionManagerScriptService;
import org.xwiki.extension.version.internal.DefaultVersion;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.query.QueryException;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Page test for the {@code getChanges} wiki macro, defined in {@code ReleaseNotes.Code.Change.GetChangesMacro}.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
@WikiMacroFactoryComponentClass
class GetChangesMacroPageTest extends PageTest
{
    private static final DocumentReference GET_CHANGES_MACRO =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code", "Change"), "GetChangesMacro");

    private static final DocumentReference TEST_PAGE =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Data"), "TestPage");

    private static final DocumentReference RELEASE_NOTE_CLASS =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "ReleaseNoteClass");

    /**
     * The versions the wiki holds a release note for. They deliberately span the 9 to 10 jump, where the alphabetical
     * order and the version order disagree, and include the milestones and release candidates that precede a final
     * version.
     */
    private static final List<String> EXISTING_VERSIONS =
        List.of("1.0", "2.0", "8.3-milestone-1", "8.3-rc-1", "8.3", "9.0", "10.0");

    private static final String RELEASE_NOTES_STATEMENT = "from doc.object(ReleaseNotes.Code.ReleaseNoteClass)";

    /**
     * The clause selecting the changes that do have a screenshot. It is spelled out by hand rather than with a
     * {@code <> ''} alone because the property is stored as a large string, which some databases return as null
     * rather than as the empty string when it was never set.
     */
    private static final String HAS_SCREENSHOTS =
        "(changes.screenshots <> '' or (changes.screenshots is not null and '' is null))";

    @Mock
    private ScriptQuery query;

    @Mock
    private ScriptQuery releaseNotesQuery;

    @Mock
    private QueryManagerScriptService queryManagerScriptService;

    @Mock
    private ExtensionManagerScriptService extensionScriptService;

    @BeforeEach
    void setUp() throws Exception
    {
        this.componentManager.registerComponent(ScriptService.class, "query", this.queryManagerScriptService);
        when(this.queryManagerScriptService.xwql(anyString())).thenAnswer(invocation -> {
            String statement = invocation.getArgument(0);
            return statement.startsWith(RELEASE_NOTES_STATEMENT) ? this.releaseNotesQuery : this.query;
        });
        when(this.query.bindValue(anyString(), any())).thenReturn(this.query);
        when(this.query.execute()).thenReturn(List.of());
        createReleaseNotes();

        // The macro compares versions through the extension version scheme rather than alphabetically.
        this.componentManager.registerComponent(ScriptService.class, "extension", this.extensionScriptService);
        when(this.extensionScriptService.parseVersion(anyString()))
            .thenAnswer(invocation -> new DefaultVersion((String) invocation.getArgument(0)));

        WikiMacroSetup.loadWikiMacro(this, this.componentManager, GET_CHANGES_MACRO);
    }

    /**
     * Creates one release note page per {@link #EXISTING_VERSIONS} entry, since that is where the macro reads the
     * versions the wiki holds, and makes the query looking for them return those pages.
     */
    private void createReleaseNotes() throws Exception
    {
        XWikiDocument classDocument = this.xwiki.getDocument(RELEASE_NOTE_CLASS, this.context);
        classDocument.getXClass().addTextField("version", "Version", 30);
        this.xwiki.saveDocument(classDocument, this.context);

        List<String> references = new ArrayList<>();
        for (String version : EXISTING_VERSIONS) {
            String name = "Note" + (references.size() + 1);
            XWikiDocument releaseNote = this.xwiki.getDocument(
                new DocumentReference("xwiki", List.of("ReleaseNotes", "Data"), name), this.context);
            BaseObject releaseNoteObject = releaseNote.newXObject(RELEASE_NOTE_CLASS, this.context);
            releaseNoteObject.setStringValue("version", version);
            this.xwiki.saveDocument(releaseNote, this.context);
            references.add("ReleaseNotes.Data." + name);
        }
        when(this.releaseNotesQuery.execute()).thenAnswer(invocation -> references);
    }

    /**
     * A filter without a comparison prefix stays a {@code like}, so that patterns such as {@code 8.3%} keep working,
     * and an {@code =} prefix asks for an exact version. Neither needs to know which versions the wiki holds.
     */
    @ParameterizedTest
    @CsvSource({
        "8.3%, like, 8.3%",
        "=8.3, =,    8.3"
    })
    void versionFilterWithoutComparison(String versions, String expectedOperator, String expectedBoundValue)
        throws Exception
    {
        renderGetChanges(versions);

        assertTrue(mainStatement().contains(String.format("entries.version %s :version1", expectedOperator)),
            String.format("Expected the \"%s\" filter to use the \"%s\" operator, got: %s", versions,
                expectedOperator, mainStatement()));
        assertEquals(List.of(expectedBoundValue), boundVersions());
        verify(this.queryManagerScriptService, never()).xwql(startsWith(RELEASE_NOTES_STATEMENT));
    }

    /**
     * A comparison filter is resolved against the versions the wiki actually holds, using the version order and not
     * the alphabetical one: {@code >=9.0} therefore keeps 10.0, which comes after 9.0 as a version but before it as a
     * string. That order also knows that the milestones and the release candidates of a version precede that version.
     * The boundary version is part of the {@code >=} and {@code <=} results but not of the {@code >} and {@code <}
     * ones.
     */
    @ParameterizedTest
    @CsvSource({
        "'>=9.0', '9.0,10.0'",
        "'<=2.0', '1.0,2.0'",
        "'>9.0',  '10.0'",
        "'<2.0',  '1.0'",
        "'>=8.3', '8.3,9.0,10.0'",
        "'<8.3',  '1.0,2.0,8.3-milestone-1,8.3-rc-1'"
    })
    void versionComparisonFilterUsesVersionOrder(String versions, String expectedVersions) throws Exception
    {
        renderGetChanges(versions);

        assertEquals(List.of(expectedVersions.split(",")), boundVersions());
    }

    /**
     * Since a comparison filter is resolved into exact versions, it is tested with an {@code =}, and the resulting
     * clauses are combined with an {@code or} together with the items that are not comparisons.
     */
    @Test
    void versionFilterWithSeveralItems() throws Exception
    {
        renderGetChanges(">=10.0,8.3%");

        assertTrue(mainStatement().contains("(entries.version = :version1 or entries.version like :version2)"),
            "Expected one clause per resolved filter item, got: " + mainStatement());
        assertEquals(List.of("10.0", "8.3%"), boundVersions());
    }

    /**
     * A comparison filter that no existing version matches must return no change, rather than generate an empty and
     * thus invalid clause.
     */
    @Test
    void versionComparisonFilterMatchingNothing() throws Exception
    {
        renderGetChanges(">=99.0");

        assertTrue(mainStatement().contains("(1 = 0)"),
            "Expected a clause matching nothing, got: " + mainStatement());
        assertEquals(List.of(), boundVersions());
    }

    /**
     * @return the XWQL statement of the query looking for the changes, as opposed to the one looking for the release
     *         notes
     */
    private String mainStatement() throws QueryException
    {
        ArgumentCaptor<String> statement = ArgumentCaptor.forClass(String.class);
        verify(this.queryManagerScriptService, atLeastOnce()).xwql(statement.capture());
        return statement.getAllValues().stream()
            .filter(value -> !value.startsWith(RELEASE_NOTES_STATEMENT))
            .reduce((first, second) -> second)
            .orElseThrow();
    }

    /**
     * @return the values bound to the {@code version} parameters of the query looking for the changes, in binding
     *         order
     */
    private List<String> boundVersions()
    {
        ArgumentCaptor<String> names = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> values = ArgumentCaptor.forClass(Object.class);
        verify(this.query, atLeastOnce()).bindValue(names.capture(), values.capture());
        List<String> versions = new ArrayList<>();
        for (int i = 0; i < names.getAllValues().size(); i++) {
            if (names.getAllValues().get(i).startsWith("version")) {
                versions.add((String) values.getAllValues().get(i));
            }
        }
        return versions;
    }

    /**
     * The audience is stored lower cased, but the macro is documented as accepting the capitalized spelling that
     * reads better in a macro call, so it must lower case whatever it is given before matching.
     */
    @Test
    void audienceFilterIsLowerCased() throws Exception
    {
        renderFilters("audience=\"User,Administrator,DEVELOPER\"");

        assertTrue(mainStatement().contains("(changes.audience like :audience1 or changes.audience like :audience2 "
            + "or changes.audience like :audience3)"), "Got: " + mainStatement());
        verify(this.query).bindValue("audience1", "user");
        verify(this.query).bindValue("audience2", "administrator");
        verify(this.query).bindValue("audience3", "developer");
    }

    /**
     * The importance is stored as a number, but the macro is documented as also accepting the names the edit form
     * displays, in any case.
     */
    @ParameterizedTest
    @CsvSource({
        "High,   2",
        "Medium, 1",
        "Low,    0",
        "HIGH,   2",
        "high,   2",
        "2,      2"
    })
    void importanceFilterAcceptsNames(String importance, String expectedBoundValue) throws Exception
    {
        renderFilters(String.format("importance=\"%s\"", importance));

        verify(this.query).bindValue("importance1", expectedBoundValue);
    }

    /**
     * Each name of a comma-separated importance filter is translated, and only the names are: a filter mixing names
     * and stored values must keep the stored values untouched.
     */
    @Test
    void importanceFilterWithSeveralItems() throws Exception
    {
        renderFilters("importance=\"High,Low,1\"");

        assertTrue(mainStatement().contains("(changes.importance like :importance1 "
            + "or changes.importance like :importance2 or changes.importance like :importance3)"),
            "Got: " + mainStatement());
        verify(this.query).bindValue("importance1", "2");
        verify(this.query).bindValue("importance2", "0");
        verify(this.query).bindValue("importance3", "1");
    }

    /**
     * The screenshot filter is not a bound parameter but a hand-written clause, and the two values must select
     * complementary sets of changes: a release note leads with the changes illustrated by a screenshot and lists
     * all the others under "Miscellaneous", so a change missing from both would never be displayed.
     */
    @Test
    void containsScreenshotsSelectsTheChangesHavingOne() throws Exception
    {
        renderFilters("containsScreenshots=\"true\"");

        assertTrue(mainStatement().contains("and " + HAS_SCREENSHOTS), "Got: " + mainStatement());
        assertFalse(mainStatement().contains("and not " + HAS_SCREENSHOTS), "Got: " + mainStatement());
    }

    @Test
    void containsScreenshotsFalseSelectsTheChangesHavingNone() throws Exception
    {
        renderFilters("containsScreenshots=\"false\"");

        assertTrue(mainStatement().contains("and not " + HAS_SCREENSHOTS), "Got: " + mainStatement());
    }

    /**
     * The screenshot filter has no default value, and an unset filter must not restrict the result at all.
     */
    @Test
    void containsScreenshotsUnsetDoesNotFilter() throws Exception
    {
        renderFilters("products=\"TestProduct\"");

        assertFalse(mainStatement().contains("changes.screenshots"), "Got: " + mainStatement());
    }

    /**
     * All the filters are combined with an {@code and}, each of them restricting the result further.
     */
    @Test
    void allFiltersAreCombined() throws Exception
    {
        renderFilters("products=\"TestProduct\" versions=\"8.3\" audience=\"User\" categories=\"UI\" "
            + "importance=\"High\" containsScreenshots=\"true\"");

        assertEquals("from doc.object(ReleaseNotes.Code.EntryClass) as entries, "
            + "doc.object(ReleaseNotes.Code.Change.ChangeClass) as changes where "
            + "(entries.product like :product1) "
            + "and (changes.audience like :audience1) "
            + "and (entries.version like :version1) "
            + "and (changes.category like :category1) "
            + "and (changes.importance like :importance1) "
            + "and " + HAS_SCREENSHOTS + " "
            + "order by changes.importance desc, doc.fullName", mainStatement().replaceAll("\\s+", " ").trim());
        verify(this.query).bindValue("product1", "TestProduct");
        verify(this.query).bindValue("audience1", "user");
        verify(this.query).bindValue("version1", "8.3");
        verify(this.query).bindValue("category1", "UI");
        verify(this.query).bindValue("importance1", "2");
    }

    /**
     * Called with only its mandatory parameter, the macro must return every change of the default product, since
     * that is what the report pages rely on to offer a filter that filters nothing.
     */
    @Test
    void defaultFiltersMatchEveryChangeOfTheDefaultProduct() throws Exception
    {
        renderFilters("");

        verify(this.query).bindValue("product1", "XWiki");
        verify(this.query).bindValue("version1", "%");
        verify(this.query).bindValue("category1", "%");
        // The three importance values that ChangeClass allows.
        verify(this.query).bindValue("importance1", "0");
        verify(this.query).bindValue("importance2", "1");
        verify(this.query).bindValue("importance3", "2");
    }

    /**
     * The result set size is driven by the filters, whose defaults are wildcards and which the report page fills
     * from the request. The query must therefore always be asked for a single page, and for the one row beyond it
     * that tells whether more changes exist.
     */
    @Test
    void queryIsAlwaysAskedForOnePageOnly() throws Exception
    {
        renderGetChanges("%");

        verify(this.query).setLimit(101);
        verify(this.query).setOffset(0);
    }

    @Test
    void limitAndOffsetSelectThePageToReturn() throws Exception
    {
        render("{{getChanges products=\"TestProduct\" limit=\"10\" offset=\"20\" contextVariable=\"c\"/}}");

        verify(this.query).setLimit(11);
        verify(this.query).setOffset(20);
    }

    /**
     * A limit is a bound, so a value that would remove it must not be honoured, and neither must a value that would
     * make the query return nothing at all.
     */
    @ParameterizedTest
    @CsvSource({ "0", "-1", "notANumber", "''" })
    void unusableLimitFallsBackToTheDefault(String limit) throws Exception
    {
        render(String.format("{{getChanges products=\"TestProduct\" limit=\"%s\" contextVariable=\"c\"/}}",
            limit));

        verify(this.query).setLimit(101);
    }

    @ParameterizedTest
    @CsvSource({ "-1", "notANumber", "''" })
    void unusableOffsetFallsBackToTheFirstPage(String offset) throws Exception
    {
        render(String.format("{{getChanges products=\"TestProduct\" offset=\"%s\" contextVariable=\"c\"/}}",
            offset));

        verify(this.query).setOffset(0);
    }

    /**
     * Importance has three values, so ordering on it alone leaves most of the result set in whatever order the
     * database returns it, and a page of an unordered result set may repeat or skip changes.
     */
    @Test
    void resultIsOrderedOnMoreThanTheImportance() throws Exception
    {
        renderGetChanges("%");

        ArgumentCaptor<String> statement = ArgumentCaptor.forClass(String.class);
        verify(this.queryManagerScriptService).xwql(statement.capture());
        assertTrue(statement.getValue().endsWith("order by changes.importance desc, doc.fullName"),
            "Expected the order to be fully determined, got: " + statement.getValue());
    }

    /**
     * The row fetched beyond the page is only there to answer the "is there a next page?" question: it must be
     * reported, under the name derived from the context variable, and it must not reach the caller's list.
     */
    @Test
    void rowBeyondThePageIsReportedRatherThanReturned() throws Exception
    {
        when(this.query.execute()).thenReturn(List.of("first", "second", "third"));

        String result = renderPageOfTwo();

        assertTrue(result.contains("[first, second] / true"),
            "Expected the page to hold the first two changes and to report the third, got: " + result);
    }

    @Test
    void lastPageIsNotReportedAsHavingMoreChanges() throws Exception
    {
        when(this.query.execute()).thenReturn(List.of("first", "second"));

        String result = renderPageOfTwo();

        assertTrue(result.contains("[first, second] / false"),
            "Expected the page to hold both changes and to report no other one, got: " + result);
    }

    /**
     * Renders a page asking for two changes and displaying both what the macro published and whether it reported
     * more changes to come.
     */
    private String renderPageOfTwo() throws Exception
    {
        return render("{{getChanges products=\"TestProduct\" limit=\"2\" contextVariable=\"changeDocs\"/}}\n\n"
            + "{{velocity}}$changeDocs / $changeDocsHasMore{{/velocity}}");
    }

    /**
     * Renders a page calling the {@code getChanges} macro with the passed filters, i.e. with all its parameters
     * but the mandatory one.
     */
    private String renderFilters(String filters) throws Exception
    {
        return render(String.format("{{getChanges %s contextVariable=\"changeDocs\"/}}", filters));
    }

    /**
     * Renders a page calling the {@code getChanges} macro with the passed {@code versions} filter.
     */
    private void renderGetChanges(String versions) throws Exception
    {
        render(String.format(
            "{{getChanges products=\"TestProduct\" versions=\"%s\" contextVariable=\"changeDocs\"/}}", versions));
    }

    /**
     * Renders the passed content as a page of the release notes space, which is what makes the macros resolve their
     * own top level space.
     */
    private String render(String content) throws Exception
    {
        XWikiDocument page = new XWikiDocument(TEST_PAGE);
        page.setSyntax(Syntax.XWIKI_2_1);
        page.setContent(content);
        this.xwiki.saveDocument(page, this.context);
        return page.getRenderedContent(this.context);
    }
}
