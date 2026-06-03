package kanvas.libs.math;

import java.util.Arrays;

public final class KVector {
    private final double[] values;

    public KVector(double... values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Vector must have at least one dimension");
        }
        this.values = values.clone();
    }

    public int dimensions() {
        return values.length;
    }

    public double get(int index) {
        return values[index];
    }
    public void set(int index, double value) {
        values[index] = value;
    }
    public void set(double... newValues) {
        if (newValues.length != values.length) throw new IllegalArgumentException("New values must match vector dimensions: " + values.length + " and " + newValues.length);
        System.arraycopy(newValues, 0, values, 0, values.length);
    }

    public KVector add(KVector other) {
        checkSameDimensions(other);
        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i] + other.values[i];
        }
        return new KVector(result);
    }

    public KVector subtract(KVector other) {
        checkSameDimensions(other);
        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i] - other.values[i];
        }
        return new KVector(result);
    }

    public KVector scale(double scalar) {
        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i] * scalar;
        }
        return new KVector(result);
    }

    public double dot(KVector other) {
        checkSameDimensions(other);
        double result = 0;
        for (int i = 0; i < values.length; i++) {
            result += values[i] * other.values[i];
        }
        return result;
    }

    public double magnitudeSquared() {
        return dot(this);
    }

    public double magnitude() {
        return Math.sqrt(magnitudeSquared());
    }

    public KVector normalized() {
        double mag = magnitude();
        if (mag == 0) return new KVector(new double[values.length]);
        return scale(1.0 / mag);
    }

    public double distanceTo(KVector other) {
        return subtract(other).magnitude();
    }

    public double[] toArray() {
        return values.clone();
    }

    private void checkSameDimensions(KVector other) {
        if (other.values.length != values.length) {
            throw new IllegalArgumentException(
                "Vector dimensions must match: " + values.length + " and " + other.values.length
            );
        }
    }

    public String toString() {
        return "KVector" + Arrays.toString(values);
    }
}