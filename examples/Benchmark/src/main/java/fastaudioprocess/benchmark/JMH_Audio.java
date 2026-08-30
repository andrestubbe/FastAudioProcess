package fastaudioprocess.benchmark;

import fastaudioprocess.FastAudioAcoustics;
import fastaudioprocess.FastAudioDenoise;
import fastaudioprocess.FastAudioEqualizer;
import fastaudioprocess.FastAudioProcess;
import fastaudioprocess.FastFFT;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class JMH_Audio {

    private float[] frame512;
    private float[] frame1024;
    private float[] frameReal;
    private float[] frameImag;
    private FastAudioEqualizer equalizer;

    @Setup
    public void setup() {
        frame512 = new float[512];
        frame1024 = new float[1024];
        frameReal = new float[512];
        frameImag = new float[512];
        equalizer = new FastAudioEqualizer();
        equalizer.setGains(3.0f, 0.0f, 2.0f);

        for (int i = 0; i < 1024; i++) {
            float val = (float) Math.sin(2.0 * Math.PI * 440.0 * i / 16000.0);
            if (i < 512) {
                frame512[i] = val;
                frameReal[i] = val;
            }
            frame1024[i] = val;
        }
    }

    @Benchmark
    public float benchmarkPitchDetection() {
        return FastAudioProcess.detectPitchNative(frame512, 16000);
    }

    @Benchmark
    public void benchmarkFastFFT() {
        FastFFT.fft(frameReal, frameImag);
    }

    @Benchmark
    public void benchmarkSpectralDenoise() {
        FastAudioDenoise.suppressNoise(frame512, 16000, 1.0f, 0.02f);
    }

    @Benchmark
    public float benchmarkAcousticCrestFactor() {
        return FastAudioAcoustics.computeCrestFactor(frame512);
    }

    @Benchmark
    public float benchmarkAcousticZCR() {
        return FastAudioAcoustics.computeZeroCrossingRate(frame512);
    }

    @Benchmark
    public void benchmarkStreamingEqualizer() {
        equalizer.process(frame512);
    }
}