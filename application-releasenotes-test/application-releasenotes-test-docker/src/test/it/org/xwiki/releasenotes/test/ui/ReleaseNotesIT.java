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
package org.xwiki.releasenotes.test.ui;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.livedata.test.po.LiveDataElement;
import org.xwiki.livedata.test.po.TableLayoutElement;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.ObjectPropertyReference;
import org.xwiki.model.reference.ObjectReference;
import org.xwiki.rest.model.jaxb.Page;
import org.xwiki.rest.model.jaxb.Property;
import org.xwiki.releasenotes.test.ui.po.PropertiesPanelElement;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.InlinePage;
import org.xwiki.test.ui.po.SuggestInputElement;
import org.xwiki.test.ui.po.ViewPage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI tests for the Release Notes Application.
 *
 * @version $Id$
 */
@UITest
class ReleaseNotesIT
{
    /**
     * The product of the two changes shared by the tests that need changes only in order to render them.
     */
    private static final String DISPLAY_PRODUCT = "DisplayProduct";

    /**
     * Whether the changes of {@link #DISPLAY_PRODUCT} have already been created, so that the first of the tests
     * sharing them builds them and the next ones reuse them.
     */
    private static boolean sharedChangesCreated;

    /**
     * Walks the contributors flow of a release note holding both application macros, the way the pages created from
     * {@code ReleaseNotes.Code.ReleaseNoteTemplate} do: the macro warns while no list exists and offers an "Add
     * contributors" button, that button opens the Contributors child page in inline edit mode, and saving there
     * renders the names back on the release note, sorted ignoring case and with their wiki syntax escaped. Ends with
     * adding the first change of that version, which must be numbered Entry001 even though the version space already
     * holds a Contributors entry.
     */
    @Test
    @Order(1)
    void contributorsListAndChangeNumbering(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        String product = "ContribProduct";
        DocumentReference releaseNote =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", product, "1.0"), "WebHome");
        setup.rest().delete(releaseNote);
        DocumentReference contributorsEntry = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", product, "1.0", "Contributors"), "WebHome");
        setup.rest().delete(contributorsEntry);

        // Both macros, in the order the shipped release note template holds them.
        setup.createPage(releaseNote, "= New and Noteworthy =\n\n{{releasenotechanges/}}\n\n= Credits =\n\n"
            + "{{releasenotecontributors/}}", "RN 1.0");
        // The changes macro only offers its "Add ... Change" forms on a note that is not released yet.
        setup.addObject(releaseNote, "ReleaseNotes.Code.ReleaseNoteClass",
            "product", product, "version", "1.0", "released", "0");

        // Before any contributors list exists, the macro shows the warning and offers the button to an editor.
        ViewPage beforePage = setup.gotoPage(releaseNote);
        assertTrue(beforePage.getContent().contains("The list of contributors has not been generated yet."),
            "Expected the not-generated-yet warning before the contributors list exists.");
        WebElement addButton = setup.getDriver().findElementWithoutWaiting(
            By.cssSelector("input.button[value='Add contributors']"));

        // Click "Add contributors": lands on the Contributors page in inline edit mode. The names are typed unsorted
        // and with mixed case to exercise the case-insensitive alphabetical ordering, and one of them carries bold
        // wiki syntax to exercise the escaping.
        addButton.click();
        WebElement textarea = setup.getDriver().findElement(By.cssSelector("textarea"));
        textarea.clear();
        textarea.sendKeys("bob jones\nAlice Smith\nCarol Nguyen\n**Robert Tables**");
        setup.getDriver().findElement(By.cssSelector("input[name='action_save']")).click();

        // Back on the release note: every name renders, sorted alphabetically ignoring case, and the warning is gone.
        ViewPage afterPage = setup.gotoPage(releaseNote);
        String content = afterPage.getContent();
        assertTrue(content.contains("Alice Smith"), "Expected the first contributor to be rendered.");
        assertTrue(content.contains("bob jones"), "Expected the second contributor to be rendered.");
        assertTrue(content.contains("Carol Nguyen"), "Expected the third contributor to be rendered.");
        assertTrue(content.indexOf("Alice Smith") < content.indexOf("bob jones")
            && content.indexOf("bob jones") < content.indexOf("Carol Nguyen"),
            "Contributors must be sorted alphabetically ignoring case, got: " + content);
        assertFalse(content.contains("has not been generated yet"),
            "Warning must disappear once the contributors list has been saved.");
        // A name carrying bold wiki syntax: if escaped, the asterisks survive in the rendered text; if interpreted,
        // the name would render as bold and the asterisks would be gone.
        assertTrue(content.contains("**Robert Tables**"),
            "Wiki syntax in a contributor name must be escaped and rendered literally, got: " + content);

        // The Contributors page created from the macro is a technical child page: it must be hidden.
        Page savedEntry = setup.rest().get(contributorsEntry);
        assertTrue(savedEntry.isHidden(), "The Contributors child page must be created as a hidden page.");

        // Kept last because it only redirects: a Contributors entry now exists in the version space but no change
        // entry does, and "Add User Change" must still number the new change Entry001, i.e. the change-numbering
        // query must not count the Contributors entry.
        setup.gotoPage(releaseNote, "view",
            "action=useradd&template=ReleaseNotes.Code.Change.ChangeTemplate&product=" + product
                + "&version=1.0&audience=user");
        String currentUrl = setup.getDriver().getCurrentUrl();
        assertTrue(currentUrl.contains("/edit/ReleaseNotes/Data/" + product + "/1.0/Entry001/WebHome"),
            "The new change must be numbered Entry001 despite the Contributors entry, landed on: " + currentUrl);
        // The redirect must go through the "edit" action and the inline editor, and not through the deprecated
        // "inline" action, which only exists in the legacy module this test's wiki does not have: with that action
        // the URL still names Entry001 but resolves to a view of a missing page in a space named "inline".
        assertTrue(currentUrl.contains("editor=inline"),
            "The new change must be opened with the inline editor, landed on: " + currentUrl);
        assertFalse(setup.getDriver()
            .findElementsWithoutWaiting(By.cssSelector("select.releasenotes-screenshots-picker")).isEmpty(),
            "The redirect must land on the edit form of the new change, filled in from the change template.");
    }

    /**
     * The {@code >=} and {@code <=} version filters of the getChanges macro must include the boundary version itself,
     * unlike their strict {@code >} and {@code <} counterparts.
     */
    @Test
    @Order(2)
    void getChangesComparisonFiltersIncludeTheBoundaryVersion(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        createChange(setup, "1.0", "Change One");
        createChange(setup, "2.0", "Change Two");
        createChange(setup, "3.0", "Change Three");

        // ">=2.0" must return 2.0 and 3.0, i.e. the boundary version is part of the result.
        String greaterOrEqual = renderFilter(setup, "GreaterOrEqual", ">=2.0");
        assertFalse(greaterOrEqual.contains("Change One"), "\">=2.0\" must exclude 1.0, got: " + greaterOrEqual);
        assertTrue(greaterOrEqual.contains("Change Two"), "\">=2.0\" must include 2.0, got: " + greaterOrEqual);
        assertTrue(greaterOrEqual.contains("Change Three"), "\">=2.0\" must include 3.0, got: " + greaterOrEqual);

        // "<=2.0" must return 1.0 and 2.0, i.e. the boundary version is part of the result.
        String lowerOrEqual = renderFilter(setup, "LowerOrEqual", "<=2.0");
        assertTrue(lowerOrEqual.contains("Change One"), "\"<=2.0\" must include 1.0, got: " + lowerOrEqual);
        assertTrue(lowerOrEqual.contains("Change Two"), "\"<=2.0\" must include 2.0, got: " + lowerOrEqual);
        assertFalse(lowerOrEqual.contains("Change Three"), "\"<=2.0\" must exclude 3.0, got: " + lowerOrEqual);

        // The strict operators must still exclude the boundary version.
        String greater = renderFilter(setup, "Greater", ">2.0");
        assertFalse(greater.contains("Change Two"), "\">2.0\" must exclude 2.0, got: " + greater);
        assertTrue(greater.contains("Change Three"), "\">2.0\" must include 3.0, got: " + greater);

        String lower = renderFilter(setup, "Lower", "<2.0");
        assertTrue(lower.contains("Change One"), "\"<2.0\" must include 1.0, got: " + lower);
        assertFalse(lower.contains("Change Two"), "\"<2.0\" must exclude 2.0, got: " + lower);
    }

    /**
     * Creates a single user change of the {@code CmpProduct} product for the passed version.
     */
    private void createChange(TestUtils setup, String version, String title) throws Exception
    {
        DocumentReference entry = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", "CmpProduct", version, "Entry001"), "WebHome");
        setup.rest().delete(entry);
        setup.createPage(entry, "", title);
        setup.addObject(entry, "ReleaseNotes.Code.EntryClass",
            "product", "CmpProduct", "type", "Change", "version", version);
        setup.addObject(entry, "ReleaseNotes.Code.Change.ChangeClass",
            "title", title, "summary", title + " summary", "audience", "user", "importance", "1",
            "category", "development");
    }

    /**
     * Renders the {@code CmpProduct} changes matching the passed {@code versions} filter and returns the page content.
     */
    private String renderFilter(TestUtils setup, String pageName, String versions) throws Exception
    {
        DocumentReference reportPage =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "CmpProduct", pageName), "WebHome");
        setup.rest().delete(reportPage);
        setup.createPage(reportPage,
            String.format("{{getChanges products=\"CmpProduct\" versions=\"%s\" contextVariable=\"changeDocs\"/}}"
                + "\n\n{{displayChanges contextVariable=\"changeDocs\" displayer=\"simple\"/}}", versions),
            pageName);
        return setup.gotoPage(reportPage).getContent();
    }

    /**
     * Checks that the configuration is reachable from the wiki Administration (thanks to the ConfigurableClass
     * xobject), with its two fields, their hints and the right administration category.
     */
    @Test
    @Order(3)
    void configureFromAdministration(TestUtils setup)
    {
        setup.loginAsSuperAdmin();

        // The section displays the fields of the configuration xobject, bound to the configuration page.
        setup.gotoPage("XWiki", "XWikiPreferences", "admin", "section=releasenotes");
        WebElement product =
            setup.getDriver().findElement(By.name("ReleaseNotes.Code.ReleaseNotesConfigClass_0_product"));
        assertEquals("", product.getAttribute("value"),
            "No product name must be shipped: the administrator has to choose one.");
        WebElement template =
            setup.getDriver().findElement(By.name("ReleaseNotes.Code.ReleaseNotesConfigClass_0_template"));
        assertEquals("ReleaseNotes.Code.ReleaseNoteTemplate", template.getAttribute("value"),
            "Expected the default template reference to be displayed.");

        // Both fields explain what they are for.
        List<String> hints = setup.getDriver().findElementsWithoutWaiting(
                By.cssSelector("#admin-page-content .xHint")).stream()
            .map(WebElement::getText)
            .toList();
        assertEquals(2, hints.size(), "Expected a hint under each of the two configuration fields, got: " + hints);
        assertTrue(hints.stream().anyMatch(hint -> hint.startsWith("The product name pre-filled")),
            "Missing the hint for the product field, got: " + hints);
        assertTrue(hints.stream().anyMatch(hint -> hint.startsWith("The page whose title and content are copied")),
            "Missing the hint for the template field, got: " + hints);

        // The application is not bundled with XWiki Standard and thus its section belongs to the "Other" category.
        assertFalse(setup.getDriver()
            .findElementsWithoutWaiting(By.cssSelector("#panel-body-other a[data-id='releasenotes']")).isEmpty(),
            "The administration section must be registered in the \"Other\" category.");
    }

    /**
     * Creates a release note through the home page form and checks that the configured template is applied: its title,
     * its content and its required rights are copied over to the newly created page.
     */
    @Test
    @Order(4)
    void createReleaseNoteFromTemplate(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        DocumentReference releaseNote =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "TplProduct", "9.0"), "WebHome");
        setup.rest().delete(releaseNote);

        DocumentReference template =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "ReleaseNoteTemplate");

        // The template holds no release note xobject, so its title falls back to a name instead of displaying the
        // unresolved script that it hands to the release notes created from it.
        assertEquals("Release Note Template", setup.gotoPage(template).getDocumentTitle(),
            "The template page must not display raw Velocity as its title.");

        // Make the template require and enforce script right, the way a template holding scripts does.
        ObjectReference templateRight = new ObjectReference("XWiki.RequiredRightClass[0]", template);
        setup.addObject(template, "XWiki.RequiredRightClass", "level", "script");
        setEnforceRequiredRights(setup, template, true);

        try {
            // The creation form is a GET form passing the product and the version to the application home page.
            setup.gotoPage("ReleaseNotes", "WebHome", "view", "action=addReleaseNotes&product=TplProduct&version=9.0");

            ViewPage createdPage = setup.gotoPage(releaseNote);
            assertTrue(createdPage.getContent().contains("New and Noteworthy"),
                "The content of the template must have been copied to the created release note.");

            // The template hands its title over as written, so it resolves against the release note's own xobject.
            assertEquals("Release Notes for TplProduct 9.0", createdPage.getDocumentTitle(),
                "The title of the template must have been copied and evaluated on the created release note.");

            Page createdRestPage = setup.rest().get(releaseNote);
            assertEquals(Boolean.TRUE, createdRestPage.isEnforceRequiredRights(),
                "The created release note must enforce required rights, like the template does.");
            Property level = setup.rest().get(new ObjectPropertyReference("level",
                new ObjectReference("XWiki.RequiredRightClass[0]", releaseNote)));
            assertEquals("script", level.getValue(),
                "The required right of the template must have been copied to the created release note.");
        } finally {
            // Leave the template as the application ships it for the other tests.
            setup.rest().delete(releaseNote);
            setup.rest().delete(templateRight);
            setEnforceRequiredRights(setup, template, false);
        }
    }

    /**
     * Checks that the two Live Data instances of the application home page list their entries: the release notes one,
     * whose properties all come from ReleaseNoteClass, and the release changes one, whose product and version columns
     * are read from EntryClass instead of from ChangeClass.
     */
    @Test
    @Order(5)
    void homeListsReleaseNotesAndChanges(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        DocumentReference releaseNote =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "ListProduct", "3.0"), "WebHome");
        setup.rest().delete(releaseNote);
        setup.createPage(releaseNote, "", "RN 3.0");
        setup.addObject(releaseNote, "ReleaseNotes.Code.ReleaseNoteClass",
            "product", "ListProduct", "version", "3.0", "released", "1", "date", "");

        DocumentReference change = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", "ListProduct", "3.0", "Entry001"), "WebHome");
        setup.rest().delete(change);
        setup.createPage(change, "", "Listed Change");
        setup.addObject(change, "ReleaseNotes.Code.EntryClass",
            "product", "ListProduct", "type", "Change", "version", "3.0");
        setup.addObject(change, "ReleaseNotes.Code.Change.ChangeClass",
            "title", "Listed Change", "summary", "Listed change summary", "audience", "user", "importance", "2",
            "category", "development");

        setup.gotoPage(new DocumentReference("xwiki", List.of("ReleaseNotes", "Data"), "WebHome"));

        // The release notes list resolves its columns from ReleaseNoteClass.
        TableLayoutElement releaseNotes = new LiveDataElement("releasenotes").getTableLayout();
        releaseNotes.waitUntilReady();
        releaseNotes.filterColumn("Version", "3.0");
        releaseNotes.waitUntilRowCountEqualsTo(1);
        releaseNotes.assertRow("Product", "ListProduct");
        releaseNotes.assertRow("Version", "3.0");

        // The changes list reads product and version from EntryClass, which only works if the product_class and
        // version_class source parameters reach the results page.
        LiveDataElement changesLiveData = new LiveDataElement("releasenoteschanges");
        TableLayoutElement changes = changesLiveData.getTableLayout();
        changes.waitUntilReady();
        changes.filterColumn("Title", "Listed Change");
        changes.waitUntilRowCountEqualsTo(1);
        changes.assertRow("Product", "ListProduct");
        changes.assertRow("Version", "3.0");
        // The title is the column that links to the change, and it keeps that link now that the creation date, which
        // used to carry it, is not displayed. The link of a non terminal page is the URL of its space.
        changes.assertCellWithLink("Title", "Listed Change", setup.getURL(change.getLastSpaceReference()));

        // The displayed columns are only the ones that fit the width of a page: the creation date and the free text
        // summary are hidden, since together they make the table wider than the content area of a standard page.
        assertFalse(changes.hasColumn("Summary"), "Summary must not be one of the displayed columns.");
        assertFalse(changes.hasColumn("Created"), "Created must not be one of the displayed columns.");

        // The Live Data table layout scrolls sideways when its columns do not fit their container, and nothing
        // indicates it, so a column past the right edge is simply invisible. The displayed columns must fit.
        Long overflow = (Long) setup.getDriver().executeJavascript(
            "const wrapper = document.querySelector('#releasenoteschanges .layout-table-wrapper');"
                + "return wrapper.scrollWidth - wrapper.clientWidth;");
        assertEquals(0L, overflow, "The changes table must fit the width of the page.");

        // The two hidden columns are hidden, not dropped: the Properties panel offers exactly the properties the
        // macro declares, so they would be unreachable had they been left out of that list. They must be offered
        // there, unticked, and ticking one must display its column back.
        PropertiesPanelElement properties = PropertiesPanelElement.open(changesLiveData);
        assertTrue(properties.hasProperty("Summary"), "Summary must be offered by the properties panel.");
        assertTrue(properties.hasProperty("Created"), "Created must be offered by the properties panel.");
        assertFalse(properties.isPropertyDisplayed("Summary"), "Summary must be offered unticked.");
        assertFalse(properties.isPropertyDisplayed("Created"), "Created must be offered unticked.");
        assertTrue(properties.isPropertyDisplayed("Title"), "Title must be offered ticked.");

        properties.setPropertyDisplayed("Summary", true);
        properties.closePanel();
        assertTrue(changes.hasColumn("Summary"), "Ticking Summary must display its column.");
        changes.assertRow("Summary", "Listed change summary");
    }

    /**
     * The grid displayer renders each change as one card holding its title, then its media, then its summary, so that
     * a screenshot can only be read as illustrating the change it is enclosed with. A card holds a single medium, so
     * a change carrying several videos and no screenshot is displayed with its first video only: referencing more
     * videos cannot make one card taller than the card beside it.
     */
    @Test
    @Order(6)
    void gridDisplayerRendersEachChangeAsACard(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();
        createSharedChanges(setup);

        DocumentReference page =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", DISPLAY_PRODUCT), "WebHome");
        setup.rest().delete(page);
        setup.createPage(page,
            String.format("{{getChanges products=\"%s\" versions=\"1.0\" contextVariable=\"changeDocs\"/}}%n%n"
                + "{{displayChanges contextVariable=\"changeDocs\" displayer=\"grid\"/}}", DISPLAY_PRODUCT),
            "Grid page");
        setup.gotoPage(page);

        List<WebElement> cards = setup.getDriver().findElementsWithoutWaiting(By.cssSelector(".rn-change-card"));
        assertEquals(2, cards.size(), "Each change must be rendered as its own card.");

        // The card is the enclosure: a title and a medium belong to the same change because they are inside it, so
        // every card carries the title of its own change, above its own media.
        for (WebElement card : cards) {
            WebElement title = setup.getDriver().findElementWithoutWaiting(card, By.cssSelector(".rn-change-title"));
            WebElement media = setup.getDriver().findElementWithoutWaiting(card, By.cssSelector(".rn-change-media"));
            assertTrue(List.of("A grid change", "A videos change").contains(title.getText()),
                "A card must be titled after the change it displays, got: " + title.getText());
            assertTrue(title.getLocation().getY() < media.getLocation().getY(),
                "The media must be displayed after the title, in the card of: " + title.getText());
        }

        // Only one of the two changes carries videos, and its card displays the first of them alone.
        List<WebElement> videos =
            setup.getDriver().findElementsWithoutWaiting(By.cssSelector(".rn-change-media video"));
        assertEquals(1, videos.size(), "Only the first video of a change must be displayed.");
        assertTrue(videos.get(0).getAttribute("src").contains("video1.mp4"),
            "The displayed video must be the first one, got: " + videos.get(0).getAttribute("src"));
    }

    /**
     * The displayer name is turned into the name of the page that renders the changes, so only a plain name selects
     * a displayer; anything else falls back to the default one instead of taking part in the page name.
     */
    @Test
    @Order(7)
    void unknownDisplayerNameFallsBackToTheDefaultDisplayer(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();
        createSharedChanges(setup);

        // A name that is not a plain name: were it used as-is it would resolve outside the Code.Change space,
        // where the displayer pages of this macro live.
        String notAName = "Foo.Bar";
        DocumentReference page = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", DISPLAY_PRODUCT, "FallbackDisplayer"), "WebHome");
        setup.rest().delete(page);
        setup.createPage(page,
            String.format("{{getChanges products=\"%s\" versions=\"1.0\" contextVariable=\"changeDocs\"/}}%n%n"
                + "{{displayChanges contextVariable=\"changeDocs\" displayer=\"%s\"/}}", DISPLAY_PRODUCT, notAName),
            "Displayer page");

        ViewPage viewPage = setup.gotoPage(page);
        assertTrue(viewPage.getContent().contains("A grid change"),
            "The changes must still be rendered, by the default displayer.");
        assertFalse(viewPage.getContent().contains("ChangeDisplayerFoo"),
            "The displayer name must not contribute anything to the rendered page.");
    }

    /**
     * The report page forwards the filter parameters it knows about to the macros that build the report, and each
     * value stays inside the macro parameter it is given to.
     */
    @Test
    @Order(8)
    void reportForwardsOnlyItsOwnFilterParameters(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();
        // The report takes the product and the version from the request, so it needs no fixture of its own.
        createSharedChanges(setup);

        // "columns" is a displayChanges parameter, but not one the report form submits. It must not reach the
        // macros just because the request happens to carry it: the changes must keep the default layout.
        Map<String, String> queryParameters = new LinkedHashMap<>();
        queryParameters.put("action", "report");
        queryParameters.put("products", DISPLAY_PRODUCT);
        queryParameters.put("versions", "1.0");
        queryParameters.put("columns", "1");
        setup.gotoPage(new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "Report"), "view",
            queryParameters);

        ViewPage reportPage = new ViewPage();
        assertTrue(reportPage.getContent().contains("A grid change"),
            "The report must render the changes, otherwise the layout below proves nothing.");
        // The grid displayer publishes its column count to its stylesheet as a custom property, so the default of
        // 2 columns is what must be found there rather than the 1 carried by the request.
        WebElement grid = setup.getDriver().findElementWithoutWaiting(By.cssSelector(".rn-changes-grid"));
        assertTrue(grid.getAttribute("style").contains("--rn-changes-grid-columns: 2"),
            "The changes must keep the default column layout of the displayer, got: " + grid.getAttribute("style"));
    }

    /**
     * Creates, once for the whole class, the two changes shared by the tests that need changes only in order to
     * render them: one carrying a screenshot, and one carrying two videos and no screenshot, so that the same grid
     * holds both a card whose medium is a gallery and a card whose medium is a video. Called by each of those tests
     * rather than from a {@code @BeforeAll} method, since {@link TestUtils} is injected per test method and since
     * each of them must also be runnable on its own.
     */
    private void createSharedChanges(TestUtils setup) throws Exception
    {
        if (sharedChangesCreated) {
            return;
        }

        DocumentReference screenshotChange = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", DISPLAY_PRODUCT, "1.0", "Entry001"), "WebHome");
        setup.rest().delete(screenshotChange);
        setup.createPage(screenshotChange, "", "A grid change");
        setup.attachFile(screenshotChange, "screenshot.png", getClass().getResourceAsStream("/screenshot.png"), false);
        setup.addObject(screenshotChange, "ReleaseNotes.Code.EntryClass",
            "product", DISPLAY_PRODUCT, "type", "Change", "version", "1.0");
        setup.addObject(screenshotChange, "ReleaseNotes.Code.Change.ChangeClass",
            "title", "A grid change", "summary", "A grid change summary", "audience", "user", "importance", "1",
            "category", "development", "screenshots", "screenshot.png");

        DocumentReference videoChange = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", DISPLAY_PRODUCT, "1.0", "Entry002"), "WebHome");
        setup.rest().delete(videoChange);
        setup.createPage(videoChange, "", "A videos change");
        setup.attachFile(videoChange, "video1.mp4", new ByteArrayInputStream(new byte[] {1}), false);
        setup.attachFile(videoChange, "video2.mp4", new ByteArrayInputStream(new byte[] {2}), false);
        setup.addObject(videoChange, "ReleaseNotes.Code.EntryClass",
            "product", DISPLAY_PRODUCT, "type", "Change", "version", "1.0");
        setup.addObject(videoChange, "ReleaseNotes.Code.Change.ChangeClass",
            "title", "A videos change", "summary", "A videos change summary", "audience", "user", "importance", "1",
            "category", "development", "screenshots", "video1.mp4,video2.mp4");

        sharedChangesCreated = true;
    }

    /**
     * The Screenshots field used to be a plain text input in which the author had to type, by hand, the exact name of
     * an attachment uploaded beforehand from the Attachments tab. It is now an attachment picker: it suggests the
     * media attached to the change, accepts several of them, and can upload new ones without leaving the form. Since
     * the picker is a multiple SELECT while the property is a String (of which XWiki only keeps the first submitted
     * value), what actually gets saved is a hidden input that the application keeps in sync with the picker. This test
     * covers that round trip, plus the link out to the change page that the form now offers.
     */
    @Test
    @Order(9)
    void screenshotsAreEditedThroughAnAttachmentPicker(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        String product = "PickerProduct";
        DocumentReference entry = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", product, "1.0", "Entry001"), "WebHome");
        setup.rest().delete(entry);
        setup.createPage(entry, "", "An illustrated change");
        setup.addObject(entry, "ReleaseNotes.Code.EntryClass",
            "product", product, "type", "Change", "version", "1.0");
        setup.addObject(entry, "ReleaseNotes.Code.Change.ChangeClass",
            "title", "An illustrated change", "summary", "An illustrated change summary", "audience", "user",
            "importance", "1", "category", "development", "screenshots", "first.png");
        setup.attachFile(entry, "first.png", getClass().getResourceAsStream("/screenshot.png"), false);
        setup.attachFile(entry, "second.png", getClass().getResourceAsStream("/screenshot.png"), false);

        setup.gotoPage(entry, "edit", "editor=inline");

        // The form is reached from the release note and used to be a dead end towards the change's own page.
        assertFalse(setup.getDriver()
            .findElementsWithoutWaiting(By.cssSelector("a.releasenotes-view-change")).isEmpty(),
            "The edit form must offer a link to view the change page.");

        SuggestInputElement picker = new SuggestInputElement(setup.getDriver().findElementWithoutWaiting(
            By.cssSelector("select.releasenotes-screenshots-picker")));
        assertEquals(List.of("first.png"), picker.getValues(),
            "The media already stored must be preselected in the picker.");

        // The widget is clicked through Selenium actions, which need it inside the viewport. The picker itself is
        // hidden behind the widget, so scroll to the block holding both.
        setup.getDriver().scrollTo(setup.getDriver().findElementWithoutWaiting(
            By.cssSelector("span.releasenotes-screenshots")));
        // The attachments of the change are the suggestions offered without typing anything.
        picker.click().waitForNonTypedSuggestions().selectByValue("second.png");
        picker.hideSuggestions();

        // Save through the page object, which waits for the asynchronous save to complete: reading the saved value
        // straight after a click on the button races it, and reads back the value from before the save.
        new InlinePage().clickSaveAndView();

        Property screenshots = setup.rest().get(new ObjectPropertyReference("screenshots",
            new ObjectReference("ReleaseNotes.Code.Change.ChangeClass[0]", entry)));
        assertEquals("first.png,second.png", screenshots.getValue(),
            "The picker must save the comma separated list the release note displayers read back, not just its "
                + "first value.");

        // Reopening the form must show both media, which is what closes the loop: the value the picker saved is a
        // value the picker itself reads back.
        setup.gotoPage(entry, "edit", "editor=inline");
        SuggestInputElement reopened = new SuggestInputElement(setup.getDriver().findElementWithoutWaiting(
            By.cssSelector("select.releasenotes-screenshots-picker")));
        assertEquals(List.of("first.png", "second.png"), reopened.getValues());

        // The saved value must also still be understood by the displayers, which render the media of a change.
        setup.gotoPage(entry);
        assertFalse(setup.getDriver().findElementsWithoutWaiting(By.cssSelector("img[src*='first.png']")).isEmpty(),
            "The change page must display the screenshots the picker saved.");
    }

    /**
     * Turns the enforcement of required rights on or off for the passed page, which the REST API exposes as a page
     * field rather than as an xobject property.
     */
    private void setEnforceRequiredRights(TestUtils setup, DocumentReference reference, boolean enforce)
        throws Exception
    {
        Page page = setup.rest().get(reference);
        page.setEnforceRequiredRights(enforce);
        // The REST API fills Page#title with the *rendered* title and stores whatever it is given back as the raw
        // title, which would flatten a page whose title holds Velocity. A null title leaves the stored one alone.
        page.setTitle(null);
        setup.rest().save(page);
    }
}
