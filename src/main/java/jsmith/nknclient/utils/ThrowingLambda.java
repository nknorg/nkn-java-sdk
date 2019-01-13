package jsmith.nknclient.utils;

/**
 *
 */
public interface ThrowingLambda<T, R> {

    R apply (T arg) throws Throwable;

}
