package tech.qiantong.qknow.rag.eval;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class FeatureHashEmbeddingModel implements EmbeddingModel {

    static final String VERSION = "feature-hash-v1";

    private final int dimensions;
    private final long seed;
    private final byte[] versionBytes;
    private final List<String> recordedInputs = new ArrayList<>();

    FeatureHashEmbeddingModel(int dimensions, long seed, String version) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        this.dimensions = dimensions;
        this.seed = seed;
        this.versionBytes = version.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> inputs = List.copyOf(Objects.requireNonNull(request, "request").getInstructions());
        recordedInputs.addAll(inputs);
        List<Embedding> embeddings = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            embeddings.add(new Embedding(vector(inputs.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        String text = Objects.requireNonNull(Objects.requireNonNull(document, "document").getText(), "text");
        recordedInputs.add(text);
        return vector(text);
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    List<String> recordedInputs() {
        return List.copyOf(recordedInputs);
    }

    private float[] vector(String text) {
        byte[] seedBytes = ByteBuffer.allocate(Long.BYTES).putLong(seed).array();
        String normalized = Normalizer.normalize(Objects.requireNonNull(text, "text"), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        MessageDigest digest = sha256();
        float[] vector = new float[dimensions];
        int featureCount = addCharacterNgrams(normalized, vector, digest, seedBytes)
                + addWordNgrams(normalized, vector, digest, seedBytes);
        if (featureCount == 0) {
            addFeature("EMPTY", vector, digest, seedBytes);
        }

        double squaredNorm = 0.0;
        for (float value : vector) {
            squaredNorm += value * value;
        }
        if (squaredNorm == 0.0) {
            vector[0] = 1.0f;
            return vector;
        }
        double norm = Math.sqrt(squaredNorm);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= (float) norm;
        }
        return vector;
    }

    private int addCharacterNgrams(String text, float[] vector, MessageDigest digest, byte[] seedBytes) {
        int[] codePoints = text.codePoints().filter(codePoint -> !Character.isWhitespace(codePoint)).toArray();
        int count = 0;
        for (int size = 1; size <= Math.min(3, codePoints.length); size++) {
            for (int start = 0; start + size <= codePoints.length; start++) {
                addFeature("C" + size + ':' + new String(codePoints, start, size), vector, digest, seedBytes);
                count++;
            }
        }
        return count;
    }

    private int addWordNgrams(String text, float[] vector, MessageDigest digest, byte[] seedBytes) {
        List<String> words = words(text);
        for (String word : words) {
            addFeature("W1:" + word, vector, digest, seedBytes);
        }
        for (int i = 0; i + 1 < words.size(); i++) {
            addFeature("W2:" + words.get(i) + '\0' + words.get(i + 1), vector, digest, seedBytes);
        }
        return words.size() + Math.max(0, words.size() - 1);
    }

    private static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            if (Character.isLetterOrDigit(codePoint)) {
                current.appendCodePoint(codePoint);
            } else if (!current.isEmpty()) {
                words.add(current.toString());
                current.setLength(0);
            }
            offset += Character.charCount(codePoint);
        }
        if (!current.isEmpty()) {
            words.add(current.toString());
        }
        return words;
    }

    private void addFeature(String feature, float[] vector, MessageDigest digest, byte[] seedBytes) {
        digest.update(versionBytes);
        digest.update((byte) 0);
        digest.update(seedBytes);
        digest.update((byte) 0);
        byte[] hash = digest.digest(feature.getBytes(StandardCharsets.UTF_8));
        int index = (int) (Integer.toUnsignedLong(ByteBuffer.wrap(hash).getInt()) % dimensions);
        vector[index] += (hash[Integer.BYTES] & 1) == 0 ? 1.0f : -1.0f;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
