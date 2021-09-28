/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.statistics.distribution;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.AggregateWith;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.aggregator.ArgumentsAggregationException;
import org.junit.jupiter.params.aggregator.ArgumentsAggregator;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Abstract base class for distribution tests.
 *
 * <p>This class uses parameterized tests that are repeated for instances of a
 * distribution. The distribution, test input and expected values are generated
 * dynamically from properties files loaded from resources.
 *
 * <p>The class has two specializations for testing {@link ContinuousDistribution} and
 * {@link DiscreteDistribution}. It is not intended to extend this class when creating
 * a test for a new distribution. This class exists for the sole purpose of containing
 * commons functionality to search for and load properties files containing the distribution
 * data.
 *
 * <p>To test a new distribution extend the specialized classes:
 * <ul>
 * <li>{@link BaseContinuousDistributionTest}
 * <li>{@link BaseDiscreteDistributionTest}
 * </ul>
 *
 * @param <T> Distribution type
 * @param <D> Distribution data type
 */
@TestInstance(Lifecycle.PER_CLASS)
abstract class BaseDistributionTest<T, D extends DistributionTestData> {
    /** The test data. Protected to allow use in sub-classes. */
    protected final List<D> data = new ArrayList<>();

    /**
     * Setup the test using data loaded from resource files.
     * Resource files are assumed to be named sequentially from 1:
     * <pre>
     * test.distname.1.properties
     * test.distname.2.properties
     * </pre>
     * <p>Where {@code distname} is the name of the distribution. The name
     * is dynamically created in {@link #getDistributionName()} and can be
     * overridden by implementing classes.
     */
    @BeforeAll
    void setup() {
        final String key = getDistributionName().toLowerCase(Locale.ROOT);
        // Set defaults
        final Properties defaults = new Properties();
        defaults.setProperty(DistributionTestData.KEY_CONNECTED, String.valueOf(isSupportConnected()));
        defaults.setProperty(DistributionTestData.KEY_TOLERANCE, String.valueOf(getTolerance()));
        defaults.setProperty(DistributionTestData.KEY_TOLERANCE_HP, String.valueOf(getHighPrecisionTolerance()));
        for (int i = 1; ; i++) {
            final String filename = String.format("test.%s.%d.properties", key, i);
            try (InputStream resource = this.getClass().getResourceAsStream(
                    filename)) {
                if (resource == null) {
                    break;
                }
                // Load properties file
                final Properties prop = new Properties(defaults);
                prop.load(resource);
                // Convert the properties to a D instance
                data.add(makeDistributionData(prop));
            } catch (IOException | NullPointerException | IllegalArgumentException e) {
                Assertions.fail("Failed to load test data: " + filename, e);
            }
        }
    }

    /**
     * Gets the default tolerance used in comparing expected and returned values.
     *
     * <p>The initial value is 1e-4.
     *
     * <p>Override this method to set the <strong>default</strong> tolerance for all test
     * cases defined by a properties file. Any properties file with a tolerance entry
     * ignore this value.
     *
     * @return the tolerance
     */
    protected double getTolerance() {
        return 1e-4;
    }

    /**
     * Gets the default tolerance used in comparing expected and returned values in high precision tests.
     *
     * <p>The initial value is 1e-22.
     *
     * <p>Override this method to set the <strong>default</strong> high-precision tolerance for all test
     * cases defined by a properties file. Any properties file with a high-precision tolerance entry
     * ignore this value.
     *
     * @return the high precision tolerance
     */
    protected double getHighPrecisionTolerance() {
        return 1e-22;
    }

    /**
     * The expected value for the distribution {@code isSupportConnected()} method.
     * The default is {@code true}. Test class should override this when the distribution
     * is not support connected.
     *
     * @return true if the distribution is support connected
     */
    protected boolean isSupportConnected() {
        return true;
    }

    /**
     * Gets the distribution name. This is used to search for test case resource files.
     *
     * <p>The default implementation removes the text {@code DistributionTest} from the
     * simple class name.
     *
     * @return the distribution name
     * @see Class#getSimpleName()
     */
    String getDistributionName() {
        return getClass().getSimpleName().replace("DistributionTest", "");
    }

    /**
     * Create a new distribution data instance from the properties.
     *
     * @param properties Properties
     * @return the distribution data
     */
    abstract D makeDistributionData(Properties properties);

    /**
     * Create a new distribution instance from the parameters.
     * It is assumed the parameters match the order of the parameter constructor.
     *
     * @param parameters Parameters of the distribution.
     * @return the distribution
     */
    abstract T makeDistribution(Object... parameters);

    /** Creates invalid parameters that are expected to throw an exception when passed to
     * the {@link #makeDistribution(Object...)} method.
     *
     * <p>This may return as many inner parameter arrays as is required to test all permutations
     * of invalid parameters to the distribution.
     * @return Array of invalid parameter arrays
     */
    abstract Object[][] makeInvalidParameters();

    /**
     * Gets the parameter names.
     * The names will be used with reflection to identify a parameter accessor in the distribution
     * with the name {@code getX()} where {@code X} is the parameter name.
     * The names should use the same order as {@link #makeDistribution(Object...)}.
     *
     * <p>Return {@code null} to ignore this test. Return {@code null} for an element of the
     * returned array to ignore that parameter.
     *
     * @return the parameter names
     */
    abstract String[] getParameterNames();


    //------------------------ Methods to stream the test data -----------------------------

    // The @MethodSource annotation will default to a no arguments method of the same name
    // as the @ParameterizedTest method. These can be overridden by child classes to
    // stream different arguments to the test case.

    /**
     * Create a named argument for the distribution from the parameters.
     * This is a convenience method to present the distribution with a short name in a test report.
     *
     * <p>This is used to create a new instance of the distribution for a test.
     *
     * @param parameters Parameters of the distribution.
     * @return the distribution argument
     */
    Named<T> namedDistribution(Object... parameters) {
        final T dist = makeDistribution(parameters);
        final String name = dist.getClass().getSimpleName() + " " + Arrays.toString(parameters);
        return Named.of(name, dist);
    }

    /**
     * Create a named argument for the array.
     * This is a convenience method to present arrays with a short name in a test report.
     * May be overridden for example to output more array details.
     *
     * @param name Name
     * @param array Array
     * @return the named argument
     */
    Named<?> namedArray(String name, Object array) {
        if (array instanceof double[]) {
            return namedArray(name, (double[]) array);
        }
        if (array instanceof int[]) {
            return namedArray(name, (int[]) array);
        }
        return Named.of(name, array);
    }

    /**
     * Create a named argument for the array.
     * This is a convenience method to present arrays with a short name in a test report.
     * May be overridden for example to output more array details.
     *
     * @param name Name
     * @param array Array
     * @return the named argument
     */
    Named<double[]> namedArray(String name, double[] array) {
        // Create the name using the first 3 elements
        final StringBuilder sb = new StringBuilder(75);
        sb.append(name);
        // Assume length is non-zero length
        int i = 0;
        sb.append(" [");
        sb.append(array[i++]);
        while (i < Math.min(3, array.length)) {
            sb.append(", ");
            sb.append(array[i++]);
        }
        if (i < array.length) {
            sb.append(", ... ");
        }
        sb.append(']');
        return Named.of(sb.toString(), array);
    }

    /**
     * Create a named argument for the array.
     * This is a convenience method to present arrays with a short name in a test report.
     * May be overridden for example to output more array details.
     *
     * @param name Name
     * @param array Array
     * @return the named argument
     */
    Named<int[]> namedArray(String name, int[] array) {
        // Create the name using the first 3 elements
        final StringBuilder sb = new StringBuilder(75);
        sb.append(name);
        // Assume length is non-zero length
        int i = 0;
        sb.append(" [");
        sb.append(array[i++]);
        while (i < Math.min(3, array.length)) {
            sb.append(", ");
            sb.append(array[i++]);
        }
        if (i < array.length) {
            sb.append(", ... ");
        }
        sb.append(']');
        return Named.of(sb.toString(), array);
    }

    /**
     * Create a predicate that returns false. This can be used to signal do not ignore
     * for test data.
     *
     * @param <D> Type of object
     * @return the predicate
     */
    private static <D> Predicate<D> doNotIgnore() {
        return d -> false;
    }

    /**
     * Create a stream of arguments containing the distribution to test.
     *
     * @return the stream
     */
    Stream<Arguments> streamDistrbution() {
        return data.stream().map(d -> Arguments.of(namedDistribution(d.getParameters())));
    }

    /**
     * Create a stream of arguments containing the distribution to test.
     *
     * @param filter Filter applied on the test data. If true the data is ignored.
     * @param name Name of the function under test
     * @return the stream
     */
    Stream<Arguments> streamDistrbution(Predicate<D> filter,
                                        String name) {
        final Builder<Arguments> b = Stream.builder();
        final int[] size = {0};
        data.forEach(d -> {
            if (filter.test(d)) {
                return;
            }
            size[0]++;
            b.accept(Arguments.of(namedDistribution(d.getParameters())));
        });
        Assumptions.assumeTrue(size[0] != 0, () -> "Distribution has no data for " + name);
        return b.build();
    }

    /**
     * Create a stream of arguments containing the distribution to test, the test
     * points, test values and the test tolerance. The points, values and tolerance
     * are identified using functions on the test instance data.
     *
     * <p>If the length of the points or values is zero then a
     * {@link org.opentest4j.TestAbortedException TestAbortedException} is raised.
     *
     * @param points Function to create the points
     * @param values Function to create the values
     * @param tolerance Function to create the tolerance
     * @param name Name of the function under test
     * @return the stream
     */
    <P, V> Stream<Arguments> stream(Function<D, P> points,
                                    Function<D, V> values,
                                    ToDoubleFunction<D> tolerance,
                                    String name) {
        return stream(doNotIgnore(), points, values, tolerance, name);
    }

    /**
     * Create a stream of arguments containing the distribution to test, the test
     * points, test values and the test tolerance. The points, values and tolerance
     * are identified using functions on the test instance data.
     *
     * <p>If the length of the points or values is zero then a
     * {@link org.opentest4j.TestAbortedException TestAbortedException} is raised.
     *
     * @param filter Filter applied on the test data. If true the data is ignored.
     * @param points Function to create the points
     * @param values Function to create the values
     * @param tolerance Function to create the tolerance
     * @param name Name of the function under test
     * @return the stream
     */
    <P, V> Stream<Arguments> stream(Predicate<D> filter,
                                    Function<D, P> points,
                                    Function<D, V> values,
                                    ToDoubleFunction<D> tolerance,
                                    String name) {
        final Builder<Arguments> b = Stream.builder();
        final int[] size = {0};
        data.forEach(d -> {
            final P p = points.apply(d);
            final V v = values.apply(d);
            if (filter.test(d) || TestUtils.getLength(p) == 0 || TestUtils.getLength(v) == 0) {
                return;
            }
            size[0]++;
            b.accept(Arguments.of(namedDistribution(d.getParameters()),
                     namedArray("points", p),
                     namedArray("values", v),
                     tolerance.applyAsDouble(d)));
        });
        Assumptions.assumeTrue(size[0] != 0, () -> "Distribution has no data for " + name);
        return b.build();
    }

    /**
     * Create a stream of arguments containing the distribution to test, the test
     * points, and test values. The points and values
     * are identified using functions on the test instance data.
     *
     * <p>If the length of the points or values is zero then a
     * {@link org.opentest4j.TestAbortedException TestAbortedException} is raised.
     *
     * @param points Function to create the points
     * @param values Function to create the values
     * @param name Name of the function under test
     * @return the stream
     */
    <P, V> Stream<Arguments> stream(Function<D, P> points,
                                    Function<D, V> values,
                                    String name) {
        return stream(doNotIgnore(), points, values, name);
    }

    /**
     * Create a stream of arguments containing the distribution to test, the test
     * points, and test values. The points and values
     * are identified using functions on the test instance data.
     *
     * <p>If the length of the points or values is zero then a
     * {@link org.opentest4j.TestAbortedException TestAbortedException} is raised.
     *
     * @param filter Filter applied on the test data. If true the data is ignored.
     * @param points Function to create the points
     * @param values Function to create the values
     * @param name Name of the function under test
     * @return the stream
     */
    <P, V> Stream<Arguments> stream(Predicate<D> filter,
                                    Function<D, P> points,
                                    Function<D, V> values,
                                    String name) {
        final Builder<Arguments> b = Stream.builder();
        final int[] size = {0};
        data.forEach(d -> {
            final P p = points.apply(d);
            final V v = values.apply(d);
            if (filter.test(d) || TestUtils.getLength(p) == 0 || TestUtils.getLength(v) == 0) {
                return;
            }
            size[0]++;
            b.accept(Arguments.of(namedDistribution(d.getParameters()),
                     namedArray("points", p),
                     namedArray("values", v)));
        });
        Assumptions.assumeTrue(size[0] != 0, () -> "Distribution has no data for " + name);
        return b.build();
    }


    /**
     * Create a stream of arguments containing the distribution to test and the test
     * points. The points are identified using functions on the test instance data.
     *
     * <p>If the length of the points or values is zero then a
     * {@link org.opentest4j.TestAbortedException TestAbortedException} is raised.
     *
     * @param points Function to create the points
     * @param name Name of the function under test
     * @return the stream
     */
    <P> Stream<Arguments> stream(Function<D, P> points,
                                 String name) {
        return stream(doNotIgnore(), points, name);
    }

    /**
     * Create a stream of arguments containing the distribution to test and the test
     * points. The points are identified using functions on the test instance data.
     *
     * <p>If the length of the points or values is zero then a
     * {@link org.opentest4j.TestAbortedException TestAbortedException} is raised.
     *
     * @param filter Filter applied on the test data. If true the data is ignored.
     * @param points Function to create the points
     * @param name Name of the function under test
     * @return the stream
     */
    <P> Stream<Arguments> stream(Predicate<D> filter,
                                 Function<D, P> points,
                                 String name) {
        final Builder<Arguments> b = Stream.builder();
        final int[] size = {0};
        data.forEach(d -> {
            final P p = points.apply(d);
            if (filter.test(d) || TestUtils.getLength(p) == 0) {
                return;
            }
            size[0]++;
            b.accept(Arguments.of(namedDistribution(d.getParameters()),
                     namedArray("points", p)));
        });
        Assumptions.assumeTrue(size[0] != 0, () -> "Distribution has no data for " + name);
        return b.build();
    }

    /**
     * Create arguments to test invalid parameters of the distribution. Each Object[]
     * will be expected to raise an exception when passed to the {@link #makeDistribution(Object...)}
     * method.
     *
     * @return the arguments
     */
    Object[][] testInvalidParameters() {
        final Object[][] params = makeInvalidParameters();
        Assumptions.assumeTrue(params != null, "Distribution has no invalid parameters");
        return params;
    }

    /**
     * Create a stream of arguments containing the parameters used to construct a distribution
     * using {@link #makeDistribution(Object...)}.
     *
     * @return the stream
     */
    Stream<Arguments> testParameterAccessors() {
        return data.stream().map(d -> Arguments.of(d.getParameters()));
    }

    //------------------------ Tests -----------------------------

    // Tests are final. It is expected that the test can be modified by overriding
    // the method used to stream the arguments, for example to use a specific tolerance
    // for a test in preference to the tolerance defined in the properties file.

    /**
     * Test invalid parameters will raise an exception when used to construct a distribution.
     */
    @ParameterizedTest
    @MethodSource
    final void testInvalidParameters(@AggregateWith(value = ArrayAggregator.class) Object[] parameters) {
        Assertions.assertThrows(DistributionException.class, () -> makeDistribution(parameters));
    }

    /**
     * Test the parameter accessors using the reflection API.
     */
    @ParameterizedTest
    @MethodSource
    final void testParameterAccessors(@AggregateWith(value = ArrayAggregator.class) Object[] parameters) {
        final String[] names = getParameterNames();
        Assumptions.assumeTrue(names != null, "No parameter accessors");
        Assertions.assertEquals(parameters.length, names.length, "Parameter <-> names length mismatch");

        final T dist = makeDistribution(parameters);
        for (int i = 0; i < names.length; i++) {
            final String name = names[i];
            if (name == null) {
                continue;
            }
            try {
                final Method method = dist.getClass().getMethod("get" + name);
                final Object o = method.invoke(dist);
                Assertions.assertEquals(parameters[i], o, () -> "Invalid parameter for " + name);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException |
                     IllegalArgumentException | InvocationTargetException e) {
                Assertions.fail("Failed to find method accessor: " + name, e);
            }
        }
    }

    /**
     * Aggregate all arguments as a single {@code Object[]} array.
     *
     * <p>Note: The default JUnit 5 behaviour for an Argument containing an {@code Object[]} is
     * to uses each element of the Object array as an indexed argument. This aggregator changes
     * the behaviour to pass the Object[] as argument index 0.
     */
    static class ArrayAggregator implements ArgumentsAggregator {
        @Override
        public Object aggregateArguments(ArgumentsAccessor accessor, ParameterContext context)
            throws ArgumentsAggregationException {
            return accessor.toArray();
        }
    }
}