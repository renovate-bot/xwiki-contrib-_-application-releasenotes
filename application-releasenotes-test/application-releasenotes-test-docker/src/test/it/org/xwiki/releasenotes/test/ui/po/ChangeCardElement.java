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
import org.openqa.selenium.WebElement;
import org.xwiki.test.ui.po.BaseElement;

/**
 * One card of a {@link ChangesGridElement}, the enclosure the grid displayer renders a single change into.
 *
 * @version $Id$
 */
public class ChangeCardElement extends BaseElement
{
    private static final By TITLE = By.cssSelector(".rn-change-title");

    private static final By MEDIA = By.cssSelector(".rn-change-media");

    private final WebElement container;

    /**
     * @param container the card itself
     */
    public ChangeCardElement(WebElement container)
    {
        this.container = container;
    }

    /**
     * @return the title of the change the card displays
     */
    public String getTitle()
    {
        return getDriver().findElementWithoutWaiting(this.container, TITLE).getText();
    }

    /**
     * The card stacks its parts, so the medium illustrating the change starts lower down the page than the title
     * naming it. Comparing their positions is what tells the two apart from a card rendered in any other order.
     *
     * @return {@code true} when the medium of the card is displayed below its title, {@code false} otherwise
     */
    public boolean isMediaBelowTitle()
    {
        WebElement title = getDriver().findElementWithoutWaiting(this.container, TITLE);
        WebElement media = getDriver().findElementWithoutWaiting(this.container, MEDIA);
        return title.getLocation().getY() < media.getLocation().getY();
    }
}
