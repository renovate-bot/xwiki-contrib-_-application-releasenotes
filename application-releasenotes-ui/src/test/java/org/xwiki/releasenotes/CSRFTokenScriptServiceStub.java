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
package org.xwiki.releasenotes;

import org.xwiki.script.service.ScriptService;

/**
 * A stand-in for {@code $services.csrf}, which a {@code PageTest} does not register, exposing just the members the
 * application pages use: the token that fills the hidden form field and the check that guards the state-changing
 * actions. A single token is treated as valid, so a test can submit it (a legitimate request) or anything else (a
 * forged one).
 *
 * @version $Id$
 */
public class CSRFTokenScriptServiceStub implements ScriptService
{
    private final String validToken;

    /**
     * @param validToken the one token this stand-in considers valid
     */
    public CSRFTokenScriptServiceStub(String validToken)
    {
        this.validToken = validToken;
    }

    /**
     * @return the token that a form must carry back for its request to be honoured
     */
    public String getToken()
    {
        return this.validToken;
    }

    /**
     * @param token the token submitted by a request
     * @return whether it matches the one valid token
     */
    public boolean isTokenValid(String token)
    {
        return this.validToken.equals(token);
    }

    /**
     * @return where a request carrying no valid token would be sent to be confirmed
     */
    public String getResubmissionURL()
    {
        return "/xwiki/bin/view/XWiki/Resubmit";
    }
}
