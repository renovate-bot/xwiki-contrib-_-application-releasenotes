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

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.query.internal.ScriptQuery;
import org.xwiki.query.script.QueryManagerScriptService;
import org.xwiki.rendering.wikimacro.internal.WikiMacroFactoryComponentClass;
import org.xwiki.script.service.ScriptService;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.WikiMacroSetup;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Page test for {@code ReleaseNotes.Code.Report}, which renders the report described by the request parameters.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
@WikiMacroFactoryComponentClass
class ReportPageTest extends PageTest
{
    private static final DocumentReference REPORT =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "Report");

    private static final DocumentReference GET_CHANGES_MACRO =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code", "Change"), "GetChangesMacro");

    private static final DocumentReference DISPLAY_CHANGES_MACRO =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code", "Change"), "DisplayChangesMacro");

    /**
     * The page size of the report, which is what its paging links move by.
     */
    private static final int PAGE_SIZE = 20;

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
        WikiMacroSetup.loadWikiMacro(this, this.componentManager, DISPLAY_CHANGES_MACRO);
    }

    /**
     * The filters come from the request, so a report can be asked for every change of a product at once. It must
     * display one page at a time, and offer the way to the next one rather than drop the changes it left out.
     */
    @Test
    void firstPageOffersTheNextOneOnly() throws Exception
    {
        // A full page, plus the row that tells the report that a next page exists.
        when(this.query.execute()).thenReturn(changes(PAGE_SIZE + 1));
        this.request.put("products", "XWiki");

        Elements links = renderReportPaginationLinks();

        assertEquals(1, links.size(), "The first page has no previous page to link to.");
        assertPageLink(links.get(0), PAGE_SIZE);
    }

    /**
     * A page of a report is reached by URL, so the filters it was built from have to travel with the paging links,
     * otherwise the next page would report on every change instead.
     */
    @Test
    void pagingLinksCarryTheFiltersOfTheReport() throws Exception
    {
        when(this.query.execute()).thenReturn(changes(PAGE_SIZE + 1));
        this.request.put("products", "XWiki");
        this.request.put("versions", "8.3");

        Elements links = renderReportPaginationLinks();

        assertEquals(1, links.size());
        String href = links.get(0).attr("href");
        assertTrue(href.contains("products=XWiki"), "Expected the product filter to be kept, got: " + href);
        assertTrue(href.contains("versions=8.3"), "Expected the version filter to be kept, got: " + href);
    }

    @Test
    void laterPageOffersBothDirections() throws Exception
    {
        when(this.query.execute()).thenReturn(changes(PAGE_SIZE + 1));
        this.request.put("products", "XWiki");
        this.request.put("offset", String.valueOf(2 * PAGE_SIZE));

        Elements links = renderReportPaginationLinks();

        assertEquals(2, links.size(), "A page in the middle of the report has one link in each direction.");
        assertPageLink(links.get(0), PAGE_SIZE);
        assertPageLink(links.get(1), 3 * PAGE_SIZE);
    }

    /**
     * The last page of a report is the one the query had nothing left to return after, and the first page of a
     * report short enough to hold all its changes is also its last one.
     */
    @Test
    void reportHoldingAllItsChangesOffersNoPagingAtAll() throws Exception
    {
        when(this.query.execute()).thenReturn(changes(PAGE_SIZE));
        this.request.put("products", "XWiki");

        assertEquals(0, renderReportPaginationLinks().size());
    }

    private void assertPageLink(Element link, int expectedOffset)
    {
        assertTrue(link.attr("href").contains("offset=" + expectedOffset),
            String.format("Expected a link to the page at offset %s, got: %s", expectedOffset, link.attr("href")));
    }

    /**
     * Renders the report and returns the links of its paging area.
     */
    private Elements renderReportPaginationLinks() throws Exception
    {
        return renderHTMLPage(REPORT).select(".rn-report-pagination a");
    }

    /**
     * @param count the number of rows the query must return
     * @return as many change document names as asked for, which is all the report needs from the query since it
     *         only counts and displays them
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
