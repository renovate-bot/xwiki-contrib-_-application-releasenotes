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
     * Renders a page calling the {@code getChanges} macro with the passed {@code versions} filter.
     */
    private void renderGetChanges(String versions) throws Exception
    {
        XWikiDocument page = new XWikiDocument(TEST_PAGE);
        page.setSyntax(Syntax.XWIKI_2_1);
        page.setContent(String.format(
            "{{getChanges products=\"TestProduct\" versions=\"%s\" contextVariable=\"changeDocs\"/}}", versions));
        this.xwiki.saveDocument(page, this.context);
        page.getRenderedContent(this.context);
    }
}
