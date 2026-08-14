package fastaudioprocess.benchmark;

import fastaudioprocess.FastAudioProcess;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 2, time = 1)
@Fork(1)
public class JMH_Audio {

    private float[] audioBuffer;

    @Setup
    public void setup() {
        audioBuffer = new float[44100];
        for (int i = 0; i < audioBuffer.length; i++) {
            audioBuffer[i] = (float) Math.sin(2.0 * Math.PI * 440.0 * i / 44100.0);
        }
    }

    @Benchmark
    public float benchmarkFastAudioProcessPitch() {
        return FastAudioProcess.detectPitchNative(audioBuffer, 44100);
    }
}
