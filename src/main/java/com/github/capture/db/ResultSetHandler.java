package com.github.capture.db;


import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Implementations of this interface convert ResultSets into other objects.
 *
 * @param <T> the target type the input ResultSet will be converted to.
 */
public interface ResultSetHandler<T> {

    /**
     * Turn the {@code ResultSet} into an Object.
     *
     * @param rs The {@code ResultSet} to handle.  It has not been touched
     * before being passed to this method.
     *
     * @return An Object initialized with {@code ResultSet} data. It is
     * legal for implementations to return {@code null} if the
     * {@code ResultSet} contained 0 rows.
     *
     * @throws SQLException if a database access error occurs
     */
    T handle(ResultSet rs) throws SQLException;
}
