package org.gradle.profiler;

import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.Sample;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.gradle.profiler.Logging.startOperation;

public abstract class ScenarioInvoker<T extends ScenarioDefinition, R extends BuildInvocationResult> {
    /**
     * Runs a scenario and collects the results.
     */
    public final void run(ScenarioDefinition scenario, InvocationSettings settings, BenchmarkResultCollector collector) throws IOException, InterruptedException {
        Consumer<R> resultConsumer = collector.scenario(scenario, samplesFor(settings));
        doRun((T) scenario, settings, resultConsumer);
    }

    /**
     * Runs a scenario and collects the results.
     */
    abstract void doRun(T scenario, InvocationSettings settings, Consumer<R> resultConsumer) throws IOException, InterruptedException;

    /**
     * Which samples will this invoker generate for the given settings?
     */
    public List<Sample<? super R>> samplesFor(InvocationSettings settings) {
        return Collections.singletonList(BuildInvocationResult.EXECUTION_TIME);
    }

    /**
     * Runs a single measured build and collects the result.
     */
    protected <R extends BuildInvocationResult> R runMeasured(String displayName, BuildMutator mutator, Supplier<? extends R> action, Consumer<? super R> consumer) {
        startOperation("Running " + displayName);
        mutator.beforeBuild();
        R result = tryRun(() -> {
            R result1 = action.get();
            printExecutionTime(result1.getExecutionTime());
            return result1;
        }, mutator::afterBuild);
        consumer.accept(result);
        return result;
    }

    private static void printExecutionTime(Duration executionTime) {
        System.out.println("Execution time " + executionTime.toMillis() + " ms");
    }

    /**
     * Runs a single clean-up build.
     */
    protected void runCleanup(String displayName, BuildMutator mutator, Runnable action) {
        startOperation("Running cleanup for " + displayName);
        mutator.beforeCleanup();
        tryRun(() -> action.run(), mutator::afterCleanup);
    }

    /**
     * Returns a {@link Supplier} that returns the result of the given command.
     */
    protected Supplier<BuildInvocationResult> measureCommandLineExecution(String displayName, List<String> commandLine, File workingDir) {
        return () -> {
            Timer timer = new Timer();
            new CommandExec().inDir(workingDir).run(commandLine);
            Duration executionTime = timer.elapsed();
            return new BuildInvocationResult(displayName, executionTime);
        };
    }

    private <V> V tryRun(Supplier<? extends V> action, Consumer<Throwable> after) {
        Throwable error = null;
        try {
            return action.get();
        } catch (RuntimeException | Error ex) {
            error = ex;
            throw ex;
        } catch (Throwable ex) {
            error = ex;
            throw new RuntimeException(ex);
        } finally {
            after.accept(error);
        }
    }

    private void tryRun(Runnable action, Consumer<Throwable> after) {
        tryRun(() -> {
            action.run();
            return null;
        }, after);
    }
}
