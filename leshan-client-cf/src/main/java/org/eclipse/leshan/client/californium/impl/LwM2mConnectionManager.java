package org.eclipse.leshan.client.californium.impl;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.californium.elements.tcp.ConnectionInfo;
import org.eclipse.californium.elements.tcp.ConnectionStateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class LwM2mConnectionManager implements ConnectionStateListener {
    private static final Logger LOG = LoggerFactory.getLogger(LwM2mConnectionManager.class);

    protected final AtomicBoolean started = new AtomicBoolean(false);

    private LwM2mConnectionManager() {
    }

    public void synchStart() {
        started.set(true);
    }

    public void synchStop() {
        started.set(false);
    }

    public boolean isConnected() {
        return started.get();
    }

    public static LwM2mConnectionManager createTLSConnectionManager() {
        return new TLSConnectionManager();
    }

    public static LwM2mConnectionManager createNullConnectionManager() {
        return new LwM2mConnectionManager() {

            @Override
            public void stateChange(final ConnectionInfo info) {
            }
        };
    }

    private static class TLSConnectionManager extends LwM2mConnectionManager {

        private static final ReentrantLock lock = new ReentrantLock();
        private static final Condition tlsCompleted = lock.newCondition();

        @Override
        public void stateChange(final ConnectionInfo info) {
            switch (info.getConnectionState()) {
            case TLS_HANDSHAKE_FAILED:
            case CONNECTED_SECURE:
                lock.lock();
                try {
                    tlsCompleted.signalAll();
                } finally {
                    lock.unlock();
                    LOG.debug("Synchronous unlocking on start for TLS");
                }
                break;
            default:
            }
        }

        @Override
        public void synchStart() {
            LOG.debug("Synchronous locking on start for TLS");
            lock.lock();
            try {
                tlsCompleted.await();
            } catch (final InterruptedException e) {
                LOG.error("Interrupted during TLS synchronized handshake", e);
            } finally {
                started.set(true);
                lock.unlock();
            }

        }

        @Override
        public void synchStop() {
            started.set(false);
        }

    }

}
