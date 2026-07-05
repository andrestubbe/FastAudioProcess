package fastaudioprocess;

import ai.onnxruntime.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Stateful wrapper for Silero Voice Activity Detection (VAD) v5 running locally via ONNX Runtime.
 */
public final class SileroVAD implements AutoCloseable {
    private final OrtEnvironment env;
    private final OrtSession session;
    
    // LSTM state: shape [2, 1, 128]
    private float[][][] state = new float[2][1][128];

    public SileroVAD(String modelPath) throws Exception {
        if (!new File(modelPath).exists()) {
            throw new FileNotFoundException("Silero VAD ONNX model not found at path: " + modelPath);
        }
        this.env = OrtEnvironment.getEnvironment();
        this.session = env.createSession(modelPath, new OrtSession.SessionOptions());
        for (Map.Entry<String, NodeInfo> entry : session.getInputInfo().entrySet()) {
            System.out.println("[VAD DEBUG] Input: " + entry.getKey() + " | Info: " + entry.getValue().getInfo().toString());
        }
        for (Map.Entry<String, NodeInfo> entry : session.getOutputInfo().entrySet()) {
            System.out.println("[VAD DEBUG] Output: " + entry.getKey() + " | Info: " + entry.getValue().getInfo().toString());
        }
    }

    /**
     * Resets the recurrent LSTM state (called e.g. at the start of a new audio stream).
     */
    public synchronized void reset() {
        state = new float[2][1][128];
    }

    /**
     * Evaluates a single 512-sample frame of 16kHz mono audio.
     * @param samples Exactly 512 float samples (normalized -1.0 to 1.0)
     * @param sampleRate Rate (must be 16000 or 8000)
     * @return Speech probability (0.0 to 1.0)
     */
    public synchronized float detectSpeech(float[] samples, int sampleRate) throws Exception {
        if (samples == null || samples.length != 512) {
            throw new IllegalArgumentException("Silero VAD requires exactly 512 samples per frame.");
        }
        if (sampleRate != 16000 && sampleRate != 8000) {
            throw new IllegalArgumentException("Silero VAD supports only 8000Hz or 16000Hz sample rates.");
        }

        // Create input tensors
        long[] inputShape = { 1, 512 };
        OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(samples), inputShape);

        OnnxTensor srTensor = OnnxTensor.createTensor(env, (long) sampleRate);

        OnnxTensor stateTensor = OnnxTensor.createTensor(env, state);

        // Feed to session
        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put("input", inputTensor);
        inputs.put("sr", srTensor);
        inputs.put("state", stateTensor);

        try (OrtSession.Result result = session.run(inputs)) {
            // Parse outputs
            // output 0 is speech probability [1, 1]
            float[][] outputVal = (float[][]) result.get(0).getValue();
            float speechProb = outputVal[0][0];

            // output 1 is new state [2, 1, 128]
            state = (float[][][]) result.get(1).getValue();

            return speechProb;
        } finally {
            inputTensor.close();
            srTensor.close();
            stateTensor.close();
        }
    }

    @Override
    public void close() throws Exception {
        if (session != null) session.close();
    }
}
