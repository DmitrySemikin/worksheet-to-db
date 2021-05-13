package xyz.dsemikin.worksheettodb;

import java.time.LocalDateTime;

public class ExcelValueWrapper {

    private final Type type;
    private final String stringValue;
    private final Double doubleValue; // we want it to be nullable
    private final LocalDateTime dateValue;
    private final Boolean booleanValue;

    public ExcelValueWrapper(Object value) {
        if (value == null) {
            type = Type.EMPTY;
            stringValue = null;
            doubleValue = null;
            dateValue = null;
            booleanValue = null;
        } else if (value instanceof String) {
            type = Type.STRING;
            stringValue = (String) value;
            doubleValue = null;
            dateValue = null;
            booleanValue = null;
        } else if (value instanceof Double) {
            type = Type.DOUBLE;
            stringValue = null;
            doubleValue = (Double) value;
            dateValue = null;
            booleanValue = null;
        } else if (value instanceof LocalDateTime) {
            type = Type.DATE;
            stringValue = null;
            doubleValue = null;
            dateValue = (LocalDateTime) value;
            booleanValue = null;
        } else if(value instanceof Boolean) {
            type = Type.BOOLEAN;
            stringValue = null;
            doubleValue = null;
            dateValue = null;
            booleanValue = (Boolean) value;
        } else {
            throw new IllegalArgumentException("Argument of type " + value.getClass().getName() + " is not supported");
        }
    }

    public ExcelValueWrapper.Type type() {
        return type;
    }

    /** Returned value maybe null. Check type first, before using value */
    public String maybeStringValue() {
        return stringValue;
    }

    /** Returned value maybe null. Check type first, before using value */
    public Double maybeDoubleValue() {
        return doubleValue;
    }

    /** Returned value maybe null. Check type first, before using value */
    public LocalDateTime maybeDateValue() {
        return dateValue;
    }

    /** Returned value maybe null. Check type first, before using value */
    public Boolean maybeBooleanValue() {
        return booleanValue;
    }

    public enum Type {
        STRING,
        DOUBLE, // for all numbers, - also integral
        DATE,
        BOOLEAN,
        EMPTY // to denote absence of value. This type matches all other types, when checked.
    }
}
