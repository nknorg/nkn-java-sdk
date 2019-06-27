package jsmith.nknsdk.utils;

import com.iwebpp.crypto.TweetNaclFast;

/**
 * Port of https://github.com/dchest/ed2curve-js/blob/master/ed2curve.js
 */
public class EdToCurve {

    // Converts Ed25519 public key to Curve25519 public key.
    // montgomeryX = (edwardsY + 1)*inverse(1 - edwardsY) mod p
    public static byte[] convertPublicKey(byte[] pk) {
        byte[] z = new byte[32];
        long[][] q = new long[][] {gf(), gf(), gf(), gf()};
        long[] a = gf(), b = gf();

        if (unpackneg(q, pk) != 0) return null; // reject invalid key

        long[] y = q[1];

        A(a, gf1, y);
        Z(b, gf1, y);
        inv25519(b, b);
        M(a, a, b);

        pack25519(z, a);
        return z;
    }

    // Converts Ed25519 secret key to Curve25519 secret key.
    public static byte[] convertSecretKey(byte[] sk) {
        byte[] d = new byte[64], o = new byte[32];
        TweetNaclFast.crypto_hash(d, sk, 0, 32);
        d[0] &= 248;
        d[31] &= 127;
        d[31] |= 64;
        System.arraycopy(d, 0, o, 0, 32);
        return o;
    }



    private static long[] gf() { return gf(null); }
    private static long[] gf(long[] init) {
        long[] r = new long[16];
        if (init != null) System.arraycopy(init, 0, r, 0, init.length);
        return r;
    }

    private static final long[] gf0 = gf();
    private static final long[] gf1 = gf(new long[]{1});
    private static final long[] D = gf(new long[]{0x78a3, 0x1359, 0x4dca, 0x75eb, 0xd8ab, 0x4141, 0x0a4d, 0x0070, 0xe898, 0x7779, 0x4079, 0x8cc7, 0xfe73, 0x2b6f, 0x6cee, 0x5203});
    private static final long[] I = gf(new long[]{0xa0b0, 0x4a0e, 0x1b27, 0xc4ee, 0xe478, 0xad2f, 0x1806, 0x2f43, 0xd7a7, 0x3dfb, 0x0099, 0x2b4d, 0xdf0b, 0x4fc1, 0x2480, 0x2b83});

    private static byte unpackneg(long[][] r, byte[] p) {
        long[] t = gf(), chk = gf(), num = gf(),
                den = gf(), den2 = gf(), den4 = gf(),
                den6 = gf();

        set25519(r[2]);
        unpack25519(r[1], p);
        S(num, r[1]);
        M(den, num, D);
        Z(num, num, r[2]);
        A(den, r[2], den);

        S(den2, den);
        S(den4, den2);
        M(den6, den4, den2);
        M(t, den6, num);
        M(t, t, den);

        pow2523(t, t);
        M(t, t, num);
        M(t, t, den);
        M(t, t, den);
        M(r[0], t, den);

        S(chk, r[0]);
        M(chk, chk, den);
        if (neq25519(chk, num) != 0) M(r[0], r[0], I);

        S(chk, r[0]);
        M(chk, chk, den);
        if (neq25519(chk, num) != 0) return -1;

        if (par25519(r[0]) == (p[31] >> 7)) Z(r[0], gf0, r[0]);

        M(r[3], r[0], r[1]);
        return 0;
    }

    private static void set25519(long[] r) {
        System.arraycopy(EdToCurve.gf1, 0, r, 0, 16);
    }

    private static void car25519(long[] o) {
        long c;
        for (int i = 0; i < 16; i++) {
            o[i] += 65536;
            c = o[i] >> 16;
            o[(i + 1) * (i < 15 ? 1 : 0)] += c - 1 + 37 * (c - 1) * (i == 15 ? 1 : 0);
            o[i] -= c << 16;
        }
    }

    private static byte par25519(long[] a) {
        byte[] d = new byte[32];
        pack25519(d, a);
        return (byte) (d[0] & 1);
    }

    private static void unpack25519(long[] o, byte[] n) {
        for (int i = 0; i < 16; i++) o[i] = (n[2 * i] & 0xFF) + ((n[2 * i + 1] << 8) & 0xFF00);
        o[15] &= 0x7fff;
    }


    private static void pack25519(byte[] o, long[] n) {
        long[] m = gf(), t = gf();
        System.arraycopy(n, 0, t, 0, 16);
        car25519(t);
        car25519(t);
        car25519(t);
        for (int j = 0; j < 2; j++) {
            m[0] = t[0] - 0xffed;
            for (int i = 1; i < 15; i++) {
                m[i] = t[i] - 0xffff - ((m[i - 1] >> 16) & 1);
                m[i-1] &= 0xffff;
            }
            m[15] = t[15] - 0x7fff - ((m[14] >> 16) & 1);
            long b = (m[15] >> 16) & 1;
            m[14] &= 0xffff;
            sel25519(t, m, 1 - b);
        }
        for (int i = 0; i < 16; i++) {
            o[2 * i] = (byte) (t[i] & 0xff);
            o[2 * i + 1] = (byte) (t[i] >> 8);
        }
    }

    private static void sel25519(long[] p, long[] q, long b) {
        long t, c = -b;
        for (int i = 0; i < 16; i++) {
            t = c & (p[i] ^ q[i]);
            p[i] ^= t;
            q[i] ^= t;
        }
    }

    // addition
    private static void A(long[] o, long[] a, long[] b) {
        for (int i = 0; i < 16; i++) o[i] = a[i] + b[i];
    }

    // subtraction
    private static void Z(long[] o, long[] a, long[] b) {
        for (int i = 0; i < 16; i++) o[i] = (a[i] - b[i]);
    }

    // multiplication
    private static void M(long[] o, long[] a, long[] b) {
        long[] t = new long[31];
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                t[i + j] += a[i] * b[j];
            }
        }
        for (int i = 0; i < 15; i++) {
            t[i] += 38 * t[i + 16];
        }
        System.arraycopy(t, 0, o, 0, 16);
        car25519(o);
        car25519(o);
    }

    // squaring
    private static void S(long[] o, long[] a) {
        M(o, a, a);
    }

    // inversion
    private static void inv25519(long[] o, long[] i) {
        long[] c = gf(i);
        for (int a = 253; a >= 0; a--) {
            S(c, c);
            if(a != 2 && a != 4) M(c, c, i);
        }
        System.arraycopy(c, 0, o, 0, 16);
    }

    private static void pow2523(long[] o, long[] i) {
        long[] c = gf();
        System.arraycopy(i, 0, c, 0, 16);
        for (int a = 250; a >= 0; a--) {
            S(c, c);
            if (a != 1) M(c, c, i);
        }
        System.arraycopy(c, 0, o, 0, 16);
    }

    private static int neq25519(long[] a, long[] b) {
        byte[] c = new byte[32], d = new byte[32];
        pack25519(c, a);
        pack25519(d, b);
        return crypto_verify_32(c, d);
    }

    private static int crypto_verify_32(byte[] x, byte[] y) {
        return vn(x, y);
    }

    private static int vn(byte[] x, byte[] y) {
        int d = 0;
        for (int i = 0; i < 32; i++) d |= x[i] ^ y[i];
        return (1 & ((d - 1) >> 8)) - 1;
    }

}
