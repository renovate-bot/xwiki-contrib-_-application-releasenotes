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
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
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
     * Builds a release note page (with the contributors macro) plus, optionally, its Contributors child entry, then
     * asserts the macro shows a warning when the list is absent and renders the names when it is present.
     */
    @Test
    @Order(1)
    void contributorsList(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        DocumentReference releaseNote =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "TestProduct", "1.0"), "WebHome");
        setup.rest().delete(releaseNote);
        setup.createPage(releaseNote, "= Credits =\n\n{{releasenotecontributors/}}", "RN 1.0");
        setup.addObject(releaseNote, "ReleaseNotes.Code.ReleaseNoteClass", "product", "TestProduct", "version", "1.0");

        // Before any contributors list exists, the macro shows the warning.
        ViewPage beforePage = setup.gotoPage(releaseNote);
        assertTrue(beforePage.getContent().contains("The list of contributors has not been generated yet."),
            "Expected the not-generated-yet warning before the contributors list exists.");

        // Create the deterministic Contributors child entry with two names.
        DocumentReference contributorsEntry = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", "TestProduct", "1.0", "Contributors"), "WebHome");
        setup.rest().delete(contributorsEntry);
        setup.createPage(contributorsEntry, "", "Contributors");
        setup.addObject(contributorsEntry, "ReleaseNotes.Code.EntryClass",
            "product", "TestProduct", "type", "Contributors", "version", "1.0");
        // Store the names unsorted and with mixed case to exercise the case-insensitive alphabetical ordering.
        setup.addObject(contributorsEntry, "ReleaseNotes.Code.ContributorsClass",
            "contributors", "bob jones\nAlice Smith\nCarol Nguyen");

        // Now the macro renders the names, sorted alphabetically ignoring case, and drops the warning.
        ViewPage afterPage = setup.gotoPage(releaseNote);
        String content = afterPage.getContent();
        assertTrue(content.contains("Alice Smith"), "Expected the first contributor to be rendered.");
        assertTrue(content.contains("bob jones"), "Expected the second contributor to be rendered.");
        assertTrue(content.contains("Carol Nguyen"), "Expected the third contributor to be rendered.");
        assertTrue(content.indexOf("Alice Smith") < content.indexOf("bob jones")
            && content.indexOf("bob jones") < content.indexOf("Carol Nguyen"),
            "Contributors must be sorted alphabetically ignoring case, got: " + content);
        assertFalse(content.contains("has not been generated yet"),
            "Warning must disappear once the contributors list exists.");
    }

    /**
     * Exercises the aligned edit UX: the macro shows an "Add contributors" button when the list is absent, the button
     * opens the separate Contributors page in inline edit mode, and saving there renders the names back on the release
     * note and drops the warning.
     */
    @Test
    @Order(2)
    void contributorsListEditFlow(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        DocumentReference releaseNote =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "EditProduct", "1.0"), "WebHome");
        setup.rest().delete(releaseNote);
        DocumentReference contributorsEntry = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", "EditProduct", "1.0", "Contributors"), "WebHome");
        setup.rest().delete(contributorsEntry);

        setup.createPage(releaseNote, "= Credits =\n\n{{releasenotecontributors/}}", "RN Edit 1.0");
        setup.addObject(releaseNote, "ReleaseNotes.Code.ReleaseNoteClass",
            "product", "EditProduct", "version", "1.0");

        // Absent: warning is shown and the "Add contributors" button is available to an editor.
        ViewPage beforePage = setup.gotoPage(releaseNote);
        assertTrue(beforePage.getContent().contains("The list of contributors has not been generated yet."),
            "Expected the not-generated-yet warning before the contributors list exists.");
        WebElement addButton = setup.getDriver().findElementWithoutWaiting(
            By.cssSelector("input.button[value='Add contributors']"));

        // Click "Add contributors": lands on the Contributors page in inline edit mode.
        addButton.click();
        WebElement textarea = setup.getDriver().findElement(By.cssSelector("textarea"));
        textarea.clear();
        textarea.sendKeys("Alice Smith\nBob Jones");
        setup.getDriver().findElement(By.cssSelector("input[name='action_save']")).click();

        // Back on the release note: both names render and the warning is gone.
        ViewPage afterPage = setup.gotoPage(releaseNote);
        String content = afterPage.getContent();
        assertTrue(content.contains("Alice Smith"), "Expected the first contributor to be rendered.");
        assertTrue(content.contains("Bob Jones"), "Expected the second contributor to be rendered.");
        assertFalse(content.contains("The list of contributors has not been generated yet."),
            "Warning must disappear once the contributors list has been saved.");

        // The Contributors page created from the macro is a technical child page: it must be hidden.
        Page savedEntry = setup.rest().get(contributorsEntry);
        assertTrue(savedEntry.isHidden(), "The Contributors child page must be created as a hidden page.");
    }

    /**
     * A contributor name containing wiki syntax must be rendered literally, not interpreted (injection guard for the
     * $services.rendering.escape call in the macro).
     */
    @Test
    @Order(3)
    void contributorsListEscapesWikiSyntax(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        DocumentReference releaseNote =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "EscProduct", "1.0"), "WebHome");
        setup.rest().delete(releaseNote);
        setup.createPage(releaseNote, "{{releasenotecontributors/}}", "RN Esc 1.0");
        setup.addObject(releaseNote, "ReleaseNotes.Code.ReleaseNoteClass", "product", "EscProduct", "version", "1.0");

        DocumentReference contributorsEntry = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", "EscProduct", "1.0", "Contributors"), "WebHome");
        setup.rest().delete(contributorsEntry);
        setup.createPage(contributorsEntry, "", "Contributors");
        setup.addObject(contributorsEntry, "ReleaseNotes.Code.EntryClass",
            "product", "EscProduct", "type", "Contributors", "version", "1.0");
        // A name carrying bold wiki syntax: if escaped, the asterisks survive in the rendered text; if interpreted,
        // the text would render as bold and the asterisks would be gone.
        setup.addObject(contributorsEntry, "ReleaseNotes.Code.ContributorsClass",
            "contributors", "**Robert Tables**");

        String content = setup.gotoPage(releaseNote).getContent();
        assertTrue(content.contains("**Robert Tables**"),
            "Wiki syntax in a contributor name must be escaped and rendered literally, got: " + content);
    }

    /**
     * Adding the first change to a version that already has a Contributors entry must still number the new change
     * Entry001 (regression guard: the Contributors entry must not be counted by the change-numbering query).
     */
    @Test
    @Order(4)
    void changeNumberingIgnoresContributors(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        DocumentReference releaseNote =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "NumProduct", "1.0"), "WebHome");
        setup.rest().delete(releaseNote);
        setup.createPage(releaseNote, "{{releasenotechanges/}}", "RN Num 1.0");
        setup.addObject(releaseNote, "ReleaseNotes.Code.ReleaseNoteClass",
            "product", "NumProduct", "version", "1.0", "released", "0");

        // A Contributors entry exists in the version space, but no change entry does yet.
        DocumentReference contributorsEntry = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", "NumProduct", "1.0", "Contributors"), "WebHome");
        setup.rest().delete(contributorsEntry);
        setup.createPage(contributorsEntry, "", "Contributors");
        setup.addObject(contributorsEntry, "ReleaseNotes.Code.EntryClass",
            "product", "NumProduct", "type", "Contributors", "version", "1.0");

        // Trigger "Add User Change": handleAddAction computes the next Entry name and redirects to its inline editor.
        setup.gotoPage(releaseNote, "view",
            "action=useradd&template=ReleaseNotes.Code.Change.ChangeTemplate&product=NumProduct&version=1.0"
                + "&audience=user");
        String currentUrl = setup.getDriver().getCurrentUrl();
        assertTrue(currentUrl.contains("Entry001"),
            "The new change must be numbered Entry001 despite the Contributors entry, landed on: " + currentUrl);
    }

    /**
     * The {@code >=} and {@code <=} version filters of the getChanges macro must include the boundary version itself,
     * unlike their strict {@code >} and {@code <} counterparts.
     */
    @Test
    @Order(5)
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
    @Order(6)
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
    @Order(7)
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
    @Order(8)
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
        TableLayoutElement changes = new LiveDataElement("releasenoteschanges").getTableLayout();
        changes.waitUntilReady();
        changes.filterColumn("Title", "Listed Change");
        changes.waitUntilRowCountEqualsTo(1);
        changes.assertRow("Product", "ListProduct");
        changes.assertRow("Version", "3.0");
        // The title is the column that links to the change, and it keeps that link now that the creation date, which
        // used to carry it, is not displayed. The link of a non terminal page is the URL of its space.
        changes.assertCellWithLink("Title", "Listed Change", setup.getURL(change.getLastSpaceReference()));

        // The default columns are only the ones that fit the width of a page: the creation date and the free text
        // summary are left out, since together they make the table wider than the content area of a standard page.
        assertFalse(changes.hasColumn("Summary"), "Summary must not be one of the default columns.");
        assertFalse(changes.hasColumn("Created"), "Created must not be one of the default columns.");

        // The Live Data table layout scrolls sideways when its columns do not fit their container, and nothing
        // indicates it, so a column past the right edge is simply invisible. The default columns must fit.
        Long overflow = (Long) setup.getDriver().executeJavascript(
            "const wrapper = document.querySelector('#releasenoteschanges .layout-table-wrapper');"
                + "return wrapper.scrollWidth - wrapper.clientWidth;");
        assertEquals(0L, overflow, "The changes table must fit the width of the page.");
    }

    /**
     * The displayer name is turned into the name of the page that renders the changes, so only a plain name selects
     * a displayer; anything else falls back to the default one instead of taking part in the page name.
     */
    @Test
    @Order(9)
    void unknownDisplayerNameFallsBackToTheDefaultDisplayer(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        String product = "DisplayerProduct";
        DocumentReference entry = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", product, "1.0", "Entry001"), "WebHome");
        setup.rest().delete(entry);
        setup.createPage(entry, "", "A displayed change");
        setup.addObject(entry, "ReleaseNotes.Code.EntryClass",
            "product", product, "type", "Change", "version", "1.0");
        setup.addObject(entry, "ReleaseNotes.Code.Change.ChangeClass",
            "title", "A displayed change", "summary", "A displayed change summary", "audience", "user",
            "importance", "1", "category", "development");

        // A name that is not a plain name: were it used as-is it would resolve outside the Code.Change space,
        // where the displayer pages of this macro live.
        String notAName = "Foo.Bar";
        DocumentReference page = new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", product), "WebHome");
        setup.rest().delete(page);
        setup.createPage(page,
            String.format("{{getChanges products=\"%s\" versions=\"1.0\" contextVariable=\"changeDocs\"/}}%n%n"
                + "{{displayChanges contextVariable=\"changeDocs\" displayer=\"%s\"/}}", product, notAName),
            "Displayer page");

        ViewPage viewPage = setup.gotoPage(page);
        assertTrue(viewPage.getContent().contains("A displayed change"),
            "The changes must still be rendered, by the default displayer.");
        assertFalse(viewPage.getContent().contains("ChangeDisplayerFoo"),
            "The displayer name must not contribute anything to the rendered page.");
    }

    /**
     * The report page forwards the filter parameters it knows about to the macros that build the report, and each
     * value stays inside the macro parameter it is given to.
     */
    @Test
    @Order(10)
    void reportForwardsOnlyItsOwnFilterParameters(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        String product = "ReportProduct";
        DocumentReference entry = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", product, "1.0", "Entry001"), "WebHome");
        setup.rest().delete(entry);
        setup.createPage(entry, "", "A reported change");
        setup.addObject(entry, "ReleaseNotes.Code.EntryClass",
            "product", product, "type", "Change", "version", "1.0");
        setup.addObject(entry, "ReleaseNotes.Code.Change.ChangeClass",
            "title", "A reported change", "summary", "A reported change summary", "audience", "user",
            "importance", "1", "category", "development");

        // "columns" is a displayChanges parameter, but not one the report form submits. It must not reach the
        // macros just because the request happens to carry it: the changes must keep the default layout.
        Map<String, String> queryParameters = new LinkedHashMap<>();
        queryParameters.put("action", "report");
        queryParameters.put("products", product);
        queryParameters.put("versions", "1.0");
        queryParameters.put("columns", "1");
        setup.gotoPage(new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "Report"), "view",
            queryParameters);

        ViewPage reportPage = new ViewPage();
        assertTrue(reportPage.getContent().contains("A reported change"),
            "The report must render the change, otherwise the layout below proves nothing.");
        // The grid displayer publishes its column count to its stylesheet as a custom property, so the default of
        // 2 columns is what must be found there rather than the 1 carried by the request.
        WebElement grid = setup.getDriver().findElementWithoutWaiting(By.cssSelector(".rn-changes-grid"));
        assertTrue(grid.getAttribute("style").contains("--rn-changes-grid-columns: 2"),
            "The changes must keep the default column layout of the displayer, got: " + grid.getAttribute("style"));
    }

    /**
     * The grid displayer renders each change as one card holding its title, then its media, then its summary, so that
     * a screenshot can only be read as illustrating the change it is enclosed with. A card holds a single medium, so
     * a change carrying several videos and no screenshot is displayed with its first video only: referencing more
     * videos cannot make one card taller than the card beside it.
     */
    @Test
    @Order(11)
    void gridDisplayerRendersEachChangeAsACard(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        String product = "GridProduct";
        DocumentReference screenshotChange = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", product, "1.0", "Entry001"), "WebHome");
        setup.rest().delete(screenshotChange);
        setup.createPage(screenshotChange, "", "A grid change");
        setup.attachFile(screenshotChange, "screenshot.png", getClass().getResourceAsStream("/screenshot.png"), false);
        setup.addObject(screenshotChange, "ReleaseNotes.Code.EntryClass",
            "product", product, "type", "Change", "version", "1.0");
        setup.addObject(screenshotChange, "ReleaseNotes.Code.Change.ChangeClass",
            "title", "A grid change", "summary", "A grid change summary", "audience", "user", "importance", "1",
            "category", "development", "screenshots", "screenshot.png");

        // A second change, carrying two videos and no screenshot, so that the same grid holds both a card whose
        // medium is a gallery and a card whose medium is a video.
        DocumentReference videoChange = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", product, "1.0", "Entry002"), "WebHome");
        setup.rest().delete(videoChange);
        setup.createPage(videoChange, "", "A videos change");
        setup.attachFile(videoChange, "video1.mp4", new ByteArrayInputStream(new byte[] {1}), false);
        setup.attachFile(videoChange, "video2.mp4", new ByteArrayInputStream(new byte[] {2}), false);
        setup.addObject(videoChange, "ReleaseNotes.Code.EntryClass",
            "product", product, "type", "Change", "version", "1.0");
        setup.addObject(videoChange, "ReleaseNotes.Code.Change.ChangeClass",
            "title", "A videos change", "summary", "A videos change summary", "audience", "user", "importance", "1",
            "category", "development", "screenshots", "video1.mp4,video2.mp4");

        DocumentReference page = new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", product), "WebHome");
        setup.rest().delete(page);
        setup.createPage(page,
            String.format("{{getChanges products=\"%s\" versions=\"1.0\" contextVariable=\"changeDocs\"/}}%n%n"
                + "{{displayChanges contextVariable=\"changeDocs\" displayer=\"grid\"/}}", product),
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
