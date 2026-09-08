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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link Audience}.
 * <p>
 * The values asserted here are the values the {@code audience} property of a change holds, which the queries of the
 * application filter its sections on: changing one of them hides every change already written.
 *
 * @version $Id$
 */
class AudienceTest
{
    @Test
    void everyAudienceIsStoredUnderItsOwnName()
    {
        assertEquals("user", Audience.USER.getStoredValue());
        assertEquals("administrator", Audience.ADMINISTRATOR.getStoredValue());
        assertEquals("developer", Audience.DEVELOPER.getStoredValue());
    }

    @Test
    void everyStoredValueIsReadBackAsItsAudience()
    {
        for (Audience audience : Audience.values()) {
            assertEquals(audience, Audience.fromStoredValue(audience.getStoredValue()));
        }
    }

    /**
     * A change whose audience is empty, or is a value the application does not know, is read back with no audience
     * rather than with a wrong one.
     */
    @Test
    void aValueThatIsNoAudienceIsReadBackAsNone()
    {
        assertNull(Audience.fromStoredValue(""));
        assertNull(Audience.fromStoredValue(null));
        assertNull(Audience.fromStoredValue("everyone"));
    }
}
