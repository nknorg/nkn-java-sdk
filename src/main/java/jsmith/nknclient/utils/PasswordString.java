package jsmith.nknclient.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Similar to String, but can delete its contents safely and thus is somewhat mutable.
 *
 * This is to partially prevent memory dump attack.
 */
public final class PasswordString implements CharSequence {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordString.class);

    private final char[] values;
    private boolean disposed = false;

    /**
     * @param values to set PasswordString's value to. Char array is used as backing!
     */
    public PasswordString(char[] values) {
        this.values = values;
    }

    public PasswordString(CharSequence sequence) {
        char[] values = new char[sequence.length()];
        for (int i = 0; i < values.length; i++) {
            values[i] = sequence.charAt(i);
        }
        this.values = values;
    }

    @Override
    public int length() {
        if(disposed) return 0;
        return values.length;
    }

    @Override
    public char charAt(int index) {
        if(disposed) throw new ArrayIndexOutOfBoundsException("charAt ("+index+")\nthis.length() = 0 (disposed)");
        return values[index];
    }

    @Override
    public PasswordString subSequence(int start, int end) {
        if(start < 0 || start > values.length || end < 0 || end > values.length || start > end){
            throw new IndexOutOfBoundsException("subSequence ("+start+", "+end+")\nthis.length() = "+values.length);
        }
        char[] subValues = new char[end - start];
        System.arraycopy(values, start, subValues, 0, subValues.length);
        return new PasswordString(subValues);
    }

    /**
     * Disposed PasswordStrings have data erased and can't be used anymore.
     */
    public boolean isDisposed(){
        return disposed;
    }

    /**
     * Disposes this PasswordString so it can't be used anymore.
     */
    public void dispose() {
        disposed = true;
        for (int i = 0; i < values.length; i++) {
            values[i] = 'd'; // Random data, d stands for disposed
        }
    }

    /**
     * Copies PasswordString so it can be kept while original is disposed.
     *
     * Undefined behavior on already disposed PasswordStrings
     */
    public PasswordString copy(){
        assert !disposed : "Copying disposed password";
        char[] newValues = new char[values.length];
        System.arraycopy(values, 0, newValues, 0, newValues.length);
        return new PasswordString(newValues);
    }

    @Override
    public String toString() {
        if(disposed){
            return "Disposed PasswordString";
        }else{
            return "PasswordString";
        }
    }

    /**
     * Compares this and other PasswordString whether they holds same data.
     * If one or both PasswordStrings are disposed, result will be false
     *
     * @param other other PasswordString to compare
     * @return true if they holds same data, false otherwise
     */
    public boolean equals(PasswordString other) {
        if(disposed || other.disposed || values.length != other.values.length) {
            return false;
        } else {
            boolean same = true;
            for(int i = 0; i < values.length; i ++) {
                if(values[i] != other.values[i]) {
                    same = false;
                }
            }

            return same;
        }
    }

    public byte[] sha256() {
        assert !disposed : "Deriving sha-256 from disposed password";
        byte[] passwordBytes = new byte[values.length];

        // Let's hope we won't have any encoding problems // TODO use java.lang.StringCoding or alternative
        for (int i = 0; i < passwordBytes.length; i++) {
            passwordBytes[i] = (byte) values[i];
        }

        return Crypto.sha256(passwordBytes);
    }

    /*
    public KeyParameter deriveAESKey(byte[] salt, int iterations){
        assert !disposed : "Deriving key from disposed password";

        final long startTime = System.currentTimeMillis();
        final PKCS5S2ParametersGenerator gen = new PKCS5S2ParametersGenerator(new SHA256Digest());
        byte[] passwordBytes = null;
        try {
            passwordBytes = PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(this.values);
            gen.init(passwordBytes, salt, iterations);
            return ((KeyParameter)gen.generateDerivedParameters(256));
        } finally {
            if(passwordBytes != null){
                for (int i = 0; i < passwordBytes.length; i++) {
                    passwordBytes[i] = (byte)0xC1;//C1eared
                }
            }

            LOG.debug("AES key derived in {} ms",System.currentTimeMillis() - startTime);
        }
    }
    */

}
