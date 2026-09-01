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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.wikimacro.internal.WikiMacroFactoryComponentClass;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.WikiMacroSetup;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Page test for the {@code displayChanges} wiki macro, defined in
 * {@code ReleaseNotes.Code.Change.DisplayChangesMacro}, and for the displayers it selects.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
@WikiMacroFactoryComponentClass
class DisplayChangesMacroPageTest extends PageTest
{
    private static final List<String> CHANGE_SPACE = List.of("ReleaseNotes", "Code", "Change");

    private static final DocumentReference DISPLAY_CHANGES_MACRO =
        new DocumentReference("xwiki", CHANGE_SPACE, "DisplayChangesMacro");

    private static final DocumentReference CHANGE_CLASS =
        new DocumentReference("xwiki", CHANGE_SPACE, "ChangeClass");

    private static final List<String> VERSION_SPACE = List.of("ReleaseNotes", "Data", "TestProduct", "8.3");

    private static final DocumentReference TEST_PAGE =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Data"), "TestPage");

    private static final String TITLE = "A first change";

    private static final String SUMMARY = "What the first change brings";

    /**
     * Serializes a change reference the way the getChanges query returns it, i.e. as the local reference of the page,
     * with the dots of the version space escaped.
     */
    private EntityReferenceSerializer<String> localSerializer;

    @BeforeEach
    void setUp() throws Exception
    {
        this.localSerializer = this.componentManager.getInstance(EntityReferenceSerializer.TYPE_STRING, "local");

        loadPage(CHANGE_CLASS);
        for (String displayer : List.of("Simple", "List", "Grid", "Flow")) {
            loadPage(new DocumentReference("xwiki", CHANGE_SPACE, "ChangeDisplayer" + displayer));
        }
        loadPage(new DocumentReference("xwiki", CHANGE_SPACE, "ChangeDisplayerVelocityMacros"));
        WikiMacroSetup.loadWikiMacro(this, this.componentManager, DISPLAY_CHANGES_MACRO);
    }

    /**
     * Only the default displayer is exercised by the functional tests, but every displayer is macro API and must
     * display at least the title and the summary of each change it is given.
     */
    @ParameterizedTest
    @ValueSource(strings = { "simple", "list", "grid", "flow" })
    void everyDisplayerDisplaysTheChange(String displayer) throws Exception
    {
        DocumentReference change = createChange("Entry001", TITLE, SUMMARY, "");

        String text = render(String.format("displayer=\"%s\"", displayer), change).text();

        assertTrue(text.contains(TITLE), String.format("The \"%s\" displayer dropped the title: %s", displayer, text));
        assertTrue(text.contains(SUMMARY),
            String.format("The \"%s\" displayer dropped the summary: %s", displayer, text));
    }

    /**
     * The {@code simple} displayer gives each change a section of its own, and links to the change page when the
     * change has a description that the summary alone doesn't carry.
     */
    @Test
    void simpleDisplayerGivesEachChangeItsOwnSection() throws Exception
    {
        DocumentReference first = createChange("Entry001", TITLE, SUMMARY, "The long story");
        DocumentReference second = createChange("Entry002", "A second change", "What the second change brings", "");

        Document html = render("displayer=\"simple\"", first, second);

        assertEquals(List.of(TITLE, "A second change"), html.select("h3").eachText());
        // Only the change that has a description is worth opening.
        assertEquals(List.of("More details"), html.select("a").eachText());
    }

    /**
     * The {@code list} displayer is the one the "Miscellaneous" parts of a release note use, so it must pack each
     * change into a single list item, with its title introducing its summary.
     */
    @Test
    void listDisplayerPacksEachChangeInAListItem() throws Exception
    {
        DocumentReference first = createChange("Entry001", TITLE, SUMMARY, "The long story");
        DocumentReference second = createChange("Entry002", "", "A change with no title", "");

        Elements items = render("displayer=\"list\"", first, second).select("ul > li");

        assertEquals(2, items.size(), "Expected one list item per change.");
        assertEquals(TITLE + ": " + SUMMARY, items.get(0).text());
        // A change with no title is nothing but its summary, and must not be prefixed with a stray colon.
        assertEquals("A change with no title", items.get(1).text());
    }

    /**
     * The {@code flow} displayer lays a change out as its media next to its text, which is what its two half-width
     * columns are for.
     */
    @Test
    void flowDisplayerLaysTheMediaNextToTheText() throws Exception
    {
        DocumentReference change = createChange("Entry001", TITLE, SUMMARY, "");

        Document html = render("displayer=\"flow\"", change);

        Elements rows = html.select("div.row");
        assertEquals(1, rows.size(), "Expected one row per change.");
        assertEquals(2, rows.get(0).select("div.col-xs-6").size(),
            "Expected the media and the text to share the row.");
        assertEquals(List.of(TITLE), html.select("h3").eachText());
    }

    /**
     * The {@code grid} displayer makes each change a card of a CSS grid, and the number of columns of that grid
     * reaches the stylesheet as a custom property.
     */
    @Test
    void gridDisplayerLaysTheChangesOutInCards() throws Exception
    {
        DocumentReference first = createChange("Entry001", TITLE, SUMMARY, "");
        DocumentReference second = createChange("Entry002", "A second change", "What the second change brings", "");

        Elements grids = render("displayer=\"grid\" columns=\"3\"", first, second).select("div.rn-changes-grid");

        assertEquals(1, grids.size(), "Expected the cards to share a single grid.");
        assertTrue(grids.get(0).attr("style").contains("--rn-changes-grid-columns: 3"),
            "Expected the column count to reach the stylesheet, got: " + grids.get(0).attr("style"));
        assertEquals(2, grids.get(0).select("div.rn-change-card").size(), "Expected one card per change.");
    }

    /**
     * The displayer name becomes part of the name of the page that is included, so a name that is not a plain name
     * must fall back to the default displayer rather than include whatever it points at.
     */
    @ParameterizedTest
    @ValueSource(strings = { "", "../../XWiki/XWikiPreferences" })
    void displayerThatIsNotAPlainNameFallsBackToTheDefaultOne(String displayer) throws Exception
    {
        DocumentReference change = createChange("Entry001", TITLE, SUMMARY, "");

        Document html = render(String.format("displayer=\"%s\"", displayer), change);

        assertEquals(1, html.select("div.rn-changes-grid").size(),
            "Expected the default displayer, got: " + html.body().html());
    }

    /**
     * The macro is given the name of the variable to display, and a name that no variable matches must display the
     * same "no changes" message as an empty list, and never a leftover value.
     */
    @Test
    void unknownContextVariableDisplaysNoChanges() throws Exception
    {
        DocumentReference change = createChange("Entry001", TITLE, SUMMARY, "");

        String text = render("", "otherChangeDocs", change).text();

        assertEquals("No changes!", text);
        assertFalse(text.contains(TITLE), "Expected no change at all to be displayed.");
    }

    private Document render(String macroParameters, DocumentReference... changes) throws Exception
    {
        return render(macroParameters, "changeDocs", changes);
    }

    /**
     * Renders a page calling the {@code displayChanges} macro on the passed changes.
     *
     * @param macroParameters the macro parameters, besides the context variable
     * @param variable the name under which the changes are published, i.e. the {@code contextVariable} parameter
     * @param changes the changes to display
     */
    private Document render(String macroParameters, String variable, DocumentReference... changes) throws Exception
    {
        String changeList = Stream.of(changes)
            .map(change -> String.format("'%s'", this.localSerializer.serialize(change)))
            .collect(Collectors.joining(", "));

        XWikiDocument page = new XWikiDocument(TEST_PAGE);
        page.setSyntax(Syntax.XWIKI_2_1);
        // The list is published under a name of the caller's choosing, the way the getChanges macro publishes it.
        page.setContent(String.format("{{velocity}}%n#set ($changeDocs = [%s])%n{{/velocity}}%n%n"
            + "{{displayChanges %s contextVariable=\"%s\"/}}", changeList, macroParameters, variable));
        this.xwiki.saveDocument(page, this.context);
        // The displayers resolve the top level space of the application from the document being rendered.
        this.context.setDoc(page);

        return Jsoup.parseBodyFragment(page.getRenderedContent(this.context));
    }

    private DocumentReference createChange(String name, String title, String summary, String description)
        throws Exception
    {
        List<String> spaces = new ArrayList<>(VERSION_SPACE);
        spaces.add(name);
        XWikiDocument change = new XWikiDocument(new DocumentReference("xwiki", spaces, "WebHome"));
        change.setSyntax(Syntax.XWIKI_2_1);
        BaseObject changeObject = change.newXObject(CHANGE_CLASS, this.context);
        changeObject.setStringValue("title", title);
        changeObject.setLargeStringValue("summary", summary);
        changeObject.setLargeStringValue("description", description);
        this.xwiki.saveDocument(change, this.context);

        return change.getDocumentReference();
    }
}
