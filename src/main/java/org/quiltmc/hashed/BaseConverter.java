package org.quiltmc.hashed;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class BaseConverter {
    public static int[] toBaseN(BigInteger bigInt, int base) {
        List<Integer> digits = new ArrayList<>();
        while (bigInt.compareTo(BigInteger.ZERO) != 0) {
            BigInteger mod = bigInt.mod(BigInteger.valueOf(base));
            bigInt = bigInt.divide(BigInteger.valueOf(base));
            digits.add(mod.intValue());
        }
        return digits.stream().mapToInt(Integer::intValue).toArray();
    }

    public static String toBase52(BigInteger bigInt) {
        int[] digits = toBaseN(bigInt, 52);

        StringBuilder result = new StringBuilder();
        for (int digit : digits) {
            if (digit < 26) {
                result.append((char)('a' + digit));
            }
            else {
                result.append((char)('A' + digit - 26));
            }
        }

        return result.toString();
    }

    public static String toBase26(BigInteger bigInt) {
        int[] digits = toBaseN(bigInt, 26);

        StringBuilder result = new StringBuilder();
        for (int digit : digits) {
            result.append((char)('a' + digit));
        }

        return result.toString();
    }
}
