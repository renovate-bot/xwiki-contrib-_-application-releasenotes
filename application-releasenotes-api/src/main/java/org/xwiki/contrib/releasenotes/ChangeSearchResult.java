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

import org.xwiki.model.reference.DocumentReference;
import org.xwiki.stability.Unstable;

/**
 * One page of the changes a search matched, and whether the search matched more of them.
 * <p>
 * The changes are given both as references and as the names those references serialize to, because the pages
 * displaying them work with names: they compare a change to the exclusions their caller wrote, and a reference would
 * silently match none of them.
 *
 * @version $Id$
 * @since 2.7
 */
@Unstable
public class ChangeSearchResult
{
    private final List<String> changeNames;

    private final List<DocumentReference> changes;

    private final boolean hasMore;

    /**
     * @param changeNames the pages of the changes of this page of the result, as names
     * @param changes the same pages, as references
     * @param hasMore whether the search matched changes beyond this page
     */
    public ChangeSearchResult(List<String> changeNames, List<DocumentReference> changes, boolean hasMore)
    {
        this.changeNames = changeNames;
        this.changes = changes;
        this.hasMore = hasMore;
    }

    /**
     * @return the pages of the changes of this page of the result
     */
    public List<DocumentReference> getChanges()
    {
        return this.changes;
    }

    /**
     * @return the names of those pages, in the same order. The list is the caller's to modify: the pages displaying
     *         the changes of a release note take their own exclusions out of it.
     */
    public List<String> getChangeNames()
    {
        return this.changeNames;
    }

    /**
     * @return whether the search matched changes beyond this page, which is what tells a caller displaying one page
     *         at a time that a next one exists
     */
    public boolean hasMore()
    {
        return this.hasMore;
    }
}
