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

import org.xwiki.model.reference.DocumentReference;
import org.xwiki.stability.Unstable;

/**
 * Raised when the release note asked to be created is already there. The release note that exists is not returned
 * instead of being created, because it may have been written for another product than the one asked for, and its
 * changes would then be the changes of that other product.
 *
 * @version $Id$
 * @since 2.7
 */
@Unstable
public class ReleaseNoteAlreadyExistsException extends ReleaseNotesException
{
    private static final long serialVersionUID = 1L;

    private final DocumentReference releaseNoteReference;

    /**
     * @param releaseNoteReference the page of the release note that already exists
     */
    public ReleaseNoteAlreadyExistsException(DocumentReference releaseNoteReference)
    {
        super(String.format("The release note [%s] already exists.", releaseNoteReference));

        this.releaseNoteReference = releaseNoteReference;
    }

    /**
     * @return the page of the release note that already exists
     */
    public DocumentReference getReleaseNoteReference()
    {
        return this.releaseNoteReference;
    }
}
