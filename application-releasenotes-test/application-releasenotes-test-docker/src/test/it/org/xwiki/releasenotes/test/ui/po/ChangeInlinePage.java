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
package org.xwiki.releasenotes.test.ui.po;

import org.openqa.selenium.By;
import org.xwiki.test.ui.po.InlinePage;
import org.xwiki.test.ui.po.SuggestInputElement;

/**
 * The inline edit form of a change, the page the "Add ... Change" forms of a release note open.
 *
 * @version $Id$
 */
public class ChangeInlinePage extends InlinePage
{
    private static final By VIEW_CHANGE_LINK = By.cssSelector("a.releasenotes-view-change");

    private static final By SCREENSHOTS_BLOCK = By.cssSelector("span.releasenotes-screenshots");

    private static final By SCREENSHOTS_PICKER = By.cssSelector("select.releasenotes-screenshots-picker");

    /**
     * @return {@code true} when the form offers the link towards the change's own page, {@code false} otherwise
     */
    public boolean hasViewChangeLink()
    {
        return !getDriver().findElementsWithoutWaiting(VIEW_CHANGE_LINK).isEmpty();
    }

    /**
     * @return {@code true} when the form offers the attachment picker editing the screenshots, {@code false}
     *         otherwise
     */
    public boolean hasScreenshotsPicker()
    {
        return !getDriver().findElementsWithoutWaiting(SCREENSHOTS_PICKER).isEmpty();
    }

    /**
     * @return the attachment picker editing the screenshots of the change
     */
    public SuggestInputElement getScreenshotsPicker()
    {
        return new SuggestInputElement(getDriver().findElementWithoutWaiting(SCREENSHOTS_PICKER));
    }

    /**
     * Opens the suggestions the screenshots picker offers without any text typed, which are the media attached to the
     * change.
     * <p>
     * Opening them sometimes needs more than one click. The form is static by the time it is clicked, yet the pointer
     * event the click sends can leave the widget without the focus, and an unfocused widget never loads any
     * suggestion, so waiting for them then times out. Clicking a widget whose dropdown is already open would close it
     * again, hence the condition on every attempt.
     *
     * @return the picker, with its suggestions loaded
     */
    public SuggestInputElement openScreenshotSuggestions()
    {
        SuggestInputElement picker = getScreenshotsPicker();
        // The widget is clicked through Selenium actions, which need it inside the viewport. The picker itself is
        // hidden behind the widget, so scroll to the block holding both.
        getDriver().scrollTo(getDriver().findElementWithoutWaiting(SCREENSHOTS_BLOCK));
        getDriver().waitUntilCondition(driver -> {
            if (!picker.isDropDownOpened()) {
                picker.click();
            }
            return picker.isDropDownOpened();
        });
        return picker.waitForNonTypedSuggestions();
    }
}
