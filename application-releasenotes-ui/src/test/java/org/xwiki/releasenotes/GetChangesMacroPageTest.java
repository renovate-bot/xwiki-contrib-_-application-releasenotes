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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
        when(this.query.execute()).thenReturn(List.of());

        WikiMacroSetup.loadWikiMacro(this, this.componentManager, GET_CHANGES_MACRO);
    }

    /**
     * The {@code >=} and {@code <=} version filters must map to the matching XWQL operators and bind the version
     * without their two-character prefix, so that the boundary version is part of the result. The strict {@code >}
     * and {@code <} filters keep excluding it, and a filter without any prefix stays a {@code like}.
     */
    @ParameterizedTest
    @CsvSource({
        ">=2.0, >=, 2.0",
        "<=2.0, <=, 2.0",
        ">2.0,  >,  2.0",
        "<2.0,  <,  2.0",
        "2.0,   like, 2.0"
    })
    void versionFilterOperator(String versions, String expectedOperator, String expectedBoundValue) throws Exception
    {
        renderGetChanges(versions);

        ArgumentCaptor<String> statement = ArgumentCaptor.forClass(String.class);
        verify(this.queryManagerScriptService).xwql(statement.capture());
        assertTrue(statement.getValue().contains(String.format("entries.version %s :version1", expectedOperator)),
            String.format("Expected the \"%s\" filter to use the \"%s\" operator, got: %s", versions,
                expectedOperator, statement.getValue()));
        verify(this.query).bindValue("version1", expectedBoundValue);
    }

    /**
     * A comma-separated filter combines its items with an {@code or}, each one getting its own query parameter.
     */
    @Test
    void versionFilterWithSeveralItems() throws Exception
    {
        renderGetChanges(">=1.0,<3.0");

        ArgumentCaptor<String> statement = ArgumentCaptor.forClass(String.class);
        verify(this.queryManagerScriptService).xwql(statement.capture());
        assertTrue(statement.getValue()
                .contains("(entries.version >= :version1 or entries.version < :version2)"),
            "Expected one clause per filter item, got: " + statement.getValue());
        verify(this.query).bindValue("version1", "1.0");
        verify(this.query).bindValue("version2", "3.0");
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
