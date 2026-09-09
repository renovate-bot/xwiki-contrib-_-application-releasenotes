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
package org.xwiki.contrib.releasenotes;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.xwiki.contrib.releasenotes.ChangeFilter.Operator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ChangeFilter}.
 *
 * @version $Id$
 */
class ChangeFilterTest
{
    /**
     * A filter is a value: two filters asking for the same thing are the same filter, which is what lets a caller
     * compare the query it built to the one it meant to build.
     */
    @Test
    void twoFiltersAskingForTheSameThingAreEqual()
    {
        ChangeFilter filter = new ChangeFilter(Operator.GTE, "9.0");

        assertEquals(new ChangeFilter(Operator.GTE, "9.0"), filter);
        assertEquals(new ChangeFilter(Operator.GTE, "9.0").hashCode(), filter.hashCode());
        assertEquals(filter, filter);
        assertNotEquals(new ChangeFilter(Operator.GT, "9.0"), filter);
        assertNotEquals(new ChangeFilter(Operator.GTE, "10.0"), filter);
        assertNotEquals("9.0", filter);
        assertTrue(List.of(filter).contains(new ChangeFilter(Operator.GTE, "9.0")));
    }

    /**
     * A filter is written the way a caller writes it, so that a filter appearing in a message says what was asked
     * for.
     */
    @Test
    void aFilterIsWrittenTheWayItIsAskedFor()
    {
        assertEquals(">=9.0", new ChangeFilter(Operator.GTE, "9.0").toString());
        assertEquals("8.3%", new ChangeFilter(Operator.LIKE, "8.3%").toString());
    }
}
