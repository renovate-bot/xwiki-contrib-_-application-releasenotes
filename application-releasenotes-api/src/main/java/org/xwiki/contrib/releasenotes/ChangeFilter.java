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

import java.util.Objects;

import org.xwiki.stability.Unstable;

/**
 * One condition a change search puts on one property of the changes, e.g. that their version is at least 9.0.
 * <p>
 * The filters of a property are combined with an {@code or}, so that several of them widen the search, whereas the
 * filters of different properties are combined with an {@code and}.
 *
 * @version $Id$
 * @since 2.7
 */
@Unstable
public class ChangeFilter
{
    /**
     * How the value of a filter is compared to the value a change holds.
     *
     * @since 2.7
     */
    @Unstable
    public enum Operator
    {
        /**
         * Matches the values the value of the filter is a pattern of, where {@code %} stands for any text. This is
         * the operator a filter written without one uses, so that a pattern such as {@code 8.3%} is what a search
         * accepts by default.
         */
        LIKE("like"),

        /** Matches the values equal to the value of the filter. */
        EQUALS("="),

        /** Matches the values lower than the value of the filter. */
        LT("<"),

        /** Matches the values lower than or equal to the value of the filter. */
        LTE("<="),

        /** Matches the values greater than the value of the filter. */
        GT(">"),

        /** Matches the values greater than or equal to the value of the filter. */
        GTE(">=");

        private final String syntax;

        Operator(String syntax)
        {
            this.syntax = syntax;
        }

        /**
         * @return how this operator is written, both in a filter a search is asked for and in the query that search
         *         is run as
         */
        public String getSyntax()
        {
            return this.syntax;
        }

        /**
         * @return whether this operator orders the values it compares, and therefore needs to know how the property
         *         it is applied to is ordered
         */
        public boolean isComparison()
        {
            return this != LIKE && this != EQUALS;
        }
    }

    private final Operator operator;

    private final String value;

    /**
     * @param operator how the value is compared to the value a change holds
     * @param value the value to compare to
     */
    public ChangeFilter(Operator operator, String value)
    {
        this.operator = operator;
        this.value = value;
    }

    /**
     * @return how the value is compared to the value a change holds
     */
    public Operator getOperator()
    {
        return this.operator;
    }

    /**
     * @return the value to compare to
     */
    public String getValue()
    {
        return this.value;
    }

    @Override
    public boolean equals(Object object)
    {
        if (this == object) {
            return true;
        }

        if (!(object instanceof ChangeFilter)) {
            return false;
        }

        ChangeFilter filter = (ChangeFilter) object;

        return this.operator == filter.operator && Objects.equals(this.value, filter.value);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.operator, this.value);
    }

    @Override
    public String toString()
    {
        return this.operator == Operator.LIKE ? this.value : this.operator.getSyntax() + this.value;
    }
}
