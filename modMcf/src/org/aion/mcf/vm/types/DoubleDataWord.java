package org.aion.mcf.vm.types;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.aion.interfaces.vm.DataWord;
import org.aion.types.ByteArrayWrapper;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;

/**
 * DoubleDataWord is double the size of the basic unit data (DataWordImpl) used by the VM. A
 * DoubleDataWord is 256 bits. Its intended use is strictly within pre-compiled contracts, which
 * often have need of 32-byte storage keys.
 */
public class DoubleDataWord implements Comparable<DoubleDataWord>, DataWord {
    public static final BigInteger MAX_VALUE =
            BigInteger.valueOf(2).pow(256).subtract(BigInteger.ONE);

    public static final DoubleDataWord ZERO = new DoubleDataWord(0);
    public static final DoubleDataWord ONE = new DoubleDataWord(1);
    public static final int BYTES = 32;

    private byte[] data;

    /** Constructs a new DoubleDataWord of 32 zero bytes. */
    public DoubleDataWord() {
        this.data = new byte[BYTES];
    }

    /** Constructs a new DoubleDataWord whose numeric representation is equal to num. */
    public DoubleDataWord(int num) {
        ByteBuffer bb = ByteBuffer.allocate(BYTES);
        bb.position(BYTES - Integer.BYTES);
        bb.putInt(num);
        data = bb.array();
    }

    /** Constructs a new DoubleDataWord whose numeric representation is equal to num. */
    public DoubleDataWord(long num) {
        ByteBuffer bb = ByteBuffer.allocate(BYTES);
        bb.position(BYTES - Long.BYTES);
        bb.putLong(num);
        data = bb.array();
    }

    /**
     * Constructs a new DoubleDataWord that will wrap data. If data is less than 32 bytes then it
     * will be prepended by the necessary amount of leading zero bytes.
     */
    public DoubleDataWord(byte[] data) {
        if (data == null) {
            throw new NullPointerException("Construct DoubleDataWord with null data.");
        } else if (data.length == BYTES) {
            this.data = Arrays.copyOf(data, data.length);
        } else if (data.length < BYTES) {
            this.data = new byte[BYTES];
            System.arraycopy(data, 0, this.data, BYTES - data.length, data.length);
        } else {
            throw new RuntimeException(
                    "DoubleDataWord can't exceed 32 bytes: " + Hex.toHexString(data));
        }
    }

    /** Constructs a new DoubleDataWord whose numeric representation is equal to num. */
    public DoubleDataWord(BigInteger num) {
        this(num.toByteArray());
    }

    /** Constructs a new DoubleDataWord whose underlying byte array matches the hex string data. */
    public DoubleDataWord(String data) {
        this(Hex.decode(data));
    }

    /** Constructs a new DoubleDataWord from wrapper. */
    public DoubleDataWord(ByteArrayWrapper wrapper) {
        this(wrapper.getData());
    }

    @Override
    public byte[] getData() {
        return this.data;
    }

    @Override
    public byte[] getNoLeadZeroesData() {
        return ByteUtil.stripLeadingZeroes(data);
    }

    public BigInteger value() {
        return new BigInteger(1, data);
    }

    /** Returns an integer representation of this DoubleDataWord. */
    public int intValue() {
        int v = 0;
        for (int i = (BYTES - Integer.BYTES); i < BYTES; i++) {
            v = (v << Byte.SIZE) + (data[i] & 0xff);
        }
        return v;
    }

    /** Returns a long representation of this DoubleDataWord. */
    public long longValue() {
        long v = 0;
        for (int i = (BYTES - Long.BYTES); i < BYTES; i++) {
            v = (v << Byte.SIZE) + (data[i] & 0xff);
        }
        return v;
    }

    public boolean isNegative() {
        return (data[0] & 0x80) == 0x80;
    }

    @Override
    public DataWord copy() {
        byte[] bs = new byte[BYTES];
        System.arraycopy(data, 0, bs, 0, BYTES);
        return new DoubleDataWord(bs);
    }

    @Override
    public boolean isZero() {
        for (int i = 0; i < BYTES; i++) {
            if (data[BYTES - 1 - i] != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (this == o) {
            return true;
        }
        if (!(o instanceof DoubleDataWord)) {
            return false;
        }

        DoubleDataWord doubleDataWordWord = (DoubleDataWord) o;
        return Arrays.equals(this.data, doubleDataWordWord.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public int compareTo(DoubleDataWord o) {
        return Arrays.compare(this.data, o.data);
    }

    @Override
    public String toString() {
        return Hex.toHexString(data);
    }

    @Override
    public ByteArrayWrapper toWrapper() {
        return ByteArrayWrapper.wrap(data);
    }
}
