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

import org.xwiki.livedata.test.po.LiveDataElement;
import org.xwiki.livedata.test.po.TableLayoutElement;

/**
 * Extends the table layout of a Live Data with the measurement of how far its table overflows the container that
 * scrolls it sideways.
 * <p>
 * This page object belongs to {@code xwiki-platform-livedata-test-pageobjects}, beside {@link TableLayoutElement}; it
 * lives here only until the platform version these tests build against carries it.
 *
 * @version $Id$
 */
public class ScrollableTableLayoutElement extends TableLayoutElement
{
    private final String liveDataId;

    /**
     * @param liveData the Live Data holding the table layout
     */
    public ScrollableTableLayoutElement(LiveDataElement liveData)
    {
        super(liveData);
        this.liveDataId = liveData.getId();
    }

    /**
     * The table layout scrolls sideways when its columns do not fit their container, and nothing indicates it, so a
     * column past the right edge is simply invisible. The width compared is the table's own against the visible width
     * of its wrapper, rather than the scroll extent of that wrapper: Live Data overhangs the last column header with
     * an absolutely positioned resize handle, which keeps that extent wider than the visible width by an amount that
     * depends on the browser and on the colour theme.
     *
     * @return the number of pixels by which the table is wider than the container that scrolls it, {@code 0} when the
     *         table fits that container
     */
    public long getHorizontalOverflow()
    {
        return (Long) getDriver().executeJavascript(
            "const wrapper = document.getElementById(arguments[0]).querySelector('.layout-table-wrapper');"
                + "const table = wrapper.querySelector('table');"
                + "return Math.max(0, Math.round(table.getBoundingClientRect().width - wrapper.clientWidth));",
            this.liveDataId);
    }
}
