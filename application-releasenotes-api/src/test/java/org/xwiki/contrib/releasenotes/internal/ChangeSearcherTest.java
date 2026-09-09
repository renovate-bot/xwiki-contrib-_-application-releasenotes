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

import java.util.Arrays;
import java.util.List;

import jakarta.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.xwiki.contrib.releasenotes.ChangeFilter;
import org.xwiki.contrib.releasenotes.ChangeFilter.Operator;
import org.xwiki.contrib.releasenotes.ChangeQuery;
import org.xwiki.contrib.releasenotes.ChangeSearchResult;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ChangeSearcher}, covering what the page test of the {@code getChanges} macro cannot reach:
 * the query the versions of the wiki are read with, and the page the result is cut into.
 *
 * @version $Id$
 */
@ComponentTest
class ChangeSearcherTest
{
    /**
     * The statement the versions the wiki holds a release note for are read with. Only the version of each release
     * note is selected, and each of them only once.
     */
    private static final String EXISTING_VERSIONS_STATEMENT =
        "select distinct note.version from Document doc, doc.object(ReleaseNotes.Code.ReleaseNoteClass) as note";

    private static final String CHANGE = "ReleaseNotes.Data.XWiki.8.3.Entry001.WebHome";

    @InjectMockComponents
    private ChangeSearcher searcher;

    @MockComponent
    private QueryManager queryManager;

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @MockComponent
    @Named("local")
    private EntityReferenceSerializer<String> localEntityReferenceSerializer;

    @Mock
    private Query query;

    @Mock
    private Query existingVersionsQuery;

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.localEntityReferenceSerializer.serialize(ReleaseNotesReferences.ENTRY_CLASS))
            .thenReturn("ReleaseNotes.Code.EntryClass");
        when(this.localEntityReferenceSerializer.serialize(ReleaseNotesReferences.CHANGE_CLASS))
            .thenReturn("ReleaseNotes.Code.Change.ChangeClass");
        when(this.localEntityReferenceSerializer.serialize(ReleaseNotesReferences.RELEASE_NOTE_CLASS))
            .thenReturn("ReleaseNotes.Code.ReleaseNoteClass");

        when(this.queryManager.createQuery(anyString(), anyString())).thenAnswer(invocation -> {
            String statement = invocation.getArgument(0);
            return statement.startsWith("select") ? this.existingVersionsQuery : this.query;
        });
        when(this.query.bindValue(anyString(), any())).thenReturn(this.query);
        when(this.query.execute()).thenReturn(List.of());
        when(this.existingVersionsQuery.execute()).thenReturn(List.of());
    }

    /**
     * The versions the wiki holds a release note for are read in one query, which selects the version of each of
     * them and nothing else.
     */
    @Test
    void theVersionsOfTheWikiAreReadWithOneQuery() throws Exception
    {
        doReturn(Arrays.asList("8.3", "9.0", "10.0")).when(this.existingVersionsQuery).execute();

        this.searcher.search(versionQuery(new ChangeFilter(Operator.GTE, "9.0")));

        verify(this.queryManager).createQuery(EXISTING_VERSIONS_STATEMENT, Query.XWQL);
        // The comparison is resolved into the existing versions it matches, so the search itself only tests
        // equality, and 10.0 is one of them since it comes after 9.0 as a version.
        verify(this.query).bindValue("version1", "9.0");
        verify(this.query).bindValue("version2", "10.0");
    }

    /**
     * A comparison is resolved with the version order and not the alphabetical one, so that {@code >=9.0} keeps
     * 10.0, which comes after 9.0 as a version but before it as a string. The bound is part of the {@code >=} and
     * {@code <=} results, and not of the {@code >} and {@code <} ones.
     */
    @ParameterizedTest
    @CsvSource({
        "GTE, '9.0,10.0'",
        "LTE, '8.3,9.0'",
        "GT,  '10.0'",
        "LT,  '8.3'"
    })
    void aComparisonIsResolvedWithTheVersionOrder(Operator operator, String expectedVersions) throws Exception
    {
        doReturn(Arrays.asList("8.3", "9.0", "10.0")).when(this.existingVersionsQuery).execute();

        this.searcher.search(versionQuery(new ChangeFilter(operator, "9.0")));

        String[] versions = expectedVersions.split(",");

        for (int index = 0; index < versions.length; index++) {
            verify(this.query).bindValue("version" + (index + 1), versions[index]);
        }

        verify(this.query, never()).bindValue("version" + (versions.length + 1), null);
    }

    /**
     * A filter is bound to a parameter of its own property, so that the values of one property never restrict
     * another one.
     */
    @Test
    void aFilterOnEveryPropertyIsBoundToItsOwnParameter() throws Exception
    {
        ChangeQuery query = new ChangeQuery();
        query.setProducts(List.of(new ChangeFilter(Operator.LIKE, "XWiki")));
        query.setVersions(List.of(new ChangeFilter(Operator.EQUALS, "8.3")));
        query.setAudiences(List.of(new ChangeFilter(Operator.LIKE, "user")));
        query.setCategories(List.of(new ChangeFilter(Operator.LIKE, "UI")));
        query.setImportances(List.of(new ChangeFilter(Operator.LIKE, "2")));

        this.searcher.search(query);

        verify(this.query).bindValue("product1", "XWiki");
        verify(this.query).bindValue("version1", "8.3");
        verify(this.query).bindValue("audience1", "user");
        verify(this.query).bindValue("category1", "UI");
        verify(this.query).bindValue("importance1", "2");
    }

    /**
     * Whether a change is illustrated is not a value to compare but a condition of its own, and the two values of
     * the filter must select complementary sets of changes: a release note leads with the changes illustrated by a
     * screenshot and lists all the others under "Miscellaneous", so a change missing from both would never be
     * displayed.
     */
    @ParameterizedTest
    @CsvSource({
        "true,  false",
        "false, true"
    })
    void theScreenshotFilterIsAConditionOfItsOwn(boolean containsScreenshots, boolean expectedNegation)
        throws Exception
    {
        ChangeQuery query = new ChangeQuery();
        query.setContainsScreenshots(containsScreenshots);

        this.searcher.search(query);

        ArgumentCaptor<String> statement = ArgumentCaptor.forClass(String.class);
        verify(this.queryManager).createQuery(statement.capture(), anyString());
        assertTrue(statement.getValue().contains("changes.screenshots"), statement.getValue());
        assertEquals(expectedNegation, statement.getValue().contains("and not ("), statement.getValue());
    }

    /**
     * A release note whose version was left empty says nothing about which versions the wiki holds, and must not
     * become a version a comparison matches.
     */
    @Test
    void aReleaseNoteWithoutAVersionIsLeftOut() throws Exception
    {
        doReturn(Arrays.asList("", "  ", "9.0")).when(this.existingVersionsQuery).execute();

        this.searcher.search(versionQuery(new ChangeFilter(Operator.GTE, "1.0")));

        verify(this.query).bindValue("version1", "9.0");
        verify(this.query, never()).bindValue("version2", "");
    }

    /**
     * The versions of the wiki are only worth reading when a filter compares versions: a pattern and an exact
     * version are tested by the database itself.
     */
    @Test
    void theVersionsOfTheWikiAreOnlyReadWhenAFilterComparesThem() throws Exception
    {
        this.searcher.search(versionQuery(new ChangeFilter(Operator.LIKE, "8.3%")));

        verify(this.queryManager, never()).createQuery(startsWith("select"), anyString());
    }

    /**
     * A comparison no existing version matches leaves the version filter with no value at all, which must return no
     * change rather than every one of them.
     */
    @Test
    void aComparisonMatchingNoExistingVersionMatchesNoChange() throws Exception
    {
        this.searcher.search(versionQuery(new ChangeFilter(Operator.GTE, "99.0")));

        verify(this.queryManager).createQuery(startsWith("from doc.object(ReleaseNotes.Code.EntryClass)"),
            anyString());
        verify(this.query, never()).bindValue(startsWith("version"), any());
    }

    /**
     * The change beyond the page the caller asked for is what tells that a next page exists, and it is not part of
     * the page: a caller displaying it would display it twice, once here and once on the next page.
     */
    @Test
    void theChangeBeyondThePageIsReportedRatherThanReturned() throws Exception
    {
        doReturn(List.of(CHANGE, "second", "third")).when(this.query).execute();
        DocumentReference reference = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", "XWiki", "8.3", "Entry001"), "WebHome");
        when(this.documentReferenceResolver.resolve(CHANGE)).thenReturn(reference);

        ChangeQuery changeQuery = new ChangeQuery();
        changeQuery.setLimit(2);
        changeQuery.setOffset(10);
        ChangeSearchResult result = this.searcher.search(changeQuery);

        verify(this.query).setLimit(3);
        verify(this.query).setOffset(10);
        assertEquals(List.of(CHANGE, "second"), result.getChangeNames());
        assertEquals(reference, result.getChanges().get(0));
        assertTrue(result.hasMore());
    }

    /**
     * The pages displaying the changes of a release note take their own exclusions out of the list they are given,
     * so that list has to be theirs to modify.
     */
    @Test
    void theChangesOfTheResultMayBeRemovedFromIt() throws Exception
    {
        doReturn(List.of(CHANGE, "second")).when(this.query).execute();

        ChangeSearchResult result = this.searcher.search(new ChangeQuery());

        assertTrue(result.getChangeNames().removeAll(List.of("second")));
        assertEquals(List.of(CHANGE), result.getChangeNames());
        assertFalse(result.hasMore());
    }

    /**
     * A search that cannot be run is not a search that found nothing, so it is reported rather than turned into an
     * empty release note.
     */
    @Test
    void aSearchThatCannotBeRunIsReported() throws Exception
    {
        when(this.query.execute()).thenThrow(new QueryException("Broken", null, null));

        assertThrows(ReleaseNotesException.class, () -> this.searcher.search(new ChangeQuery()));
    }

    /**
     * The versions of the wiki are read to resolve a comparison, so a search that cannot read them is a search that
     * cannot be resolved, and is reported rather than answered with the wrong changes.
     */
    @Test
    void aSearchThatCannotReadTheVersionsIsReported() throws Exception
    {
        when(this.existingVersionsQuery.execute()).thenThrow(new QueryException("Broken", null, null));

        assertThrows(ReleaseNotesException.class,
            () -> this.searcher.search(versionQuery(new ChangeFilter(Operator.GTE, "9.0"))));
    }

    /**
     * @param filter the only version filter of the query
     * @return a query filtering nothing but the version
     */
    private ChangeQuery versionQuery(ChangeFilter filter)
    {
        ChangeQuery query = new ChangeQuery();
        query.setVersions(List.of(filter));

        return query;
    }
}
