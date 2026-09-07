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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.test.ui.po.BaseElement;

/**
 * The grid of changes the {@code grid} displayer renders, one card per change.
 *
 * @version $Id$
 */
public class ChangesGridElement extends BaseElement
{
    private static final By GRID = By.cssSelector(".rn-changes-grid");

    private static final By CARD = By.cssSelector(".rn-change-card");

    private static final By MEDIA_VIDEO = By.cssSelector(".rn-change-media video");

    private static final Pattern COLUMNS = Pattern.compile("--rn-changes-grid-columns:\\s*(\\d+)");

    /**
     * @return one page object per card of the grid, in the order the grid displays them
     */
    public List<ChangeCardElement> getCards()
    {
        return getDriver().findElementsWithoutWaiting(CARD).stream().map(ChangeCardElement::new).toList();
    }

    /**
     * @return the {@code src} of every video the cards of the grid display, in the order they are displayed
     */
    public List<String> getVideoSources()
    {
        return getDriver().findElementsWithoutWaiting(MEDIA_VIDEO).stream()
            .map(video -> video.getAttribute("src"))
            .toList();
    }

    /**
     * The layout of the grid is defined in the stylesheet of the displayer, so the displayer publishes the number of
     * columns it lays the cards out in to that stylesheet, as a custom property carried by the grid itself.
     *
     * @return the number of columns the grid is laid out in
     */
    public int getColumnCount()
    {
        WebElement grid = getDriver().findElementWithoutWaiting(GRID);
        String style = grid.getAttribute("style");
        Matcher matcher = COLUMNS.matcher(style);
        if (!matcher.find()) {
            throw new IllegalStateException(
                String.format("The grid declares no column count, its style is [%s].", style));
        }
        return Integer.parseInt(matcher.group(1));
    }
}
