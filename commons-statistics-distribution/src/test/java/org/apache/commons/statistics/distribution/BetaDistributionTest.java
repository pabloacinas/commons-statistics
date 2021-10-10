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

import java.util.Arrays;

import org.apache.commons.rng.simple.RandomSource;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.inference.GTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test cases for {@link BetaDistribution}.
 * Extends {@link BaseContinuousDistributionTest}. See javadoc of that class for details.
 *
 * <p>The properties files contain test cases for
 * alpha and beta in [0.1, 0.5, 1.0, 2.0, 4.0] (25 cases).
 */
class BetaDistributionTest extends BaseContinuousDistributionTest {
    /** Alpha/Beta values for extended test of the sampling. */
    static final double[] ALPHA_BETAS = {0.1, 1, 10, 100, 1000};
    /** Epsilon value for extended test of the sampling. */
    static final double EPSILON = StatUtils.min(ALPHA_BETAS);

    @Override
    ContinuousDistribution makeDistribution(Object... parameters) {
        final double alpha = (Double) parameters[0];
        final double beta = (Double) parameters[1];
        return BetaDistribution.of(alpha, beta);
    }

    @Override
    protected double getRelativeTolerance() {
        // A lower tolerance creates failures in the CDF inverse mapping test
        return 1e-9;
    }

    @Override
    Object[][] makeInvalidParameters() {
        return new Object[][] {
            {0.0, 1.0},
            {-0.1, 1.0},
            {0.5, 0.0},
            {0.5, -0.1}
        };
    }

    @Override
    String[] getParameterNames() {
        return new String[] {"Alpha", "Beta"};
    }

    //-------------------- Additional test cases -------------------------------

    /**
     * Precision tests for verifying that CDF calculates accurately in cases
     * where 1-cdf(x) is inaccurately 1.
     */
    @Test
    void testCumulativePrecision() {
        // Calculated using WolframAlpha
        checkCumulativePrecision(5.0, 5.0, 0.0001, 1.2595800539968654e-18);
        checkCumulativePrecision(4.0, 5.0, 0.00001, 6.999776002800025e-19);
        checkCumulativePrecision(5.0, 4.0, 0.0001, 5.598600119996539e-19);
        checkCumulativePrecision(6.0, 2.0, 0.001, 6.994000000000028e-18);
        checkCumulativePrecision(2.0, 6.0, 1e-9, 2.0999999930000014e-17);
    }

    /**
     * Precision tests for verifying that survival function calculates accurately in cases
     * where 1-sf(x) is inaccurately 1.
     */
    @Test
    void testSurvivalPrecision() {
        // Calculated using WolframAlpha
        checkSurvivalPrecision(5.0, 5.0, 0.9999, 1.2595800539961496e-18);
        checkSurvivalPrecision(4.0, 5.0, 0.9999, 5.598600119993397e-19);
        checkSurvivalPrecision(5.0, 4.0, 0.99998, 1.1199283217964632e-17);
        checkSurvivalPrecision(6.0, 2.0, 0.999999999, 2.0999998742158932e-17);
        checkSurvivalPrecision(2.0, 6.0, 0.999, 6.994000000000077e-18);
    }

    private static void checkCumulativePrecision(double alpha, double beta, double value, double expected) {
        final double tolerance = 1e-22;
        final BetaDistribution dist = BetaDistribution.of(alpha, beta);
        Assertions.assertEquals(
            expected,
            dist.cumulativeProbability(value),
            tolerance,
            () -> "cumulative probability not precise at " + value + " for a=" + alpha + " & b=" + beta);
    }

    private static void checkSurvivalPrecision(double alpha, double beta, double value, double expected) {
        final double tolerance = 1e-22;
        final BetaDistribution dist = BetaDistribution.of(alpha, beta);
        Assertions.assertEquals(
            expected,
            dist.survivalProbability(value),
            tolerance,
            () -> "survival function not precise at " + value + " for a=" + alpha + " & b=" + beta);
    }

    @Test
    void testLogDensityPrecondition1() {
        final BetaDistribution dist = BetaDistribution.of(0.5, 3);
        Assertions.assertEquals(Double.POSITIVE_INFINITY, dist.logDensity(0.0));
    }

    @Test
    void testLogDensityPrecondition2() {
        final BetaDistribution dist = BetaDistribution.of(2, 0.5);
        Assertions.assertEquals(Double.POSITIVE_INFINITY, dist.logDensity(1.0));
    }

    @Test
    void testMomentsSampling() {
        final UniformRandomProvider rng = RandomSource.XO_SHI_RO_256_PP.create(123456789L);
        final int numSamples = 1000;
        for (final double alpha : ALPHA_BETAS) {
            for (final double beta : ALPHA_BETAS) {
                final BetaDistribution betaDistribution = BetaDistribution.of(alpha, beta);

                final ContinuousDistribution.Sampler sampler = betaDistribution.createSampler(rng);
                final double[] observed = TestUtils.sample(numSamples, sampler);
                Arrays.sort(observed);

                Assertions.assertEquals(betaDistribution.getMean(), StatUtils.mean(observed), EPSILON,
                    () -> String.format("E[Beta(%.2f, %.2f)]", alpha, beta));
                Assertions.assertEquals(betaDistribution.getVariance(), StatUtils.variance(observed), EPSILON,
                    () -> String.format("Var[Beta(%.2f, %.2f)]", alpha, beta));
            }
        }
    }

    @Test
    void testGoodnessOfFit() {
        final UniformRandomProvider rng = RandomSource.XO_SHI_RO_256_PP.create(123456789L);

        final int numSamples = 1000;
        final double level = 0.01;
        for (final double alpha : ALPHA_BETAS) {
            for (final double beta : ALPHA_BETAS) {
                final BetaDistribution betaDistribution = BetaDistribution.of(alpha, beta);

                final ContinuousDistribution.Sampler sampler = betaDistribution.createSampler(rng);
                final double[] observed = TestUtils.sample(numSamples, sampler);

                final double gT = gTest(betaDistribution, observed);
                Assertions.assertFalse(gT < level,
                    () -> "G goodness-of-fit (" + gT + ") test rejected null at alpha = " + level);
            }
        }
    }

    private static double gTest(final ContinuousDistribution expectedDistribution, final double[] values) {
        final int numBins = values.length / 30;
        final double[] breaks = new double[numBins];
        for (int b = 0; b < breaks.length; b++) {
            breaks[b] = expectedDistribution.inverseCumulativeProbability((double) b / numBins);
        }

        final long[] observed = new long[numBins];
        for (final double value : values) {
            int b = 0;
            do {
                b++;
            } while (b < numBins && value >= breaks[b]);

            observed[b - 1]++;
        }

        final double[] expected = new double[numBins];
        Arrays.fill(expected, (double) values.length / numBins);

        return new GTest().gTest(expected, observed);
    }
}
