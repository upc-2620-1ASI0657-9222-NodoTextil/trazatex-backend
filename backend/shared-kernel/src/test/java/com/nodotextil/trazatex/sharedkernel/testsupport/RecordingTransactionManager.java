package com.nodotextil.trazatex.sharedkernel.testsupport;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SmartTransactionObject;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Gestor de transacciones de prueba con la semántica real de Spring (propagación, rollback-only global y
 * {@code UnexpectedRollbackException}) que anota cómo terminó cada transacción física.
 */
public class RecordingTransactionManager extends AbstractPlatformTransactionManager {

    public static final class TxState implements SmartTransactionObject {
        boolean active;
        volatile boolean rollbackOnly;

        @Override
        public boolean isRollbackOnly() {
            return rollbackOnly;
        }

        @Override
        public void flush() {
        }
    }

    private final ThreadLocal<TxState> current = new ThreadLocal<>();
    private final List<String> outcomes = new CopyOnWriteArrayList<>();

    public List<String> outcomes() {
        return outcomes;
    }

    @Override
    protected Object doGetTransaction() {
        TxState state = current.get();
        return state != null ? state : new TxState();
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) {
        return ((TxState) transaction).active;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        TxState state = (TxState) transaction;
        state.active = true;
        current.set(state);
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        outcomes.add("commit");
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        outcomes.add("rollback");
    }

    @Override
    protected void doSetRollbackOnly(DefaultTransactionStatus status) {
        ((TxState) status.getTransaction()).rollbackOnly = true;
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        current.remove();
    }
}
