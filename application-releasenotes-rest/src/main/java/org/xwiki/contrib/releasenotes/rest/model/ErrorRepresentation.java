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
package org.xwiki.contrib.releasenotes.rest.model;

import org.xwiki.stability.Unstable;

/**
 * Why a request was refused, which every failing endpoint answers with.
 *
 * @version $Id$
 * @since 2.7
 */
@Unstable
public class ErrorRepresentation
{
    private String message;

    private String reference;

    /**
     * Creates an empty error, which the JSON reader of a client needs.
     */
    public ErrorRepresentation()
    {
        // The fields are set through their setters.
    }

    /**
     * @param message see {@link #getMessage()}
     * @param reference see {@link #getReference()}
     */
    public ErrorRepresentation(String message, String reference)
    {
        this.message = message;
        this.reference = reference;
    }

    /**
     * @return why the request was refused, in English: these messages are meant for whoever is calling the endpoint,
     *         and are not translated
     */
    public String getMessage()
    {
        return this.message;
    }

    /**
     * @param message see {@link #getMessage()}
     */
    public void setMessage(String message)
    {
        this.message = message;
    }

    /**
     * @return the page the refusal is about, or {@code null} when the refusal is about no page in particular. A
     *         release note that already exists is reported with the page it lives in, so that the client can add its
     *         changes to it rather than look it up.
     */
    public String getReference()
    {
        return this.reference;
    }

    /**
     * @param reference see {@link #getReference()}
     */
    public void setReference(String reference)
    {
        this.reference = reference;
    }
}
