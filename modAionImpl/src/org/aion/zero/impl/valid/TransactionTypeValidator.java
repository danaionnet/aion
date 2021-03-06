package org.aion.zero.impl.valid;

import static org.aion.mcf.valid.TransactionTypeRule.isValidTransactionType;

/**
 * Validator for the type field of transactions allowed by the network. The transaction types
 * currently correlate with which virtual machines are enabled. This field mainly impacts contract
 * creation. Contracts created using the default transaction type should be deployed on the FastVM.
 * AVM contracts creations/transactions are declared valid only if the AVM is enabled from the
 * configuration and the transaction has the correct type associated with the AVM.
 *
 * @author Alexandra Roatis
 */
public class TransactionTypeValidator {

    private static boolean avmEnabled;

    public static void enableAvmCheck(boolean enableAVM) {
        avmEnabled = enableAVM;
    }

    public static boolean isValid(byte type) {
        return true;
    }

    public static boolean isValidAfterFork(byte type) {
        return isValidTransactionType(type);
    }
}
