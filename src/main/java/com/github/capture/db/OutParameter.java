package com.github.capture.db;

import java.sql.CallableStatement;
import java.sql.SQLException;

/**
 * Represents an OUT parameter for a stored procedure.  When running a stored
 * procedure with {@link QueryRunner}, pass an instance of
 * {@code OutParameter} to indicate that the parameter at that index is an
 * OUT parameter.  The value of the parameter may be obtained from the
 * {@code OutParameter} instance via {@link #getValue() }.
 * <p>
 * INOUT parameters are also supported by setting the {@code value} of
 * the {@code OutParameter} instance before invoking the stored procedure.
 *
 * @param <T> the class of the parameter; should be compatible via cast with the
 * class returned by the {@code CallableStatement.getObject(int)} method.
 */
public class OutParameter<T> {
    private final int sqlType;
    private final Class<T> javaType;
    private T value = null;

    /**
     * Construct an {@code OutParameter} for the given JDBC SQL type and
     * Java type.
     * @param sqlType the JDBC SQL type of the parameter as in
     * {@code java.sql.Types}.
     * @param javaType the Java class of the parameter value, cast compatible
     * with the type returned by {@code CallableStatement.getObject(int)}
     * for the JDBC type given by {@code sqlType}.
     */
    public OutParameter(final int sqlType, final Class<T> javaType) {
        this.sqlType = sqlType;
        this.javaType = javaType;
    }

    /**
     * Construct an {@code OutParameter} for the given JDBC SQL type and
     * Java type and with the given value.  The parameter will be treated as an
     * INOUT parameter if the value is null.
     * @param sqlType the JDBC SQL type of the parameter as in
     * {@code java.sql.Types}.
     * @param javaType the Java class of the parameter value, cast compatible
     * with the type returned by {@code CallableStatement.getObject(int)}
     * for the JDBC type given by {@code sqlType}.
     * @param value the IN value of the parameter
     */
    public OutParameter(final int sqlType, final Class<T> javaType, final T value) {
        this.sqlType = sqlType;
        this.javaType = javaType;
        this.value = value;
    }

    /**
     * Get the JDBC SQL type for this OUT parameter.
     * @return the JDBC SQL type for this OUT parameter.
     */
    public int getSqlType() {
        return sqlType;
    }

    /**
     * Get the Java class for this OUT parameter.
     * @return the Java class for this OUT parameter.
     */
    public Class<T> getJavaType() {
        return javaType;
    }

    /**
     * Get the value of the OUT parameter.  After the stored procedure has
     * been executed, the value is the value returned via this parameter.
     * @return the value of the OUT parameter.
     */
    public T getValue() {
        return value;
    }

    /**
     * Set the value of the OUT parameter.  If the value is not null when the
     * stored procedure is executed, then the parameter will be treated like an
     * INOUT parameter.
     * @param value the new value for the parameter.
     */
    public void setValue(final T value) {
        this.value = value;
    }

    /**
     * Set the value using the return value of the parameter an the given index
     * from the given {@code CallableStatement}.
     * @param stmt the already executed statement
     * @param index the (1-based) index of the parameter
     * @throws SQLException when the value could not be retrieved from the
     * statement.
     */
    void setValue(final CallableStatement stmt, final int index) throws SQLException {
        final Object object = stmt.getObject(index);
        value = javaType.cast(object);
    }

    /**
     * Set up the given statement by registering an OUT parameter at the given
     * index using the {@code sqlType} and {@code value} of this
     * {@code OutParameter}.  If the value is not null, the parameter is
     * treated like an INOUT parameter and the value is set on the statement.
     * @param stmt the statement the parameter should register on.
     * @param index the (1-based) index of the parameter.
     * @throws SQLException if the parameter could not be registered, or if the
     * value of the parameter could not be set.
     */
    void register(final CallableStatement stmt, final int index) throws SQLException {
        stmt.registerOutParameter(index, sqlType);
        if (value != null) {
            stmt.setObject(index, value);
        }
    }

    @Override
    public String toString() {
        return "OutParameter{" + "sqlType=" + sqlType + ", javaType="
                + javaType + ", value=" + value + '}';
    }
}
