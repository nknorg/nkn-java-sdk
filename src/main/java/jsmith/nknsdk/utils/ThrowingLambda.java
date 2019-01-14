package jsmith.nknsdk.utils;

/**
 *
 */
public interface ThrowingLambda<T, R> {

    R apply (T arg) throws Throwable;

}
