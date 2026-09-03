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

import java.util.function.UnaryOperator;

import org.xwiki.script.service.ScriptService;

/**
 * A stand-in for {@code $services.rendering}, which a {@code PageTest} does not register, exposing just the
 * {@code escape} method the displayers call. The escaping itself is provided by a function so a test can pass the
 * identity (to leave a plain title untouched) or a function neutralizing wiki syntax (to check that a title is
 * routed through escaping before it is rendered).
 *
 * @version $Id$
 */
public class RenderingScriptServiceStub implements ScriptService
{
    private final UnaryOperator<String> escaper;

    /**
     * Leaves whatever it is asked to escape untouched.
     */
    public RenderingScriptServiceStub()
    {
        this(UnaryOperator.identity());
    }

    /**
     * @param escaper how to transform the content passed to {@link #escape}
     */
    public RenderingScriptServiceStub(UnaryOperator<String> escaper)
    {
        this.escaper = escaper;
    }

    /**
     * @param content the content to escape
     * @param syntaxId the identifier of the syntax the content is escaped for (ignored by this stand-in)
     * @return the content transformed by the configured function
     */
    public String escape(String content, String syntaxId)
    {
        return this.escaper.apply(content);
    }
}
