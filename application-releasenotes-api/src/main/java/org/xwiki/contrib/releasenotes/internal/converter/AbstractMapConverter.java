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
package org.xwiki.contrib.releasenotes.internal.converter;

import java.lang.reflect.Type;
import java.util.Map;

import jakarta.inject.Inject;

import org.xwiki.properties.BeanManager;
import org.xwiki.properties.PropertyException;
import org.xwiki.properties.converter.AbstractConverter;
import org.xwiki.properties.converter.ConversionException;

/**
 * Reads one of the beans of the application from a map of its properties, so that a script may write that bean as a
 * map literal and have it converted on the way in.
 *
 * @param <T> the bean this converter reads
 * @version $Id$
 * @since 2.7
 */
public abstract class AbstractMapConverter<T> extends AbstractConverter<T>
{
    @Inject
    private BeanManager beanManager;

    /**
     * @return a new bean, for the properties of the map to be set on
     */
    protected abstract T createBean();

    @Override
    protected <G extends T> G convertToType(Type targetType, Object value)
    {
        if (value == null) {
            return null;
        }

        if (!(value instanceof Map)) {
            throw new ConversionException(String.format(
                "A [%s] is written as a map of its properties, and got a value of type [%s].",
                ((Class<?>) targetType).getSimpleName(), value.getClass().getName()));
        }

        T bean = createBean();

        try {
            this.beanManager.populate(bean, (Map<String, ?>) value);
        } catch (PropertyException e) {
            throw new ConversionException(
                String.format("Failed to read a [%s] from [%s].", ((Class<?>) targetType).getSimpleName(), value), e);
        }

        return (G) bean;
    }
}
