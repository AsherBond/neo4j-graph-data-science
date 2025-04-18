/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gds.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Configuration {

    /**
     * Name of the generated class.
     * <p>
     * If not manually set, the value is set to the
     * annotation class name with an "Impl" suffix:
     *
     * <pre>
     * &#64;Configuration
     * interface Foo { }
     *
     * &#64;Generated
     * public class FooImpl { }
     * </pre>
     *
     */
    String value() default "";

    boolean generateBuilder() default true;

    /**
     * By default, a configuration field is resolved in the {@link org.neo4j.gds.core.CypherMapWrapper} parameter with the method name as the expected key.
     * This annotation changes the key to look up to use {@link org.neo4j.gds.annotation.Configuration.Key#value()} instead.
     */
    @Documented
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Key {
        String value();
    }

    /**
     * This annotation can be used together with {@link org.neo4j.gds.annotation.Configuration.Key} or {@link org.neo4j.gds.annotation.Configuration.Parameter}.
     * The value must be a method reference of format `package.class#function` to a static and public method
     * or a method name, referring to a public static or instance method in the surrounding interface or its supertypes.
     * The input for the specific field will be transformed using the method-reference.
     */
    @Documented
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface ConvertWith {
        /**
         * The method to be used for converting the configuration value.
         * <p>
         * This can have one of two forms:
         * <ul>
         * <li>A method reference of format `package.class#function` to a static and public method.</li>
         * <li>An unqualified method name, referring to a public static or instance method in the surrounding interface or its supertypes.</li>
         * </ul>
         * <p>
         * In the second case, the method can be an instance method, which allows for the conversion to make use of
         * other configuration values.
         * <b>FOOT-GUN WARNING:</b> Using other configuration values only works correctly if they are already initialized.
         * That is, they must be written above this configuration option, in source code order.
         */
        String method();

        String INVERSE_IS_TO_MAP = "__USE_TO_MAP_METHOD__";

        // necessary if the ConvertWithMethod does not accept an already parsed value
        String inverse() default "";
    }

    /**
     * This annotation can be used together with {@link org.neo4j.gds.annotation.Configuration.Key} or {@link org.neo4j.gds.annotation.Configuration.Parameter}.
     * The value must be a method reference of format `package.class#function` to a static and public method.
     * The value of the specific field will be transformed using the method-reference and used for the implementation of the method annotated with {@link org.neo4j.gds.annotation.Configuration.ToMap}.
     */
    @Documented
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface ToMapValue {
        String value();
    }

    /**
     * Used to specify which interface methods to ignore by the ConfigurationProcessor.
     */
    @Documented
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Ignore {
    }

    /**
     * Configuration field is expected to be passed to the constructor as a parameter.
     */
    @Documented
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Parameter {
        boolean acceptNull() default false;
    }

    /**
     * Annotated function will return the list of configuration keys.
     * The return type of the method must be of type Collection&lt;String&gt;.
     */
    @Documented
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface CollectKeys {
    }

    /**
     * Annotated function will return the map representation of the configuration.
     * The return type of the method must be of type Map&lt;String, Object&gt;.
     *
     * By default, each field will be directly put into the returned map.
     * If {@link org.neo4j.gds.annotation.Configuration.ToMapValue} is defined, the given method will be applied before.
     */
    @Documented
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface ToMap {
    }

    /**
     * The annotated method will be used to insert the implementation of validating a given graphStore.
     * The implementation calls each method annotated with {@link GraphStoreValidationCheck}.
     * <p>
     * The method cannot be abstract but should have an empty body, and have exactly three parameter graphStore, selectedLabels, selectedRelationshipTypes.
     */
    @Documented
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface GraphStoreValidation {
    }

    /**
     * The annotated method will be used to insert the implementation of {@link org.neo4j.gds.annotation.Configuration.GraphStoreValidation} to verify the configuration is valid for the given graphStore.
     * <p>
     * The method cannot be abstract and must have exactly three parameters (graphStore, selectedLabels, selectedRelationshipTypes).
     * The method is expected to throw an exception if the check failed.
     */
    @Documented
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface GraphStoreValidationCheck {
    }

    /**
     * The annotated method will be invoked internally to validate invariants after instance has been created,
     * but before returned to a client. The method must
     * <ul>
     * <li>be parameter-less</li>
     * <li>be non-private</li>
     * <li>have a {@code void} return type</li>
     * <li>not throw a checked exception</li>
     * </ul>
     * <p>
     * Can also be used to compute a normalized variant by specifying a non-void return type.
     * This is probably not what you want.
     * <p>
     * See {@code org.immutables.value.Value.Check}.
     */
    @Documented
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Check {
    }

    /**
     * Input for the annotated configuration field storing an Integer, will be validated if it is in the given range.
     */
    @Documented
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface IntegerRange {
        int min() default Integer.MIN_VALUE;
        int max() default Integer.MAX_VALUE;
        boolean minInclusive() default true;
        boolean maxInclusive() default true;
    }

    /**
     * Input for the annotated configuration field storing a Long, will be validated if it is in the given range
     */
    @Documented
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface LongRange {
        long min() default Long.MIN_VALUE;
        long max() default Long.MAX_VALUE;
        boolean minInclusive() default true;
        boolean maxInclusive() default true;
    }

    /**
     * Input for the annotated configuration field storing a Double, will be validated if it is in the given range
     */
    @Documented
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface DoubleRange {
        double min() default -Double.MAX_VALUE;
        double max() default Double.MAX_VALUE;
        boolean minInclusive() default true;
        boolean maxInclusive() default true;
    }
}
