package com.masuary.masucraftfixes.network;

import net.minecraft.util.thread.BlockableEventLoop;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisconnectHandOffTest {

    private static final class TestServerLoop extends BlockableEventLoop<Runnable> {

        private final AtomicReference<Thread> serverThread = new AtomicReference<>();

        TestServerLoop() {
            super("test-server");
        }

        void runPendingTasks() {
            runAllTasks();
        }

        void bindToCurrentThread() {
            serverThread.set(Thread.currentThread());
        }

        @Override
        protected Runnable wrapRunnable(Runnable runnable) {
            return runnable;
        }

        @Override
        protected boolean shouldRun(Runnable runnable) {
            return true;
        }

        @Override
        protected Thread getRunningThread() {
            return serverThread.get();
        }
    }

    @Test
    void workerDisconnectReturnsWhileTheServerThreadWaitsForTheWorker() throws Exception {
        TestServerLoop server = new TestServerLoop();
        List<String> handledOnThreads = new CopyOnWriteArrayList<>();
        CountDownLatch workerFinished = new CountDownLatch(1);
        AtomicReference<Boolean> workerFinishedInTime = new AtomicReference<>();

        Thread serverThread = new Thread(() -> {
            server.bindToCurrentThread();
            Thread worker = new Thread(() -> {
                DisconnectHandOff.handleDisconnection(server, () -> handledOnThreads.add(Thread.currentThread().getName()), "QAPlayer");
                workerFinished.countDown();
            }, "the_vault:vault_test");
            worker.start();
            try {
                workerFinishedInTime.set(workerFinished.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("server thread interrupted", interrupted);
            }
            server.runPendingTasks();
        }, "Server thread");
        serverThread.start();
        serverThread.join(TimeUnit.SECONDS.toMillis(10));

        assertEquals(Boolean.TRUE, workerFinishedInTime.get(), "the worker waited for the blocked server thread");
        assertEquals(List.of("Server thread"), handledOnThreads);
    }

    @Test
    void serverThreadDisconnectIsHandledBeforeItReturns() throws Exception {
        TestServerLoop server = new TestServerLoop();
        List<String> events = new CopyOnWriteArrayList<>();

        Thread serverThread = new Thread(() -> {
            server.bindToCurrentThread();
            DisconnectHandOff.handleDisconnection(server, () -> events.add("handled on " + Thread.currentThread().getName()), "QAPlayer");
            events.add("returned");
        }, "Server thread");
        serverThread.start();
        serverThread.join(TimeUnit.SECONDS.toMillis(10));

        assertEquals(List.of("handled on Server thread", "returned"), events);
        assertEquals(0, server.getPendingTasksCount(), "nothing was queued for later");
    }
}
