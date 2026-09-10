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
 * Raised when the page asked about holds no release note, or no change: a page of the wiki that is not one of the
 * pages of the application, and a page that was never created, are the same thing to a caller reading or writing one.
 *
 * @version $Id$
 * @since 2.8
 */
@Unstable
public class ReleaseNotesNotFoundException extends ReleaseNotesException
{
    private static final long serialVersionUID = 1L;

    private final DocumentReference reference;

    /**
     * @param message what that page was expected to hold
     * @param reference the page that holds neither
     */
    public ReleaseNotesNotFoundException(String message, DocumentReference reference)
    {
        super(message);

        this.reference = reference;
    }

    /**
     * @return the page that holds neither a release note nor a change
     */
    public DocumentReference getReference()
    {
        return this.reference;
    }
}
